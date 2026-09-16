package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.denthe.plaguecore.core.Marks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Подкоманды `/plague health` — правка пометок Мастера игры.
 * Спек «Пометки Мастера игры», раздел 9.
 *
 * Правки идут командами, а не своим пакетом «клиент → сервер»: право
 * проверяет уже само дерево /plague (оператор, уровень 2), и второй
 * путь пришлось бы защищать отдельно. Экран правки просто печатает
 * эти же команды — так у логики одно место, а не два.
 *
 * Отдельной команды «изменить» нет: карандаш в экране шлёт remove,
 * а следом add или text. Четвёртый близнец add/text ради случая,
 * который собирается из двух существующих, не нужен.
 */
public final class HealthMarksCommands {
    private HealthMarksCommands() {}

    private static final String[] РЕЖИМЫ = { "replace", "append" };

    public static void подключить(LiteralArgumentBuilder<CommandSourceStack> корень) {
        корень.then(Commands.literal("health")
            .then(Commands.literal("edit")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::редактор)))
            .then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("place", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(имена(Marks.Место.values()), b))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(РЕЖИМЫ, b))
                            .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(имена(Marks.Заготовка.values()), b))
                                .executes(c -> добавить(c, true)))))))
            .then(Commands.literal("text")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("place", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(имена(Marks.Место.values()), b))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(РЕЖИМЫ, b))
                            .then(Commands.argument("line", StringArgumentType.greedyString())
                                .executes(c -> добавить(c, false)))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(HealthMarksCommands::удалить))))
            .then(Commands.literal("clear")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::очистить)))
            .then(Commands.literal("list")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::список))));
    }

    /** Открыть редактор у того, кто позвал. Игрок об этом не узнаёт. */
    private static int редактор(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        if (!(c.getSource().getEntity() instanceof ServerPlayer гм)) {
            c.getSource().sendFailure(Component.literal("Только от лица игрока"));
            return 0;
        }
        PlagueNetwork.отправитьПометки(гм, цель, (byte) 1);
        return 1;
    }

    private static int добавить(CommandContext<CommandSourceStack> c, boolean заготовкой)
            throws CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");

        Marks.Место место = Marks.место(StringArgumentType.getString(c, "place"));
        if (место == null) {
            c.getSource().sendFailure(Component.literal(
                "Нет такого места. Есть: " + перечислить(Marks.Место.values())));
            return 0;
        }

        String режим = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
        if (!режим.equals("replace") && !режим.equals("append")) {
            c.getSource().sendFailure(Component.literal("Режим — replace или append"));
            return 0;
        }

        String заготовка = "", текст = "";
        if (заготовкой) {
            Marks.Заготовка з = Marks.заготовка(StringArgumentType.getString(c, "preset"));
            if (з == null) {
                c.getSource().sendFailure(Component.literal(
                    "Нет такой заготовки. Есть: " + перечислить(Marks.Заготовка.values())));
                return 0;
            }
            заготовка = з.идентификатор();
        } else {
            текст = StringArgumentType.getString(c, "line");
        }

        int id = PlayerHealthMarks.добавить(цель, место, режим.equals("replace"), заготовка, текст);
        if (id < 0) {
            c.getSource().sendFailure(Component.literal(
                "Не вышло: строка пуста или пометок уже " + Marks.ПРЕДЕЛ));
            return 0;
        }

        разослать(цель, c.getSource());
        String имяЦели = цель.getGameProfile().getName();
        c.getSource().sendSuccess(() -> Component.literal(
            "Пометка " + id + " поставлена: " + имяЦели), false);
        return 1;
    }

    private static int удалить(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!PlayerHealthMarks.удалить(цель, id)) {
            c.getSource().sendFailure(Component.literal("Нет пометки " + id));
            return 0;
        }
        разослать(цель, c.getSource());
        c.getSource().sendSuccess(() -> Component.literal("Пометка " + id + " снята"), false);
        return 1;
    }

    private static int очистить(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        PlayerHealthMarks.очистить(цель);
        разослать(цель, c.getSource());
        c.getSource().sendSuccess(() -> Component.literal(
            "Всё снято, экран снова считается по показателям"), false);
        return 1;
    }

    /** Список в чат: отладка без экрана и способ для второй сессии свериться. */
    private static int список(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        List<Marks.Пометка> пометки = PlayerHealthMarks.список(цель);
        if (пометки.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Пометок нет"), false);
            return 1;
        }
        for (Marks.Пометка п : пометки) {
            String строка = п.id() + ": " + п.место().name().toLowerCase(Locale.ROOT)
                + (п.заменяет() ? " [замена] " : " [добавка] ")
                + (п.своя() ? "«" + п.текст() + "»" : п.заготовка());
            c.getSource().sendSuccess(() -> Component.literal(строка), false);
        }
        return 1;
    }

    /**
     * Разослать новый список: игроку — чтобы его экран обновился сразу,
     * ГМ — чтобы обновился его редактор, если он открыт.
     */
    private static void разослать(ServerPlayer цель, CommandSourceStack источник) {
        PlagueNetwork.отправитьПометки(цель, цель, (byte) 0);
        if (источник.getEntity() instanceof ServerPlayer гм && гм != цель) {
            PlagueNetwork.отправитьПометки(гм, цель, (byte) 1);
        }
    }

    private static List<String> имена(Enum<?>[] значения) {
        List<String> список = new ArrayList<>(значения.length);
        for (Enum<?> з : значения) список.add(з.name().toLowerCase(Locale.ROOT));
        return список;
    }

    private static String перечислить(Enum<?>[] значения) {
        return String.join(", ", имена(значения));
    }
}
