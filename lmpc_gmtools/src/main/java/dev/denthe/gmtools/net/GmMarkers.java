package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Общие метки на карте: их видят все операторы. Ставятся в точке
 * вызывающего командой `/gmtools mark <имя>`, убираются `/gmtools unmark <имя>`.
 *
 * ponytail: список в памяти, при перезапуске сервера теряется. Хватит
 * на сессию; понадобится дольше — перенести в SavedData оверворлда.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmMarkers {
    private GmMarkers() {}

    private static final List<GmNetwork.Mark> marks = new ArrayList<>();

    static int add(CommandSourceStack src, double x, double z, int icon, String name) {
        marks.removeIf(m -> m.name().equalsIgnoreCase(name));
        marks.add(new GmNetwork.Mark(name, (float) x, (float) z, (byte) icon));
        broadcast(src.getServer());
        src.sendSuccess(() -> Component.literal("Метка «" + name + "» поставлена"), true);
        return 1;
    }

    static int remove(CommandSourceStack src, String name) {
        boolean removed = marks.removeIf(m -> m.name().equalsIgnoreCase(name));
        if (removed) broadcast(src.getServer());
        src.sendSuccess(() -> Component.literal(removed ? "Метка убрана" : "Нет такой метки"), false);
        return removed ? 1 : 0;
    }

    private static void broadcast(MinecraftServer server) {
        GmNetwork.Marks pkt = new GmNetwork.Marks(List.copyOf(marks));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) PacketDistributor.sendToPlayer(p, pkt);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.hasPermissions(2) && !marks.isEmpty()) {
            PacketDistributor.sendToPlayer(p, new GmNetwork.Marks(List.copyOf(marks)));
        }
    }
}
