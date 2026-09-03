package dev.denthe.plaguecore.core;

/**
 * Какие столбцы чанка поражены на данном уровне. Дизайн материализации,
 * раздел 4.1; доли поверхности — ядро, раздел 5.
 *
 * Каждому столбцу мира раз и навсегда даётся вес из хэша (сид, x, z).
 * Столбец гниёт на уровне L, если его вес меньше доли, положенной уровню.
 *
 * Из этого даром получаются два свойства:
 *   детерминированность — при перезаходе картинка та же;
 *   вложенность — поражённое на уровне 2 остаётся поражённым на 3,
 *   поэтому рост уровня только добавляет работу, никогда не переделывает.
 *
 * Чистая функция: никакого состояния, никакого Minecraft.
 */
public final class MaterializationMask {
    private MaterializationMask() {}

    /** Доля поражённой поверхности по уровням 0..5. Ядро, раздел 5. */
    private static final float[] ДОЛИ = { 0.00f, 0.10f, 0.35f, 0.70f, 0.95f, 1.00f };

    public static float fractionFor(int level) {
        if (level <= 0) return 0f;
        if (level >= ДОЛИ.length) return 1f;
        return ДОЛИ[level];
    }

    /** Поражён ли столбец мира с координатами блока (x, z) на данном уровне. */
    public static boolean isAffected(long seed, int blockX, int blockZ, int level) {
        if (level <= 0) return false;
        if (level >= ДОЛИ.length - 1) return true;
        return columnWeight(seed, blockX, blockZ) < fractionFor(level);
    }

    /**
     * Вес столбца: число в [0, 1). Перемешивание — SplitMix64 поверх
     * координат, разнесённых нечётными множителями. Дёшево и без узоров.
     */
    public static float columnWeight(long seed, int blockX, int blockZ) {
        long h = seed;
        h ^= blockX * 0x9E3779B97F4A7C15L;
        h ^= blockZ * 0xC2B2AE3D27D4EB4FL;
        h = перемешать(h);
        // старшие 24 бита: их хватает на точность и они самые «перемешанные»
        return (h >>> 40) / (float) (1 << 24);
    }

    private static long перемешать(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
