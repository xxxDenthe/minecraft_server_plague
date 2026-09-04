package dev.denthe.shade;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Все числа эффекта — здесь и в config/lmpc_shade-client.toml. За день до
 * сессии тон мира крутится текстовым файлом, без пересборки мода
 * (соглашение репозитория: «игровые числа наружу»).
 *
 * Дефолты взяты из палитры чумы: серый глубокий #26252A — тон,
 * серый тёмный #3A383E — туман.
 */
public final class ShadeConfig {
    private ShadeConfig() {}

    static final float[] DEFAULT_TINT = { 0.173f, 0.169f, 0.157f }; // #2C2B28 нейтрально-тёплый
    static final float[] DEFAULT_FOG  = { 0.290f, 0.282f, 0.259f }; // #4A4842 бледный тёплый серый

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue SATURATION;
    public static final ModConfigSpec.DoubleValue BRIGHTNESS;
    public static final ModConfigSpec.ConfigValue<String> TINT_COLOR;
    public static final ModConfigSpec.DoubleValue TINT_STRENGTH;
    public static final ModConfigSpec.DoubleValue VIGNETTE;

    public static final ModConfigSpec.DoubleValue NIGHT_BOOST;
    public static final ModConfigSpec.DoubleValue NIGHT_DARKNESS;
    public static final ModConfigSpec.DoubleValue SURFACE_NIGHT_FLOOR;
    public static final ModConfigSpec.DoubleValue GRAIN;
    public static final ModConfigSpec.IntValue POSTERIZE;

    public static final ModConfigSpec.BooleanValue LOW_HEALTH_EFFECT;
    public static final ModConfigSpec.DoubleValue LOW_HEALTH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue LOW_HEALTH_INTENSITY;

    public static final ModConfigSpec.ConfigValue<String> FOG_COLOR;
    public static final ModConfigSpec.DoubleValue FOG_COLOR_STRENGTH;
    public static final ModConfigSpec.DoubleValue CAVE_FOG_STRENGTH;
    public static final ModConfigSpec.DoubleValue CAVE_FOG_DISTANCE;
    public static final ModConfigSpec.DoubleValue CAVE_FOG_TOP_Y;

    public static final ModConfigSpec.IntValue SPORE_RATE;
    public static final ModConfigSpec.IntValue GROUND_SPORE_RATE;
    public static final ModConfigSpec.DoubleValue GROUND_SPORE_CHANCE;

    public static final ModConfigSpec.BooleanValue SKY_OVERCAST;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("grade");
        ENABLED = b.comment("Главный выключатель всего эффекта.")
            .define("enabled", true);
        SATURATION = b.comment("Насыщенность кадра: 0 — полностью серо, 1 — как в ваниле. В темноте всё равно серее.")
            .defineInRange("saturation", 0.45, 0.0, 1.0);
        BRIGHTNESS = b.comment("Общая яркость кадра: 1 — без изменений, меньше — темнее.")
            .defineInRange("brightness", 0.88, 0.2, 1.0);
        TINT_COLOR = b.comment("Цвет тона, hex RRGGBB. По умолчанию 2C2B28 — нейтрально-тёплый серый, без лиловости.")
            .define("tintColor", "2C2B28");
        TINT_STRENGTH = b.comment("Сколько тона подмешать: 0 — нет, 1 — заметный сдвиг цветового баланса.")
            .defineInRange("tintStrength", 0.15, 0.0, 1.0);
        VIGNETTE = b.comment("Затемнение краёв экрана: 0 — нет, 1 — сильное.")
            .defineInRange("vignette", 0.22, 0.0, 1.0);
        b.pop();

        b.push("light");
        NIGHT_BOOST = b.comment(
                "«Свет — это жизнь». Общий множитель ночного затемнения (и lightmap поверхности, и кадра).",
                "0 — выключено, 1 — заметно, 1.6 — по умолчанию, 2+ — резче.")
            .defineInRange("nightBoost", 1.6, 0.0, 4.0);
        SURFACE_NIGHT_FLOOR = b.comment(
                "Главная крутилка тёмной ночи. До какой доли яркости проваливается",
                "поверхность ночью БЕЗ источника света (правится сам lightmap — влияет на",
                "видимость мобов и блоков): 0.06 — почти чёрное, 0.15 — силуэты различимы, 0.3 — сумрак.")
            .defineInRange("surfaceNightFloor", 0.10, 0.02, 0.6);
        NIGHT_DARKNESS = b.comment(
                "Дополнительный ночной провал в кадре поверх lightmap (косметика, контраст):",
                "1 — нет, 0.5 — умеренно, 0.1 — сильно. Основную тьму даёт surfaceNightFloor.")
            .defineInRange("nightDarkness", 0.5, 0.0, 1.0);
        b.pop();

