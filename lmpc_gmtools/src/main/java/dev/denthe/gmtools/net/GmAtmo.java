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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Общие настройки Atmospherics: мастер крутит ползунок в «Графике»,
 * сервер запоминает значение и рассылает его всем игрокам. Так небо и
 * туман выглядят одинаково у всей группы — как это уже сделано для
 * цветокора в lmpc_shade.
 *
 * Atmospherics — чисто клиентский мод, на сервере его классов нет.
 * Поэтому сервер значения не разбирает: держит пары «путь → строка» и
 * пересылает их как есть, а применяет уже клиент через AtmoAccess.
 *
 * ponytail: карта в памяти, при перезапуске сервера теряется — ровно
 * как у GmMarkers. Хватит на сессию; понадобится переживать рестарт —
 * переносить в SavedData оверворлда, вместе с метками одним заходом.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmAtmo {
    private GmAtmo() {}

    /** Ключ — путь к полю без префикса atmo: («sky.nightDarkening»). */
    private static final Map<String, String> settings = new LinkedHashMap<>();

    private static final int MAX_SETTINGS = 64;

    static int set(CommandSourceStack src, String path, String value) {
        if (settings.size() >= MAX_SETTINGS && !settings.containsKey(path)) {
            src.sendFailure(Component.literal("слишком много параметров Atmospherics"));
            return 0;
        }
        settings.put(path, value);
        broadcast(src.getServer());
        return 1;
    }

    static int reset(CommandSourceStack src) {
        settings.clear();
        broadcast(src.getServer());
        src.sendSuccess(() -> Component.literal("Настройки Atmospherics сброшены у всех"), true);
        return 1;
    }

    /** Всем игрокам, а не только операторам: небо видит вся группа. */
    private static void broadcast(MinecraftServer server) {
        GmNetwork.Atmo pkt = new GmNetwork.Atmo(Map.copyOf(settings));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, pkt);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !settings.isEmpty()) {
            PacketDistributor.sendToPlayer(p, new GmNetwork.Atmo(Map.copyOf(settings)));
        }
    }
}
