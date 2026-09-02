package dev.denthe.plaguecore.core;

/**
 * Таблица фаз из спека, раздел 6.2. Числа рассчитаны под мир 1000×1000.
 *
 * Кривая: старт 10% (≈397 чанков), к ночи 30 суммарный бюджет 2735,
 * итого около 80% мира.
 */
public final class PhaseTable {
    private PhaseTable() {}

    public static final int PHASE_COUNT = 5;

    /** Последняя ночь каждой фазы. Фаза 4 бессрочная. */
    private static final int[] PHASE_END_NIGHT = { 5, 12, 20, 30, Integer.MAX_VALUE };

    private static final PhaseParams[] PARAMS = {
        //               base   budget  каждые N ночей  на сколько
        new PhaseParams(0.04f,   25,      3,             1),
        new PhaseParams(0.07f,   50,      2,             1),
        new PhaseParams(0.11f,   95,      1,             1),
        new PhaseParams(0.16f,  150,      1,             1),
        new PhaseParams(0.24f,  240,      1,             2)
    };

    public static int phaseForNight(int night) {
        if (night <= 0) return 0;
        for (int p = 0; p < PHASE_COUNT; p++) {
            if (night <= PHASE_END_NIGHT[p]) return p;
        }
        return PHASE_COUNT - 1;
    }

    public static PhaseParams paramsFor(int phase) {
        int p = phase < 0 ? 0 : Math.min(phase, PHASE_COUNT - 1);
        return PARAMS[p];
    }
}
