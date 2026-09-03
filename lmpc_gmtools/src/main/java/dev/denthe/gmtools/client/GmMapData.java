package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;

import java.util.List;

/** Последний снимок позиций игроков с сервера. Читает его карта в панели. */
public final class GmMapData {
    private GmMapData() {}

    private static volatile List<GmNetwork.Pos> players = List.of();
    private static volatile long updatedAt;

    public static void update(List<GmNetwork.Pos> list) {
        players = list;
        updatedAt = System.currentTimeMillis();
    }

    public static List<GmNetwork.Pos> players() {
        return players;
    }

    /** Сколько миллисекунд назад пришёл последний снимок; Long.MAX_VALUE — не приходил. */
    public static long ageMs() {
        return updatedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - updatedAt;
    }
}
