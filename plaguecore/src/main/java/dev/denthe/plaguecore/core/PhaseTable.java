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

    /** Последняя ночь фазы. Нужно конфигу, чтобы показать значение по умолчанию. */
    public static int endNightOf(int phase) {
        return PHASE_END_NIGHT[зажать(phase)];
    }

    /**
     * Заменить одну фазу значениями из конфига. Ядро, раздел 6.2 —
     * ровно те числа, которыми правится кривая распространения.
     *
     * Ночи фаз обязаны идти по возрастанию, иначе фаза схлопнется
     * и следующая никогда не наступит. Ряд выправляется молча; метод
     * сообщает, пришлось ли править. Вызывать по порядку, от нулевой.
     *
     * У последней фазы конца нет: она бессрочная, и endNight у неё
     * не спрашивают.
     *
     * @return true, если значения из файла пришлось поправить
     */
    public static boolean задатьФазу(int phase, int endNight, PhaseParams params) {
        int p = зажать(phase);
        PARAMS[p] = params;
        if (p == PHASE_COUNT - 1) return false;          // бессрочная
        int минимум = (p == 0) ? 1 : PHASE_END_NIGHT[p - 1] + 1;
        int годный = Math.max(минимум, endNight);
        PHASE_END_NIGHT[p] = годный;
        return годный != endNight;
    }

    private static int зажать(int phase) {
        return phase < 0 ? 0 : Math.min(phase, PHASE_COUNT - 1);
    }

    public static int phaseForNight(int night) {
        if (night <= 0) return 0;
        for (int p = 0; p < PHASE_COUNT; p++) {
            if (night <= PHASE_END_NIGHT[p]) return p;
        }
        return PHASE_COUNT - 1;
    }

    public static PhaseParams paramsFor(int phase) {
        return PARAMS[зажать(phase)];
    }
}
