package dev.denthe.classes;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * Команды. Спек, раздел 2.
 *
 * {@code /lmpcclasses choose <класс>} — настоящий путь смены: себе,
 * с проверкой кулдауна и срезом мастерства (раздел 2.1). Её шлёт экран
 * алтаря призвания. {@code /lmpcclasses class <кто> [класс]} и
 * {@code /lmpcclasses mastery <кто> <число>} — админский обход правил
 * для проверки на живом сервере, тем же приёмом, что
 * `/plague setlevel`/`/plague player` в plaguecore.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassCommands {
    private ClassCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        var корень = Commands.literal("lmpcclasses");

        var выбор = Commands.literal("choose");
        for (PlayerClassData.Класс к : PlayerClassData.Класс.values()) {
            выбор.then(Commands.literal(имя(к)).executes(c -> выбрать(c, к)));
        }
        корень.then(выбор);

        var ктоДляКласса = Commands.argument("who", EntityArgument.player())
            .executes(ClassCommands::показать);
        for (PlayerClassData.Класс к : PlayerClassData.Класс.values()) {
            ктоДляКласса.then(Commands.literal(имя(к)).executes(c -> выставить(c, к)));
        }
        корень.then(Commands.literal("class").requires(s -> s.hasPermission(2)).then(ктоДляКласса));

        корень.then(Commands.literal("mastery").requires(s -> s.hasPermission(2))
            .then(Commands.argument("who", EntityArgument.player())
                .then(Commands.argument("value", IntegerArgumentType.integer(0, ClassMastery.МАКСИМУМ))
                    .executes(ClassCommands::выставитьМастерство))));

        var настройка = Commands.literal("tune").requires(s -> s.hasPermission(2))
            .executes(ClassCommands::перечислитьЧисла);
        for (String ключ : ClassesConfig.настраиваемые()) {
            настройка.then(Commands.literal(ключ)
                .executes(c -> показатьЧисло(c, ключ))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes(c -> задатьЧисло(c, ключ))));
        }
        корень.then(настройка);

        event.getDispatcher().register(корень);
    }

    private static String имя(PlayerClassData.Класс класс) {
        return класс.name().toLowerCase(Locale.ROOT);
    }

    /** Настоящая смена: себе, с кулдауном и срезом мастерства. */
    private static int выбрать(CommandContext<CommandSourceStack> c, PlayerClassData.Класс класс)
            throws CommandSyntaxException {
        ServerPlayer игрок = c.getSource().getPlayerOrException();
        PlayerClassData д = PlayerClassData.данные(игрок);

        // Выбрать тот же класс, что уже есть, — не «смена». Раньше это
        // молча жгло кулдаун и срезало 70% мастерства: достаточно было
        // ткнуть в свою же строку в алтаре.
        if (д.класс == класс) {
            c.getSource().sendFailure(Component.translatable(
                "msg.lmpc_classes.switch.already", ClassLore.заголовок(класс)));
            return 0;
        }

        long кулдаунТики = ClassesConfig.кулдаунСменыТики();
        long сейчас = игрок.level().getGameTime();
        long осталось = ClassSwitch.осталосьТиков(д.последняяСменаТик, сейчас, кулдаунТики);
        if (осталось > 0) {
            c.getSource().sendFailure(Component.translatable(
                "msg.lmpc_classes.switch.cooldown", ClassSwitch.минутОсталось(осталось)));
            return 0;
        }

        д.сменитьКласс(класс, сейчас, ClassesConfig.доляМастерстваПриСмене());
        PlayerClassData.синхронизировать(игрок);
        выдатьГримуарЕслиНадо(игрок);
        c.getSource().sendSuccess(
            () -> Component.translatable("msg.lmpc_classes.switch.done", ClassLore.заголовок(класс)), false);
        return 1;
    }

    /**
     * Гримуар — не крафтимый предмет, а личный дневник призвания:
     * выдаётся один раз при первом реальном выборе класса. Проверка
     * по инвентарю, а не по отдельному флагу — так и потерянный
     * гримуар восполнится при следующей смене класса, без отдельной
     * команды на этот случай.
     */
    private static void выдатьГримуарЕслиНадо(ServerPlayer игрок) {
        boolean есть = игрок.getInventory().contains(
            стопка -> стопка.is(ClassItems.CLASS_CODEX.get()));
        if (!есть) игрок.getInventory().add(new ItemStack(ClassItems.CLASS_CODEX.get()));
    }

    private static int показать(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerClassData д = PlayerClassData.данные(кто);
        c.getSource().sendSuccess(() -> Component.translatable(
            "msg.lmpc_classes.admin.status",
            кто.getGameProfile().getName(), ClassLore.заголовок(д.класс), д.мастерство, д.тир()), false);
        return 1;
    }

    /**
     * Админский обход кулдауна и среза мастерства — для проверки.
     * Кулдаун нарочно не ставится: раньше админская выдача класса
     * запирала игроку смену на полчаса, хотя смысл команды —
     * наоборот, быстро перебрать классы на живом сервере.
     */
    private static int выставить(CommandContext<CommandSourceStack> c, PlayerClassData.Класс класс)
            throws CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerClassData д = PlayerClassData.данные(кто);
        д.класс = класс;
        д.последняяСменаТик = -1L;
        PlayerClassData.синхронизировать(кто);
        выдатьГримуарЕслиНадо(кто);
        c.getSource().sendSuccess(() -> Component.translatable(
            "msg.lmpc_classes.admin.set", кто.getGameProfile().getName(), ClassLore.заголовок(класс)), true);
        return 1;
    }

    /**
     * {@code /lmpcclasses tune} — правка баланса, не выходя из игры.
     *
     * Заведена по просьбе владельца про скорость грядки и доводит
     * до конца проектное правило «игровые числа наружу»: за день
     * до сессии баланс должен править не пересборка мода и даже
     * не перезапуск сервера, а одна строка в чате. Значение пишется
     * в `config/lmpc_classes-common.toml` сразу, поэтому переживает
     * перезапуск.
     */
    private static int перечислитьЧисла(CommandContext<CommandSourceStack> c) {
        for (String ключ : ClassesConfig.настраиваемые()) {
            c.getSource().sendSuccess(() -> Component.translatable(
                "msg.lmpc_classes.tune.value", ключ, String.valueOf(ClassesConfig.значение(ключ))), false);
        }
        return 1;
    }

    private static int показатьЧисло(CommandContext<CommandSourceStack> c, String ключ) {
        c.getSource().sendSuccess(() -> Component.translatable(
            "msg.lmpc_classes.tune.value", ключ, String.valueOf(ClassesConfig.значение(ключ))), false);
        return 1;
    }

    private static int задатьЧисло(CommandContext<CommandSourceStack> c, String ключ) {
        Object записано = ClassesConfig.задать(ключ, DoubleArgumentType.getDouble(c, "value"));
        if (записано == null) {
            c.getSource().sendFailure(Component.translatable("msg.lmpc_classes.tune.failed", ключ));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(
            "msg.lmpc_classes.tune.set", ключ, String.valueOf(записано)), true);
        return 1;
    }

    /** Выставить мастерство напрямую — единственный способ увидеть тиры 2 и 3 сразу. */
    private static int выставитьМастерство(CommandContext<CommandSourceStack> c)
            throws CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        int значение = IntegerArgumentType.getInteger(c, "value");
        PlayerClassData д = PlayerClassData.данные(кто);
        д.мастерство = ClassMastery.прибавить(0, значение);
        PlayerClassData.синхронизировать(кто);
        c.getSource().sendSuccess(() -> Component.translatable(
            "msg.lmpc_classes.admin.mastery",
            кто.getGameProfile().getName(), д.мастерство, д.тир()), true);
        return 1;
    }
}
