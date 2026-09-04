package dev.denthe.shade;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Плоский фасад над {@link ShadeConfig} для внешнего редактора. Панель
 * мастера в моде {@code lmpc_gmtools} дёргает эти методы рефлексией —
 * зависимости между джарами нет. Поэтому всё — строки и примитивы.
 *
 * Значения меняются в памяти сразу (шейдер/туман читают конфиг каждый
 * кадр), {@link #save()} пишет их в {@code config/lmpc_shade-client.toml}.
 * Поля с {@code live == false} (небо) применяются только при перезапуске
 * игры — они читаются один раз при регистрации эффектов измерения.
 */
public final class ShadeApi {
    private ShadeApi() {}

    public static final String DOUBLE = "DOUBLE";
    public static final String INT = "INT";
    public static final String BOOL = "BOOL";
    public static final String HEX = "HEX";

    private record Entry(String group, String label, String kind,
                         double min, double max, boolean live,
                         ModConfigSpec.ConfigValue<?> cv) {}

    private static final Map<String, Entry> M = new LinkedHashMap<>();

    private static void e(String id, String group, String label, String kind,
                          double min, double max, boolean live, ModConfigSpec.ConfigValue<?> cv) {
        M.put(id, new Entry(group, label, kind, min, max, live, cv));
    }

    static {
        e("enabled",           "Кадр", "Весь эффект",        BOOL,   0,   0,   true,  ShadeConfig.ENABLED);
        e("saturation",        "Кадр", "Насыщенность",       DOUBLE, 0,   1,   true,  ShadeConfig.SATURATION);
        e("brightness",        "Кадр", "Яркость",            DOUBLE, 0.2, 1,   true,  ShadeConfig.BRIGHTNESS);
        e("tintColor",         "Кадр", "Цвет тона",          HEX,    0,   0,   true,  ShadeConfig.TINT_COLOR);
        e("tintStrength",      "Кадр", "Сила тона",          DOUBLE, 0,   1,   true,  ShadeConfig.TINT_STRENGTH);
        e("vignette",          "Кадр", "Виньетка",           DOUBLE, 0,   1,   true,  ShadeConfig.VIGNETTE);
        e("grain",             "Кадр", "Зерно",              DOUBLE, 0,   0.2, true,  ShadeConfig.GRAIN);
        e("posterizeLevels",   "Кадр", "Постеризация",       INT,    0,   64,  true,  ShadeConfig.POSTERIZE);

        e("nightBoost",        "Ночь", "Сила ночи",          DOUBLE, 0,   4,   true,  ShadeConfig.NIGHT_BOOST);
        e("surfaceNightFloor", "Ночь", "Дно темноты",        DOUBLE, 0.02, 0.6, true, ShadeConfig.SURFACE_NIGHT_FLOOR);
        e("nightDarkness",     "Ночь", "Провал в кадре",     DOUBLE, 0,   1,   true,  ShadeConfig.NIGHT_DARKNESS);
        e("lowHealthEffect",   "Ночь", "Эффект низкого HP",  BOOL,   0,   0,   true,  ShadeConfig.LOW_HEALTH_EFFECT);
        e("lowHealthThreshold","Ночь", "Порог HP",           DOUBLE, 0.05, 1,  true,  ShadeConfig.LOW_HEALTH_THRESHOLD);
        e("lowHealthIntensity","Ночь", "Сила HP-эффекта",    DOUBLE, 0,   2,   true,  ShadeConfig.LOW_HEALTH_INTENSITY);

        e("fogColor",          "Туман", "Цвет тумана",       HEX,    0,   0,   true,  ShadeConfig.FOG_COLOR);
        e("fogColorStrength",  "Туман", "Сила цвета",        DOUBLE, 0,   1,   true,  ShadeConfig.FOG_COLOR_STRENGTH);
        e("caveFogStrength",   "Туман", "Плотность в пещере", DOUBLE, 0,  1,   true,  ShadeConfig.CAVE_FOG_STRENGTH);
        e("caveFogDistance",   "Туман", "Дальность (блоки)", DOUBLE, 6,   128, true,  ShadeConfig.CAVE_FOG_DISTANCE);
        e("caveFogTopY",       "Туман", "Потолок тумана Y",  DOUBLE, -64, 320, true,  ShadeConfig.CAVE_FOG_TOP_Y);

        e("overcast",          "Небо", "Пасмурное небо",     BOOL,   0,   0,   false, ShadeConfig.SKY_OVERCAST);
        e("cloudHeight",       "Небо", "Высота облаков",     INT,    40,  320, false, ShadeConfig.CLOUD_HEIGHT);
        e("sporeRate",         "Небо", "Споровая взвесь",    INT,    0,   20,  true,  ShadeConfig.SPORE_RATE);
    }

    public static String[] ids()            { return M.keySet().toArray(new String[0]); }
    public static String group(String id)   { return M.get(id).group(); }
    public static String label(String id)   { return M.get(id).label(); }
    public static String kind(String id)    { return M.get(id).kind(); }
    public static double min(String id)     { return M.get(id).min(); }
    public static double max(String id)     { return M.get(id).max(); }
    public static boolean live(String id)   { return M.get(id).live(); }
    public static Object get(String id)         { return M.get(id).cv().get(); }
    public static Object getDefault(String id)  { return M.get(id).cv().getDefault(); }

    /** Вернуть все параметры к значениям по умолчанию (тем, что подбирались). */
    @SuppressWarnings("unchecked")
    public static void resetAll() {
        for (Entry en : M.values()) {
            ((ModConfigSpec.ConfigValue<Object>) en.cv()).set(en.cv().getDefault());
        }
    }

    /** Принимает как типизированное значение (Number/Boolean), так и строку. */
    @SuppressWarnings("unchecked")
    public static void set(String id, Object value) {
        Entry en = M.get(id);
        if (en == null || value == null) return;
        String s = String.valueOf(value).trim();
        Object v = switch (en.kind()) {
            case INT -> (value instanceof Number n) ? n.intValue() : (int) Math.round(Double.parseDouble(s));
            case DOUBLE -> (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(s);
            case BOOL -> (value instanceof Boolean b) ? b : Boolean.parseBoolean(s);
            default -> s.replace("#", "");
        };
        try {
            ((ModConfigSpec.ConfigValue<Object>) en.cv()).set(v);
        } catch (RuntimeException ignored) {
            // мусорное значение из команды — молча игнорируем
        }
    }

    public static void save() {
        ShadeConfig.SPEC.save();
    }
}
