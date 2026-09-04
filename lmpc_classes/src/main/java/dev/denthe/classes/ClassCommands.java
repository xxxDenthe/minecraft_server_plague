package dev.denthe.classes;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * Админские команды. Спек, раздел 2.
 *
 * Обход кулдауна и крафта путёвки для проверки на живом сервере —
 * тем же приёмом, что `/plague setlevel`/`/plague player` в plaguecore.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassCommands {
    private ClassCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        var корень = Commands.literal("lmpcclasses").requires(s -> s.hasPermission(2));

        var ктоДляКласса = Commands.argument("who", EntityArgument.player())
            .executes(ClassCommands::показать);
        for (PlayerClassData.Класс к : PlayerClassData.Класс.values()) {
            ктоДляКласса.then(Commands.literal(к.name().toLowerCase(Locale.ROOT))
                .executes(c -> выставить(c, к)));
        }
        корень.then(Commands.literal("class").then(ктоДляКласса));

        event.getDispatcher().register(корень);
    }

    private static int показать(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerClassData.Класс класс = PlayerClassData.данные(кто).класс;
        c.getSource().sendSuccess(() -> Component.literal(
            кто.getGameProfile().getName() + ": класс " + класс), false);
        return 1;
    }

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
