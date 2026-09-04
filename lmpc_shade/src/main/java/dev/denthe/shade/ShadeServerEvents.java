package dev.denthe.shade;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Серверная часть цветокора: при входе игрока раздаёт мастер-значения,
 * команда {@code /lmpcshade set|reset} (право 2) правит их и рассылает
 * всем. Панель мастера в {@code lmpc_gmtools} шлёт именно эту команду.
 */
@EventBusSubscriber(modid = LmpcShade.MODID)
public final class ShadeServerEvents {
    private ShadeServerEvents() {}

    /** Раздача мастер-значений присоединившемуся игроку (и всем при /reload). */
    @SubscribeEvent
    static void onSync(OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) {
            sendTo(e.getPlayer());
        } else {
            e.getRelevantPlayers().forEach(ShadeServerEvents::sendTo);
        }
    }

    private static void sendTo(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p,
            new ShadeNet.Sync(ShadeServerState.get(p.serverLevel()).snapshot()));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("lmpcshade").requires(s -> s.hasPermission(2))
            .then(Commands.literal("set")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(c -> set(c.getSource(),
                            StringArgumentType.getString(c, "id"),
                            StringArgumentType.getString(c, "value"))))))
            .then(Commands.literal("reset")
                .executes(c -> reset(c.getSource()))));
    }

    private static int set(CommandSourceStack src, String id, String value) {
        ShadeServerState.get(src.getLevel()).put(id, value);
        broadcast(src.getServer());
        return 1;
    }

    private static int reset(CommandSourceStack src) {
        ShadeServerState.get(src.getLevel()).clear();
        broadcast(src.getServer());
        src.sendSuccess(() -> Component.literal(
            "Цветокор сброшен: у всех вернутся их локальные настройки"), true);
        return 1;
    }

    private static void broadcast(MinecraftServer server) {
        ShadeNet.Sync payload = new ShadeNet.Sync(
            ShadeServerState.get(server.overworld()).snapshot());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
