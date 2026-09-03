package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;

import java.util.List;

/** Последние снимки с сервера: позиции игроков и общие метки. Читает карта. */
public final class GmMapData {
    private GmMapData() {}

    private static volatile List<GmNetwork.Pos> players = List.of();
    private static volatile List<GmNetwork.Mark> marks = List.of();
    private static volatile long updatedAt;

    public static void update(List<GmNetwork.Pos> list) {
        players = list;
        updatedAt = System.currentTimeMillis();
    }

    public static void updateMarks(List<GmNetwork.Mark> list) {
        marks = list;
    }

    public static List<GmNetwork.Pos> players() {
        return players;
    }

    public static List<GmNetwork.Mark> marks() {
        return marks;
    }

    /** Сколько миллисекунд назад пришёл последний снимок позиций; Long.MAX_VALUE — не приходил. */
    public static long ageMs() {
        return updatedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - updatedAt;
    }
}
