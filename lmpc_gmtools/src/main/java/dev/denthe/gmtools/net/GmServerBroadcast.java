package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Раз в секунду собирает позиции всех онлайн-игроков и шлёт их каждому
 * OP-игроку (право 2). Не-OP позиции не получают — не светим их
 * координаты обычным игрокам.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmServerBroadcast {
    private GmServerBroadcast() {}

    private static int ticks;

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post e) {
        if (++ticks % 20 != 0) return;

        MinecraftServer server = e.getServer();
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) return;

        List<GmNetwork.Pos> all = new ArrayList<>(online.size());
        for (ServerPlayer p : online) {
            all.add(new GmNetwork.Pos(p.getUUID(), p.getGameProfile().getName(),
                (float) p.getX(), (float) p.getZ(), dimByte(p.level().dimension())));
        }

        GmNetwork.Positions payload = new GmNetwork.Positions(all);
        for (ServerPlayer p : online) {
            if (p.hasPermissions(2)) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    private static byte dimByte(ResourceKey<Level> dim) {
        if (dim == Level.OVERWORLD) return 0;
        if (dim == Level.NETHER) return 1;
        if (dim == Level.END) return 2;
        return 3;
    }
}
