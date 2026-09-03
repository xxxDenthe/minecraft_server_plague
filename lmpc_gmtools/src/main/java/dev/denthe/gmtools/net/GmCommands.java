package dev.denthe.gmtools.net;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.denthe.gmtools.GmTools;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Единая точка регистрации `/gmtools` — все подкоманды в одном дереве,
 * чтобы не полагаться на слияние двух регистраций одного корня.
 * Всё под правом оператора (2).
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmCommands {
    private GmCommands() {}

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("gmtools").requires(s -> s.hasPermission(2))
            .then(Commands.literal("freeze")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> GmFreeze.toggle(c.getSource(), EntityArgument.getPlayer(c, "target")))))
            .then(Commands.literal("frozen")
                .executes(c -> GmFreeze.list(c.getSource())))
            .then(Commands.literal("ghost")
                .executes(c -> GmGhost.toggle(c.getSource())))
            .then(Commands.literal("inv")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> GmInventory.view(c.getSource(), EntityArgument.getPlayer(c, "target")))))
            .then(Commands.literal("mark")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(c -> GmMarkers.add(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("unmark")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(c -> GmMarkers.remove(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("log")
                .executes(c -> GmLog.print(c.getSource()))));
    }
}
