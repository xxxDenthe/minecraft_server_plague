package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

import java.util.random.RandomGenerator;

/**
 * Ночной тик распространения. Спек, раздел 6.
 *
 * Порядок внутри ночи важен:
 *   1. снимок уровней — чтобы рост этой ночи не каскадировал сам в себя
 *   2. рост на месте
 *   3. экспансия по снимку, в перемешанном порядке источников
 *   4. таяние шрамов
 *
 * Перемешивание источников нужно, чтобы бюджет не доставался всегда
 * чанкам с начала массива: без него заражение систематически ползло бы
 * на северо-запад.
 */
public final class SpreadEngine {
    private SpreadEngine() {}

    public record NightResult(int newlyInfected, int grown, int scarsHealed, int phase) {}

    private static final int[] СМЕЩЕНИЯ_X = { -1, 0, 1, -1, 1, -1, 0, 1 };
    private static final int[] СМЕЩЕНИЯ_Z = { -1, -1, -1, 0, 0, 1, 1, 1 };

    /** Ночь по расписанию фаз. */
    public static NightResult runNight(PlagueGrid grid, int night, boolean slept, RandomGenerator rng) {
        int phase = PhaseTable.phaseForNight(night);
        PhaseParams params = PhaseTable.paramsFor(phase);
        float budgetMultiplier = slept ? PlagueConstants.SLEEP_BUDGET_MULTIPLIER : 1.0f;
        int extraGrowth = slept ? PlagueConstants.SLEEP_EXTRA_GROWTH : 0;
        NightResult r = runNightWith(grid, night, params, budgetMultiplier, extraGrowth, rng);
        return new NightResult(r.newlyInfected(), r.grown(), r.scarsHealed(), phase);
    }

    /** Ночь с явно заданными параметрами. Используется генератором старта. */
    public static NightResult runNightWith(PlagueGrid grid, int night, PhaseParams params,
                                           float budgetMultiplier, int extraGrowth,
                                           RandomGenerator rng) {
        final byte[] снимок = grid.levelsCopy();
        final int cells = grid.cellCount();

        // ── 1. рост на месте ───────────────────────────────────────────
        int выросло = 0;
        boolean растимСегодня = params.growthEveryNights() <= 1
            || night % params.growthEveryNights() == 0;
        if (растимСегодня) {
            int прирост = params.growthAmount() + extraGrowth;
            for (int i = 0; i < cells; i++) {
                int было = снимок[i];
                if (было > 0 && было < PlagueConstants.MAX_NATURAL_LEVEL) {
                    grid.setLevelAt(i, Math.min(PlagueConstants.MAX_NATURAL_LEVEL, было + прирост));
                    выросло++;
                }
            }
        }

        // ── 2. экспансия ───────────────────────────────────────────────
        int[] источники = собратьИсточники(снимок, cells);
        перемешать(источники, rng);

        int бюджет = Math.round(params.budget() * budgetMultiplier);
        int заражено = 0;

        for (int idx : источники) {
            if (бюджет <= 0) break;
            int cx = grid.chunkXOf(idx);
            int cz = grid.chunkZOf(idx);
            float силаИсточника = снимок[idx] / (float) PlagueConstants.MAX_NATURAL_LEVEL;

            for (int d = 0; d < 8 && бюджет > 0; d++) {
                int nx = cx + СМЕЩЕНИЯ_X[d];
                int nz = cz + СМЕЩЕНИЯ_Z[d];
                int ni = grid.index(nx, nz);
                if (ni < 0 || grid.getLevelAt(ni) != 0) continue;

                float p = params.base()
                    * силаИсточника
                    * grid.getTerrainAt(ni)
                    * (1f - grid.getResistanceAt(ni));

                if (rng.nextFloat() < p) {
                    grid.setLevelAt(ni, 1);
                    grid.setScarAt(ni, 0);
                    бюджет--;
                    заражено++;
                }
            }
        }

        // ── 3. таяние шрамов ───────────────────────────────────────────
        int зажило = 0;
        for (int i = 0; i < cells; i++) {
            if (grid.getLevelAt(i) != 0) continue;
            int шрам = grid.getScarAt(i);
            if (шрам > 0) {
                grid.setScarAt(i, шрам - 1);
                if (шрам - 1 == 0) зажило++;
            }
        }

        return new NightResult(заражено, выросло, зажило, PhaseTable.phaseForNight(night));
    }

    private static int[] собратьИсточники(byte[] снимок, int cells) {
        int n = 0;
        for (int i = 0; i < cells; i++) if (снимок[i] > 0) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < cells; i++) if (снимок[i] > 0) out[k++] = i;
        return out;
    }

    private static void перемешать(int[] a, RandomGenerator rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
