package dev.denthe.plaguecore;

/**
 * Все игровые числа в одном месте. Позже переедут в конфиг.
 */
public final class PlagueConstants {
    private PlagueConstants() {}

    /** Максимальный уровень заражения чанка. 5 выставляет только подсистема логова. */
    public static final int MAX_LEVEL = 5;

    /** Потолок, до которого поднимается обычное распространение. */
    public static final int MAX_NATURAL_LEVEL = 4;

    /** Сколько ночей держится шрам после полной очистки. */
    public static final int SCAR_NIGHTS = 5;

    /** Во сколько раз растёт бюджет ночи, если игроки спали. */
    public static final float SLEEP_BUDGET_MULTIPLIER = 2.0f;

    /** Дополнительный рост уровня на месте при сне. */
    public static final int SLEEP_EXTRA_GROWTH = 1;

    /** Сторона мира в блоках. */
    public static final int WORLD_SIZE_BLOCKS = 1000;

    /** Сторона сетки в чанках. 63 × 16 = 1008 — покрывает границу с запасом. */
    public static final int GRID_SIZE_CHUNKS = 63;

    /** Доля мира, заражённая на старте сессии. */
    public static final float START_INFECTION_PERCENT = 0.10f;

    /**
     * Сколько очагов сажается на старте сессии.
     *
     * Число не косметическое, а определяющее темп: заражение растёт только
     * по краю очага, поэтому один большой очаг ползёт втрое медленнее, чем
     * та же площадь, разбитая на десятки мелких. При одном очаге к ночи 30
     * заражено 23% мира, при 32 — 75%, как и задумано спеком.
     * Разбор: docs/superpowers/notes/2026-09-03-krivaya-rasprostraneniya.md
     */
    public static final int START_EPICENTERS = 32;
}
