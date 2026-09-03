package dev.denthe.gmtools.net;

import com.mojang.brigadier.CommandDispatcher;
import dev.denthe.gmtools.GmTools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Заморозка одного игрока. Каждый тик замороженного возвращаем в точку,
 * где его заморозили, и обнуляем импульс — двигаться он не может,
 * осматриваться и говорить — может. Команда:
 *   /gmtools freeze &lt;игрок&gt;   — переключает
 *   /gmtools frozen             — список
 * Только оператору. Панель шлёт эти команды кнопкой в карточке игрока.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmFreeze {
    private GmFreeze() {}

    private static final Map<UUID, Vec3> pinned = new HashMap<>();

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("gmtools").requires(s -> s.hasPermission(2))
            .then(Commands.literal("freeze")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(c -> toggle(c.getSource(), EntityArgument.getPlayer(c, "target")))))
            .then(Commands.literal("frozen")
                .executes(c -> list(c.getSource()))));
    }

    private static int toggle(CommandSourceStack src, ServerPlayer target) {
        String name = target.getGameProfile().getName();
        if (pinned.remove(target.getUUID()) != null) {
            src.sendSuccess(() -> Component.literal(name + " разморожен"), true);
        } else {
            pinned.put(target.getUUID(), target.position());
            src.sendSuccess(() -> Component.literal(name + " заморожен"), true);
        }
        return 1;
    }

    private static int list(CommandSourceStack src) {
        String names = src.getServer().getPlayerList().getPlayers().stream()
            .filter(p -> pinned.containsKey(p.getUUID()))
            .map(p -> p.getGameProfile().getName())
            .reduce((a, b) -> a + ", " + b).orElse(null);
        src.sendSuccess(() -> Component.literal(names == null ? "никто не заморожен" : "заморожены: " + names), false);
        return names == null ? 0 : 1;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Vec3 pin = pinned.get(sp.getUUID());
        if (pin == null) return;
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        if (sp.position().distanceToSqr(pin) > 0.02) {
            sp.connection.teleport(pin.x, pin.y, pin.z, sp.getYRot(), sp.getXRot());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        pinned.remove(e.getEntity().getUUID());
    }
}
