package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.DreadMath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Пульт страха для Мастера игры. Спек хоррора, раздел 4.
 *
 * Мастер не получает второй, параллельной системы: его кнопки зовут тот
 * же {@link DreadDirector}, поэтому ручное событие тоже заводит долину
 * тишины. Иначе режиссёр и человек за пультом били бы в одну точку
 * дважды подряд и сбивали друг другу ритм.
 *
 * Панель `lmpc_gmtools` печатает именно эти команды — своей логики
 * у неё нет, и право проверяет сервер по дереву `/plague` (уровень 2).
 */
public final class DreadCommands {
    private DreadCommands() {}

    /** Секунд в минуте тиками — «тишина на N минут» считается в них. */
    private static final int ТИКОВ_В_МИНУТЕ = 1200;

    public static void подключить(LiteralArgumentBuilder<CommandSourceStack> корень) {
        корень.then(Commands.literal("dread")
            .then(Commands.literal("info").executes(DreadCommands::сводка))
            .then(Commands.literal("rustle")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> выдать(c, DreadMath.Уровень.ШОРОХ))))
            .then(Commands.literal("vision")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> выдать(c, DreadMath.Уровень.ВИДЕНИЕ))))
            .then(Commands.literal("watcher")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(DreadCommands::явление)))
            .then(Commands.literal("silence")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 240))
                        .executes(DreadCommands::тишина))))
            .then(Commands.literal("push")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("amount", FloatArgumentType.floatArg(-100f, 100f))
                        .executes(DreadCommands::подтолкнуть))))
            .then(Commands.literal("resetwatchers").executes(DreadCommands::сброс)));
    }

    /**
     * Кто как напуган. Панель читает эту же строку, поэтому формат
     * жёсткий: имя, напряжение, остаток тишины, потрачено за час.
     */
    private static int сводка(CommandContext<CommandSourceStack> ctx) {
        ServerLevel уровень = ctx.getSource().getLevel();
        long сейчас = уровень.getGameTime();
        int явлений = PlagueState.get(уровень).watchers();

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
            "Страх: %s, явлений за сессию %d из %d",
            PlagueConstants.DREAD_ENABLED ? "включён" : "выключен",
            явлений, PlagueConstants.DREAD_WATCHERS_PER_SESSION)), false);

        for (ServerPlayer игрок : уровень.getServer().getPlayerList().getPlayers()) {
            DreadDirector.Счёт с = DreadDirector.состояние(игрок);
            long тишины = Math.max(0L, с.последнее() + с.долина() - сейчас);
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s: напряжение %.0f, тишина %d с, за час шорохов %d, видений %d",
                игрок.getGameProfile().getName(), с.напряжение(), тишины / 20L,
                с.шороховЗаЧас(), с.виденийЗаЧас())), false);
        }
        return 1;
    }

    private static int выдать(CommandContext<CommandSourceStack> ctx,
                              DreadMath.Уровень уровень) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(ctx, "target");
        DreadDirector.выдать(цель, уровень);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Выдано: " + уровень + " → " + цель.getGameProfile().getName()), false);
        return 1;
    }

    private static int явление(CommandContext<CommandSourceStack> ctx) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(ctx, "target");
        boolean встал = Watcher.явить(цель);
        if (!встал) {
            ctx.getSource().sendFailure(Component.literal(
                "Некуда поставить: вокруг нет подходящей точки в 24–40 блоках"));
            return 0;
        }
        DreadDirector.тишина(цель, PlagueConstants.DREAD_VALLEY_WATCHER);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Явление → " + цель.getGameProfile().getName()), false);
        return 1;
    }

    private static int тишина(CommandContext<CommandSourceStack> ctx) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(ctx, "target");
        int минут = IntegerArgumentType.getInteger(ctx, "minutes");
        DreadDirector.тишина(цель, минут * ТИКОВ_В_МИНУТЕ);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Тишина " + минут + " мин → " + цель.getGameProfile().getName()), false);
        return 1;
    }

    private static int подтолкнуть(CommandContext<CommandSourceStack> ctx) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(ctx, "target");
        float сколько = FloatArgumentType.getFloat(ctx, "amount");
        DreadDirector.подтолкнуть(цель, сколько);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
            "Напряжение %+.0f → %s, теперь %.0f", сколько,
            цель.getGameProfile().getName(),
            DreadDirector.состояние(цель).напряжение())), false);
        return 1;
    }

    /** Новая сессия на том же мире: явления начинаются заново. */
    private static int сброс(CommandContext<CommandSourceStack> ctx) {
        PlagueState.get(ctx.getSource().getLevel()).resetWatchers();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Счётчик явлений сброшен"), false);
        return 1;
    }
}