        b.push("filmic");
        GRAIN = b.comment("Плёночное зерно: 0 — нет, 0.1 — заметное. Пробуем на 0.035.")
            .defineInRange("grain", 0.035, 0.0, 0.2);
        POSTERIZE = b.comment("Постеризация: число уровней на канал. 0 — выключено, 32 — лёгкая, 16 — грубая.")
            .defineInRange("posterizeLevels", 32, 0, 256);
        b.pop();

        b.push("health");
        LOW_HEALTH_EFFECT = b.comment("Реакция на низкое HP: серость, туннельное зрение, пульс-сердцебиение, к смерти — красный по краям.")
            .define("lowHealthEffect", true);
        LOW_HEALTH_THRESHOLD = b.comment("Доля здоровья, с которой эффект начинается (0.5 = половина).")
            .defineInRange("lowHealthThreshold", 0.5, 0.05, 1.0);
        LOW_HEALTH_INTENSITY = b.comment("Общая сила эффекта низкого HP: 1 — как задумано, меньше — мягче, больше — паника.")
            .defineInRange("lowHealthIntensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("fog");
        FOG_COLOR = b.comment("Цвет тумана и пасмурного неба, hex RRGGBB. По умолчанию 4A4842 — бледный тёплый серый.")
            .define("fogColor", "4A4842");
        FOG_COLOR_STRENGTH = b.comment("Насколько увести туман в этот цвет: 0 — ванильный, 1 — целиком серый.")
            .defineInRange("fogColorStrength", 0.7, 0.0, 1.0);
        CAVE_FOG_STRENGTH = b.comment("Плотность подземного тумана: 0 — выкл, 1 — стянуть вплотную к caveFogDistance.")
            .defineInRange("caveFogStrength", 0.8, 0.0, 1.0);
        CAVE_FOG_DISTANCE = b.comment("К какой дальности в блоках стягивать туман глубоко под землёй при caveFogStrength = 1.")
            .defineInRange("caveFogDistance", 24.0, 6.0, 128.0);
        CAVE_FOG_TOP_Y = b.comment(
                "Потолок подземного тумана по высоте. На этой Y тумана ещё нет, на 32 блока ниже —",
                "полная сила. Так под кроной деревьев на поверхности туман не включается.")
            .defineInRange("caveFogTopY", 60.0, -64.0, 320.0);
        b.pop();

        b.push("spores");
        SPORE_RATE = b.comment("Споровая взвесь: частиц пепла в воздухе за тик вокруг игрока. 0 — выключено, 2 — редко.")
            .defineInRange("sporeRate", 2, 0, 20);
        GROUND_SPORE_RATE = b.comment(
                "Грибные споры у земли — второй, отдельный от пепла эффект: частицы за одну",
                "вспышку (см. groundSporeChance), в узком поясе у ног игрока. 0 — выключено.")
            .defineInRange("groundSporeRate", 1, 0, 10);
        GROUND_SPORE_CHANCE = b.comment(
                "Как часто вообще бывает вспышка спор у земли: доля тиков. 0 — никогда,",
                "1 — каждый тик (часто). По умолчанию редко — не должно мельтешить.")
            .defineInRange("groundSporeChance", 0.05, 0.0, 1.0);
        b.pop();

        b.push("sky");
        SKY_OVERCAST = b.comment(
                "Пасмурное небо: убирает синеву, солнце, луну и звёзды — сверху ровный",
                "серый купол цвета тумана (fogColor), облака остаются. false — ванильное небо.",
                "Меняется только при перезаходе в мир.")
            .define("overcast", true);
        CLOUD_HEIGHT = b.comment(
                "Высота облаков в блоках (ваниль — 192). Ниже — облака нависают. Меняется при перезаходе в мир.")
            .defineInRange("cloudHeight", 192, 40, 320);
        b.pop();

        SPEC = b.build();
    }

    public static float[] tintRgb() {
        return ShadeColor.hexToRgb(TINT_COLOR.get(), DEFAULT_TINT);
    }

    public static float[] fogRgb() {
        return ShadeColor.hexToRgb(FOG_COLOR.get(), DEFAULT_FOG);
    }
}
