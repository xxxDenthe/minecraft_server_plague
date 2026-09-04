package dev.denthe.classes;

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
 * алтаря призвания. {@code /lmpcclasses class <кто> <класс>} — админский
 * обход обоих правил для проверки на живом сервере, тем же приёмом,
 * что `/plague setlevel`/`/plague player` в plaguecore.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassCommands {
    private ClassCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        var корень = Commands.literal("lmpcclasses");

        var выбор = Commands.literal("choose");
        for (PlayerClassData.Класс к : PlayerClassData.Класс.values()) {
            выбор.then(Commands.literal(к.name().toLowerCase(Locale.ROOT))
                .executes(c -> выбрать(c, к)));
        }
        корень.then(выбор);

        var ктоДляКласса = Commands.argument("who", EntityArgument.player())
            .requires(s -> s.hasPermission(2))
            .executes(ClassCommands::показать);
        for (PlayerClassData.Класс к : PlayerClassData.Класс.values()) {
            ктоДляКласса.then(Commands.literal(к.name().toLowerCase(Locale.ROOT))
                .executes(c -> выставить(c, к)));
        }
        корень.then(Commands.literal("class").requires(s -> s.hasPermission(2)).then(ктоДляКласса));

        event.getDispatcher().register(корень);
    }

    /** Настоящая смена: себе, с кулдауном и срезом мастерства. */
    private static int выбрать(CommandContext<CommandSourceStack> c, PlayerClassData.Класс класс)
            throws CommandSyntaxException {
        ServerPlayer игрок = c.getSource().getPlayerOrException();
        PlayerClassData д = PlayerClassData.данные(игрок);
        long кулдаунТики = ClassesConfig.кулдаунСменыТики();
        long сейчас = игрок.level().getGameTime();

        if (!ClassSwitch.можноСменить(д.последняяСменаТик, сейчас, кулдаунТики)) {
            long осталосьТиков = кулдаунТики - (сейчас - д.последняяСменаТик);
            c.getSource().sendFailure(Component.literal(
                "Класс можно сменить через " + (осталосьТиков / 1200 + 1) + " мин."));
            return 0;
        }

        д.сменитьКласс(класс, сейчас, ClassesConfig.доляМастерстваПриСмене());
        выдатьГримуарЕслиНадо(игрок);
        c.getSource().sendSuccess(() -> Component.literal("Класс: " + класс), false);
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
        c.getSource().sendSuccess(() -> Component.literal(
            кто.getGameProfile().getName() + ": класс " + д.класс + ", мастерство " + д.мастерство), false);
        return 1;
    }

    /** Админский обход кулдауна и среза мастерства — для проверки. */
    private static int выставить(CommandContext<CommandSourceStack> c, PlayerClassData.Класс класс)
            throws CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerClassData д = PlayerClassData.данные(кто);
        д.класс = класс;
        д.последняяСменаТик = кто.level().getGameTime();
        c.getSource().sendSuccess(() -> Component.literal(
            кто.getGameProfile().getName() + ": класс " + класс), true);
        return 1;
    }
}
