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

    /**
     * Доля поражённой поверхности по уровням 0..5. Ядро, раздел 5.
     *
     * Четвёртый уровень — сплошной по решению владельца: полностью
     * заражённый чанк заражён целиком, а не на девяносто пять процентов.
     * Оспины внутри сплошной Гнили читались как недоделка.
     */
    private static final float[] ДОЛИ = { 0.00f, 0.10f, 0.35f, 0.70f, 1.00f, 1.00f };

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

    /** Поражён ли столбец при уже посчитанной доле. */
    public static boolean isAffected(long seed, int blockX, int blockZ, float fraction) {
        if (fraction <= 0f) return false;
        if (fraction >= 1f) return true;
        return columnWeight(seed, blockX, blockZ) < fraction;
    }

    /**
     * Доля для точки мира, сглаженная между центрами четырёх ближайших
     * чанков. Ключ ко всей картине заражения на глаз.
     *
     * Без сглаживания сплошь заражённый чанк упирается в чистого соседа
     * ровной линией по границе чанка, и мир выглядит расчерченным
     * на квадраты. Со сглаживанием в середине сплошной области доля
     * остаётся единицей — заражено всё, — а у фронта плавно съезжает
     * к нулю, и хэш столбцов превращает съезд в рваную кайму шириной
     * примерно в чанк.
     *
     * Свойство монотонности сохраняется: доля растёт вместе с любым
     * из четырёх уровней, поэтому перерисовка только добавляет блоки
     * и никогда не отменяет уже поставленные.
     */
    public static float fractionAt(PlagueGrid grid, int blockX, int blockZ) {
        // Единица измерения — чанк, начало отсчёта — его центр.
        float fx = (blockX - 8) / 16.0f;
        float fz = (blockZ - 8) / 16.0f;
        int cx = (int) Math.floor(fx);
        int cz = (int) Math.floor(fz);
        float tx = fx - cx;
        float tz = fz - cz;

        float f00 = fractionFor(grid.getLevel(cx,     cz));
        float f10 = fractionFor(grid.getLevel(cx + 1, cz));
        float f01 = fractionFor(grid.getLevel(cx,     cz + 1));
        float f11 = fractionFor(grid.getLevel(cx + 1, cz + 1));

        return смешать(смешать(f00, f10, tx), смешать(f01, f11, tx), tz);
    }

    private static float смешать(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Вес блока: то же число в [0, 1), но с учётом высоты. Нужно под
     * землёй, где решение принимается для каждого блока, а не для столбца:
     * стена пещеры зарастает пятнами и по вертикали тоже.
     */
    public static float blockWeight(long seed, int blockX, int blockY, int blockZ) {
        long h = seed;
        h ^= blockX * 0x9E3779B97F4A7C15L;
        h ^= blockY * 0xD1B54A32D192ED03L;
        h ^= blockZ * 0xC2B2AE3D27D4EB4FL;
        h = перемешать(h);
        return (h >>> 40) / (float) (1 << 24);
    }

    private static long перемешать(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
