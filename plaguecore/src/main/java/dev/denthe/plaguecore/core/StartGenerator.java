package dev.denthe.plaguecore.core;

import java.util.random.RandomGenerator;

/**
 * Генератор стартового состояния. Спек, раздел 12.3.
 *
 * Сажает очаги и прогоняет ускоренную симуляцию, пока доля заражённых
 * чанков не достигнет цели. Работает только на карте чанков —
 * материализация блоков не запускается, поэтому весь мир генерируется
 * за секунды.
 *
 * Симуляция использует собственные параметры, не связанные с расписанием
 * фаз: генерация — это не игровое время, а подготовка стартового расклада.
 */
public final class StartGenerator {
    private StartGenerator() {}

    public record GenerationResult(int nightsSimulated, float achievedFraction, int epicenterCount) {}

    /** Вероятность заражения при генерации: агрессивнее любой игровой фазы. */
    private static final float GEN_BASE = 0.12f;

    /** Потолок новых чанков за одну ночь генерации. */
    private static final int GEN_BUDGET = 400;

    /** Раз во сколько ночей растёт уровень при генерации. */
    private static final int GEN_GROWTH_EVERY = 2;

    /** Уровень, с которого начинают очаги. */
    private static final int EPICENTER_LEVEL = 3;

    /** Предохранитель от зацикливания, если цель недостижима. */
    private static final int MAX_NIGHTS = 2000;

    /** Отступ очагов от края мира в чанках: у самой границы расти некуда. */
    private static final int EDGE_MARGIN = 3;

    /**
     * Разбросать очаги по сетке.
     *
     * Не просто случайные точки: сетка режется на полосы, и в каждой полосе
     * стоит ровно один очаг. Чистая случайность иногда собирает половину
     * очагов в одном углу, а тогда пропадает весь смысл затеи — суммарная
     * длина фронта, по которому и растёт чума.
     */
    public static long[] scatterEpicenters(PlagueGrid grid, int count, RandomGenerator rng) {
        if (count <= 0) return new long[0];

        int поле = grid.size() - 2 * EDGE_MARGIN;
        int отступ = EDGE_MARGIN;
        if (поле < 1) { // сетка меньше двух отступов — сажаем по всей
            поле = grid.size();
            отступ = 0;
        }

        int полос = Math.min((int) Math.ceil(Math.sqrt(count)), поле);
        int ширина = поле / полос;
        int всего = Math.min(count, полос * полос);

        // перемешиваем номера полос, чтобы при count < полос*полос
        // занятыми оказались не первые слева направо, а разные
        int[] ячейки = new int[полос * полос];
        for (int i = 0; i < ячейки.length; i++) ячейки[i] = i;
        for (int i = ячейки.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = ячейки[i]; ячейки[i] = ячейки[j]; ячейки[j] = t;
        }

        long[] out = new long[всего];
        for (int k = 0; k < всего; k++) {
            int ячейка = ячейки[k];
            int cx = grid.originX() + отступ + (ячейка % полос) * ширина + rng.nextInt(ширина);
            int cz = grid.originZ() + отступ + (ячейка / полос) * ширина + rng.nextInt(ширина);
            out[k] = packChunk(cx, cz);
        }
        return out;
    }

    public static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) { return (int) (packed >> 32); }

    public static int unpackZ(long packed) { return (int) packed; }

    public static GenerationResult generate(PlagueGrid grid, float targetFraction,
                                            long[] epicenters, RandomGenerator rng) {
        if (epicenters.length == 0) {
            return new GenerationResult(0, grid.infectedFraction(), 0);
        }

        for (long p : epicenters) {
            grid.setLevel(unpackX(p), unpackZ(p), EPICENTER_LEVEL);
        }

        float цель = Math.min(Math.max(targetFraction, 0f), 1f);
        int целевыхЯчеек = Math.round(цель * grid.cellCount());

        int ночей = 0;
        int безПрогресса = 0;
        int прошлоеКоличество = grid.countInfected();

        while (прошлоеКоличество < целевыхЯчеек && ночей < MAX_NIGHTS) {
            // Бюджет ночи ограничен остатком до цели: без этого одна щедрая
            // ночь может перепрыгнуть цель на сотни чанков.
            int осталось = Math.max(1, целевыхЯчеек - прошлоеКоличество);
            PhaseParams params = new PhaseParams(
                GEN_BASE, Math.min(GEN_BUDGET, осталось), GEN_GROWTH_EVERY, 1);

            SpreadEngine.runNightWith(grid, ночей + 1, params, 1.0f, 0, rng);
            ночей++;

            int сейчас = grid.countInfected();
            безПрогресса = (сейчас == прошлоеКоличество) ? безПрогресса + 1 : 0;
            прошлоеКоличество = сейчас;

            // если 50 ночей подряд ничего не меняется — дальше некуда расти
            if (безПрогресса >= 50) break;
        }

        return new GenerationResult(ночей, grid.infectedFraction(), epicenters.length);
    }
}
