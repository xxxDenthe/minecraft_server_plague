package dev.denthe.gmtools.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Один фасад над двумя мостами «Графики»: {@link ShadeAccess} (наш
 * цветокор) и {@link AtmoAccess} (небо и туман мода Atmospherics).
 * Панель ходит только сюда и не знает, чей это параметр.
 *
 * Разводка по префиксу: идентификаторы Atmospherics начинаются с
 * {@code atmo:}, остальные — от lmpc_shade. Группы у обоих мостов
 * названы одинаково (Кадр / Ночь / Туман / Небо), поэтому параметры
 * ложатся в уже существующие папки и новых вкладок не появляется.
 */
final class GradeAccess {
    private GradeAccess() {}

    static boolean available() {
        return ShadeAccess.available() || AtmoAccess.available();
    }

    /** Сначала параметры цветокора, следом — Atmospherics в тех же папках. */
    static String[] ids() {
        List<String> all = new ArrayList<>(List.of(ShadeAccess.ids()));
        all.addAll(List.of(AtmoAccess.ids()));
        return all.toArray(new String[0]);
    }

    static String group(String id) { return atmo(id) ? AtmoAccess.group(id) : ShadeAccess.group(id); }
    static String label(String id) { return atmo(id) ? AtmoAccess.label(id) : ShadeAccess.label(id); }
    static String kind(String id)  { return atmo(id) ? AtmoAccess.kind(id)  : ShadeAccess.kind(id); }
    static double min(String id)   { return atmo(id) ? AtmoAccess.min(id)   : ShadeAccess.min(id); }
    static double max(String id)   { return atmo(id) ? AtmoAccess.max(id)   : ShadeAccess.max(id); }
    static boolean live(String id) { return atmo(id) ? AtmoAccess.live(id)  : ShadeAccess.live(id); }
    static Object get(String id)   { return atmo(id) ? AtmoAccess.get(id)   : ShadeAccess.get(id); }

    static void set(String id, Object v) {
        if (atmo(id)) AtmoAccess.set(id, v); else ShadeAccess.set(id, v);
    }

    /** Команда, которой значение уходит на сервер и оттуда всем игрокам. */
    static String pushCommand(String id, String value) {
        return atmo(id)
            ? "gmtools atmo " + id.substring("atmo:".length()) + " " + value
            : "lmpcshade set " + id + " " + value;
    }

    /** «Вернуть подобранные» — сброс обоих мостов. */
    static void resetAll() {
        ShadeAccess.resetAll();
        AtmoAccess.resetAll();
    }

    /** «Сохранить локально» — оба конфига разом, каждый в свой файл. */
    static void save() {
        ShadeAccess.save();
        AtmoAccess.save();
    }

    private static boolean atmo(String id) {
        return AtmoAccess.owns(id);
    }
}
