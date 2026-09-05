package dev.denthe.gmtools.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Мост к моду Atmospherics через рефлексию. Прямой зависимости между
 * джарами нет: нет мода — {@link #available()} вернёт false, и в
 * «Графике» просто не появится его ползунков.
 *
 * У Atmospherics всё нужное открыто: {@code Atmospherics.getConfig()}
 * отдаёт объект {@code FogConfig} с публичными полями,
 * {@code saveConfig()} пишет {@code config/ambientfog/biome_fog.json}.
 * Правки полей видны в кадре сразу — рендер читает конфиг каждый кадр,
 * на этом же держится его собственное меню по клавише B.
 *
 * Наружу выставляем тот же набор методов, что и {@link ShadeAccess}, —
 * панель работает с обоими мостами одинаково через {@link GradeAccess}.
 * Идентификаторы вида {@code atmo:sky.nightDarkening}: после префикса —
 * путь к полю от корня FogConfig, точка разделяет вложенные объекты.
 *
 * ponytail: правим только глобальные параметры, по 65 биомам не лазим —
 * на сессии крутят именно общий тон. Понадобится биом под ногами —
 * это отдельная работа с чтением биома игрока.
 */
final class AtmoAccess {
    private AtmoAccess() {}

    private static final String BOOL = "BOOL";
    private static final String DOUBLE = "DOUBLE";
    private static final String INT = "INT";

    private record Entry(String group, String label, String kind, double min, double max) {}

    /** Порядок вставки = порядок ползунков в папке. */
    private static final Map<String, Entry> M = new LinkedHashMap<>();

    private static void e(String path, String group, String label, String kind, double min, double max) {
        M.put("atmo:" + path, new Entry(group, label, kind, min, max));
    }

    static {
        // Папки те же, что у lmpc_shade, — новых вкладок не заводим.
        e("fogDensity",       "Туман", "Atmo: плотность тумана", DOUBLE, 0,   1);
        e("haze.strength",    "Туман", "Atmo: сила дымки",       DOUBLE, 0,   5);
        e("haze.topY",        "Туман", "Atmo: верх дымки Y",     DOUBLE, -64, 320);
        e("haze.radius",      "Туман", "Atmo: радиус дымки",     DOUBLE, 0,   128);
        e("airHazeIntensity", "Туман", "Atmo: взвесь в воздухе", DOUBLE, 0,   2);

        e("sky.nightDarkening",    "Ночь", "Atmo: небо ночью темнее",   DOUBLE, 0, 1);
        e("clouds.nightDarkening", "Ночь", "Atmo: облака ночью темнее", DOUBLE, 0, 1);
        e("weather.fogNightColorDarkening", "Ночь", "Atmo: туман ночью темнее", DOUBLE, 0, 1);

        // Цвета неба здесь нет и не будет: небо в этом мире всегда ровно
        // серое, его тон задан пресетом config/ambientfog/biome_fog.json.
        // Поэтому наружу не выведены ни masterEnabled (выключение мода
        // возвращает ванильную синеву), ни sky.fogBlendStrength (меняет,
        // насколько купол берёт цвет тумана). Мастер крутит плотность,
        // облака и звёзды — но не тон неба.
        e("clouds.storeModeCloudLayerHeight", "Небо", "Atmo: высота облаков",    DOUBLE, 40, 320);
        e("clouds.storeModeCloudOpacity",     "Небо", "Atmo: плотность облаков", DOUBLE, 0, 1);
        e("clouds.fogColorMixStrength",       "Небо", "Atmo: облака в цвет тумана", DOUBLE, 0, 1);
        e("stars.enabled",                    "Небо", "Atmo: звёзды",            BOOL,   0, 0);
        e("stars.brightness",                 "Небо", "Atmo: яркость звёзд",     DOUBLE, 0, 1);
        e("stars.amount",                     "Небо", "Atmo: число звёзд",       INT,    0, 2000);
        e("comets.enabled",                   "Небо", "Atmo: кометы",            BOOL,   0, 0);
    }

    /**
     * Значения на момент первой правки. Нужны для «Вернуть подобранные»:
     * у Atmospherics нет своего сброса к тому, что лежало в файле, а
     * перечитывать файл в обход мода рискованно — он держит конфиг в
     * памяти. Запоминаем исходное сами, при первой же записи.
     */
    private static final Map<String, Object> initial = new LinkedHashMap<>();

    private static boolean tried;
    private static Method mGetConfig, mSaveConfig;

    private static void load() {
        if (tried) return;
        tried = true;
        try {
            Class<?> mod = Class.forName("com.beash.atmospherics.Atmospherics");
            mGetConfig = mod.getMethod("getConfig");
            mSaveConfig = mod.getMethod("saveConfig");
        } catch (ReflectiveOperationException e) {
            mGetConfig = null;
        }
    }

    static boolean available() {
        load();
        return mGetConfig != null && config() != null;
    }

    static boolean owns(String id) {
        return id.startsWith("atmo:");
    }

    static String[] ids() {
        return available() ? M.keySet().toArray(new String[0]) : new String[0];
    }

    static String group(String id) { return M.get(id).group(); }
    static String label(String id) { return M.get(id).label(); }
    static String kind(String id)  { return M.get(id).kind(); }
    static double min(String id)   { return M.get(id).min(); }
    static double max(String id)   { return M.get(id).max(); }

    /** Все параметры применяются в текущем кадре — звёздочки в панели не нужны. */
    static boolean live(String id) { return true; }

    static Object get(String id) {
        Field f = field(id);
        if (f == null) return null;
        try {
            Object v = f.get(owner(id));
            if (v instanceof Float fl) return fl.doubleValue();  // панель ждёт Number
            return v;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static void set(String id, Object value) {
        Field f = field(id);
        if (f == null) return;
        initial.computeIfAbsent(id, AtmoAccess::get);
        try {
            Object target = owner(id);
            Class<?> t = f.getType();
            if (t == boolean.class) f.setBoolean(target, Boolean.parseBoolean(String.valueOf(value)));
            else if (t == float.class) f.setFloat(target, (float) toDouble(value));
            else if (t == double.class) f.setDouble(target, toDouble(value));
            else if (t == int.class) f.setInt(target, (int) Math.round(toDouble(value)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // молча: мод мог поменять поле между версиями, ронять панель незачем
        }
    }

    /** Вернуть всё, что трогали, к значениям на момент первой правки. */
    static void resetAll() {
        for (Map.Entry<String, Object> en : initial.entrySet()) {
            if (en.getValue() != null) set(en.getKey(), en.getValue());
        }
        initial.clear();
    }

    /** Пишет config/ambientfog/biome_fog.json — своя машина, без сервера. */
    static void save() {
        if (!available()) return;
        try {
            mSaveConfig.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // см. выше
        }
    }

    private static double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
    }

    private static Object config() {
        try {
            return mGetConfig.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Объект, которому принадлежит последнее звено пути (FogConfig или вложенный settings). */
    private static Object owner(String id) {
        Object o = config();
        String[] parts = path(id);
        for (int i = 0; i < parts.length - 1 && o != null; i++) {
            try {
                o = o.getClass().getField(parts[i]).get(o);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return o;
    }

    private static Field field(String id) {
        Object o = owner(id);
        if (o == null) return null;
        String[] parts = path(id);
        try {
            return o.getClass().getField(parts[parts.length - 1]);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String[] path(String id) {
        return id.substring("atmo:".length()).split("\\.");
    }
}
