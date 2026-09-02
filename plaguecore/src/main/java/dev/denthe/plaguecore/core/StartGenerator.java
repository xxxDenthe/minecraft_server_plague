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
