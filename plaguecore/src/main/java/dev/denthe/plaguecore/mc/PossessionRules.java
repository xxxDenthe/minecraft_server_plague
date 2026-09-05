package dev.denthe.plaguecore.mc;

/**
 * Чистая арифметика одержимости. Заметка 2026-09-06-oderzhimost.
 *
 * Здесь нет ни одного {@code import net.minecraft} — нарочно.
 * Это не требование {@code CorePurityTest} (тот стережёт только пакет
 * {@code core}), а желание проверять выбор цели и упаковку нажатий
 * обычным JUnit, без запуска игры. Всё, что знает про мир, живёт
 * в {@link Possession}.
 */
public final class PossessionRules {
    private PossessionRules() {}

    // ── нажатия ────────────────────────────────────────────────────────

    /**
     * Биты одного нажатия. По сети идёт байт: семь клавиш в него влезают
     * с запасом, а поле фиксированной ширины нельзя раздуть подменённым
     * клиентом.
     */
    public static final int ВПЕРЁД = 1;
    public static final int НАЗАД = 1 << 1;
    public static final int ВЛЕВО = 1 << 2;
    public static final int ВПРАВО = 1 << 3;
    public static final int ПРЫЖОК = 1 << 4;
    public static final int КРАДУЧИСЬ = 1 << 5;
    public static final int УДАР = 1 << 6;

    public static int флаги(boolean вперёд, boolean назад, boolean влево, boolean вправо,
                            boolean прыжок, boolean крадучись, boolean удар) {
        int ф = 0;
        if (вперёд) ф |= ВПЕРЁД;
        if (назад) ф |= НАЗАД;
        if (влево) ф |= ВЛЕВО;
        if (вправо) ф |= ВПРАВО;
        if (прыжок) ф |= ПРЫЖОК;
        if (крадучись) ф |= КРАДУЧИСЬ;
        if (удар) ф |= УДАР;
        return ф;
    }

    public static boolean есть(int флаги, int бит) {
        return (флаги & бит) != 0;
    }

    // ── цель ───────────────────────────────────────────────────────────

    /**
     * Кто из списка ближе всех и не дальше радиуса.
     *
     * Считается по горизонтали: чума ведёт тело по земле, и сосед этажом
     * выше для неё не цель, а недоразумение.
     *
     * @return индекс в переданных массивах или -1, если бить некого
     */
    public static int ближайший(double x, double z, double[] цельX, double[] цельZ, double радиус) {
        int лучший = -1;
        double лучшее = радиус * радиус;
        for (int i = 0; i < цельX.length; i++) {
            double dx = цельX[i] - x;
            double dz = цельZ[i] - z;
            double d = dx * dx + dz * dz;
            if (d <= лучшее) {
                лучшее = d;
                лучший = i;
            }
        }
        return лучший;
    }

    /**
     * Рыскание, при котором взгляд смотрит вдоль (dx, dz).
     * Отсчёт как у Minecraft: ноль — на юг, то есть в сторону +Z.
     */
    public static float курс(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    /** Достаёт ли рука до цели по горизонтали. */
    public static boolean дотянуться(double dx, double dz, double дальность) {
        return dx * dx + dz * dz <= дальность * дальность;
    }

    // ── звать ли админа ────────────────────────────────────────────────

    /**
     * Пора ли предложить админа отдать это тело чуме.
     *
     * Условие «рядом есть сосед» — не украшение: чума идёт бить, и без
     * цели предложение бессмысленно, а чат у нас общий на восьмерых.
     *
     * @param тикПрошлогоЗова {@link Long#MIN_VALUE}, если ещё не звали
     */
    public static boolean предлагать(int стадия, int порог, boolean естьСосед,
                                     long тик, long тикПрошлогоЗова, int кулдаун) {
        if (стадия < порог) return false;
        if (!естьСосед) return false;
        if (тикПрошлогоЗова == Long.MIN_VALUE) return true;
        return тик - тикПрошлогоЗова >= кулдаун;
    }
}
