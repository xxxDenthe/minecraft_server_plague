package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.SpreadEngine;
import dev.denthe.plaguecore.core.StartGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Админские команды. Спек, раздел 12.1.
 * В этом плане реализована часть, не требующая материализации блоков.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueCommands {
    private PlagueCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> корень = Commands.literal("plague")
            .requires(s -> s.hasPermission(2));

        корень.then(Commands.literal("gui").executes(PlagueCommands::экран));

        корень.then(Commands.literal("info").executes(PlagueCommands::info));

        корень.then(Commands.literal("night").executes(PlagueCommands::ночь));

        корень.then(Commands.literal("fastforward")
            .then(Commands.argument("nights", IntegerArgumentType.integer(1, 500))
                .executes(PlagueCommands::прогнать)));

        корень.then(Commands.literal("setphase")
            .then(Commands.argument("phase", IntegerArgumentType.integer(0, 4))
                .executes(PlagueCommands::установитьФазу)));

        корень.then(Commands.literal("pause").executes(c -> пауза(c.getSource(), true)));
        корень.then(Commands.literal("resume").executes(c -> пауза(c.getSource(), false)));

        корень.then(Commands.literal("generate")
            .then(Commands.argument("percent", FloatArgumentType.floatArg(0.01f, 1.0f))
                .executes(PlagueCommands::сгенерировать)));

        корень.then(Commands.literal("seed")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, true))));

        корень.then(Commands.literal("remove")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, false))));

        event.getDispatcher().register(корень);
    }

    private static ServerLevel мир(CommandSourceStack src) {
        return src.getServer().getLevel(Level.OVERWORLD);
    }

    /** Открыть админский экран. Работает только для игрока — из консоли нечего показывать. */
    private static int экран(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlagueNetwork.отправитьСнимок(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        PlagueGrid g = st.grid();

        int[] поУровням = new int[6];
        for (int cz = g.originZ(); cz < g.originZ() + g.size(); cz++) {
            for (int cx = g.originX(); cx < g.originX() + g.size(); cx++) {
                поУровням[g.getLevel(cx, cz)]++;
            }
        }

        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.literal("=== Состояние чумы ==="), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Ночь %d, фаза %d%s", st.night(), st.phase(),
                st.isPaused() ? " (ПАУЗА)" : "")), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Заражено: %.1f%% (%d из %d чанков)",
                g.infectedFraction() * 100f, g.countInfected(), g.cellCount())), false);
        for (int lvl = 1; lvl <= 5; lvl++) {
            final int l = lvl, n = поУровням[lvl];
            if (n > 0) s.sendSuccess(() -> Component.literal("  уровень " + l + ": " + n), false);
        }
        s.sendSuccess(() -> Component.literal("Очагов: " + st.epicenters().size()), false);
        s.sendSuccess(() -> Component.literal(
            "Местность размечена: " + (st.isTerrainInitialized() ? "да" : "нет")), false);
        return 1;
    }

    private static int ночь(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        st.advanceNight();
        SpreadEngine.NightResult r = NightHook.runNight(level, st, false);
        st.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Ночь %d: заражено %d, выросло %d, зажило %d",
                st.night(), r.newlyInfected(), r.grown(), r.scarsHealed())), true);
        return 1;
    }

    private static int прогнать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int ночей = IntegerArgumentType.getInteger(ctx, "nights");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        long t0 = System.nanoTime();
        int всего = 0;
        for (int i = 0; i < ночей; i++) {
            st.advanceNight();
            всего += NightHook.runNight(level, st, false).newlyInfected();
        }
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        final int итог = всего;
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Прогнано %d ночей за %d мс. Заражено %d чанков, теперь %.1f%%",
                ночей, мс, итог, st.grid().infectedFraction() * 100f)), true);
        return 1;
    }

    private static int установитьФазу(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int фаза = IntegerArgumentType.getInteger(ctx, "phase");
        PlagueState st = PlagueState.get(мир(ctx.getSource()));
        int[] перваяНочьФазы = { 1, 6, 13, 21, 31 };
        st.setNight(перваяНочьФазы[фаза]);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Фаза " + фаза + ", ночь " + st.night()), true);
        return 1;
    }

    private static int пауза(CommandSourceStack src, boolean значение) {
        PlagueState st = PlagueState.get(src.getServer().getLevel(Level.OVERWORLD));
        st.setPaused(значение);
        src.sendSuccess(() -> Component.literal(
            значение ? "Чума поставлена на паузу" : "Чума продолжается"), true);
        return 1;
    }

    private static int сгенерировать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        float доля = FloatArgumentType.getFloat(ctx, "percent");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        long[] очаги = st.epicentersArray();
        if (очаги.length == 0) {
            ctx.getSource().sendFailure(Component.literal(
                "Сначала посадите хотя бы один очаг: /plague seed <x> <z>"));
            return 0;
        }

        RandomGenerator rng = RandomGeneratorFactory.of("Xoshiro256PlusPlus")
            .create(level.getSeed());

        long t0 = System.nanoTime();
        StartGenerator.GenerationResult r =
            StartGenerator.generate(st.grid(), доля, очаги, rng);
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Сгенерировано за %d мс: %.1f%% мира, %d ночей симуляции, очагов %d",
                мс, r.achievedFraction() * 100f, r.nightsSimulated(), r.epicenterCount())), true);
        return 1;
    }

    private static int очаг(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                            boolean добавить) {
        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        PlagueState st = PlagueState.get(мир(ctx.getSource()));

        if (!st.grid().contains(cx, cz)) {
            ctx.getSource().sendFailure(Component.literal(
                "Чанк " + cx + ", " + cz + " вне сетки мира"));
            return 0;
        }

        long packed = StartGenerator.packChunk(cx, cz);
        if (добавить) {
            st.addEpicenter(packed);
            st.grid().setLevel(cx, cz, 3);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг посажен в чанке " + cx + ", " + cz), true);
        } else {
            st.removeEpicenter(packed);
            st.grid().setLevel(cx, cz, 0);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг убран из чанка " + cx + ", " + cz), true);
        }
        return 1;
    }
}
