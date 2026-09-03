package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Журнал действий мастеров: перехватывает команды операторов и держит
 * последние 100 в памяти. Смотреть — `/gmtools log` (пишет в чат).
 *
 * ponytail: кольцевой буфер в памяти, при перезапуске теряется.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmLog {
    private GmLog() {}

    private static final int MAX = 100;
    private static final Deque<String> lines = new ArrayDeque<>();
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Команды, которые стоит писать в журнал (по первому слову). */
    private static final String[] WATCH = {
        "gmtools", "gamemode", "gm", "tp", "teleport", "give", "effect", "kill", "ban", "ban-ip",
        "pardon", "kick", "weather", "gamerule", "tick", "plague", "title", "tellraw", "playsound",
        "stopsound", "spectate", "clear", "op", "deop", "difficulty", "setblock", "fill", "summon"
    };

    @SubscribeEvent
    static void onCommand(CommandEvent e) {
        CommandSourceStack src = e.getParseResults().getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || !p.hasPermissions(2)) return;

        String input = e.getParseResults().getReader().getString();
        String cmd = input.startsWith("/") ? input.substring(1) : input;
        String first = cmd.split("\\s+", 2)[0].toLowerCase();
        boolean watched = false;
        for (String w : WATCH) if (w.equals(first)) { watched = true; break; }
        if (!watched) return;

        synchronized (lines) {
            lines.addLast(LocalTime.now().format(HMS) + "  " + p.getGameProfile().getName() + ": " + cmd);
            while (lines.size() > MAX) lines.removeFirst();
        }
    }

    static int print(CommandSourceStack src) {
        synchronized (lines) {
            if (lines.isEmpty()) {
                src.sendSuccess(() -> Component.literal("Журнал пуст"), false);
                return 0;
            }
            src.sendSuccess(() -> Component.literal("=== Журнал мастеров (" + lines.size() + ") ==="), false);
            for (String s : lines) src.sendSuccess(() -> Component.literal(s), false);
        }
        return 1;
    }
}
