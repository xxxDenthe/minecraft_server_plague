package dev.denthe.plaguecore.core;

/**
 * Что делать с блоком на данном уровне заражения. Ядро, раздел 8.3.
 *
 * Правило знает про виды блоков, а не про сами блоки: «трава», «камень»,
 * «ствол». Перевод «BlockState → вид» и обратно «действие → BlockState»
 * живёт в mc/BlockTransforms. Так вся таблица остаётся чистой функцией
 * и проверяется обычным JUnit.
 *
 * Уровни 1–2 — «Следы» и «Поражён», косметика и подзол. Уровни 3+ —
 * «Гниль» и выше, необратимые на глаз изменения.
 */
public final class SurfaceRule {
    private SurfaceRule() {}

    /** Вид блока с точки зрения чумы. */
    public enum BlockKind {
        GRASS,   // травяной блок
        DIRT,    // земля и её родня, включая грядку
        LEAVES,  // листва
        LOG,     // стволы и доски
        STONE,   // камень и руда
        CROP,    // посевы
        WATER,   // вода
        OTHER    // всё прочее — не трогаем
    }

    /** Что сделать. */
    public enum PlagueAction {
        NONE,
        PODZOL,
        ROTTED_GRASS,
        ROTTED_DIRT,
        BLIGHTED_LEAVES,
        TRAMPLE_CROP,
        DESTROY_CROP,
        COAT_GROWTH;

        /** Нарост не заменяет блок, а кладётся плёнкой на соседний воздух. */
        public boolean isCoating() { return this == COAT_GROWTH; }

        public boolean isNothing() { return this == NONE; }
    }

    /** Граница между косметикой и Гнилью. */
    private static final int ГНИЛЬ = 3;

    public static PlagueAction actionFor(BlockKind kind, int level) {
        if (level <= 0) return PlagueAction.NONE;
        boolean гниль = level >= ГНИЛЬ;

        return switch (kind) {
            case GRASS  -> гниль ? PlagueAction.ROTTED_GRASS : PlagueAction.PODZOL;
            case DIRT   -> PlagueAction.ROTTED_DIRT;
            // Лоза на поверхности отменена решением владельца: она осталась
            // только в пещерах. Листва гниёт и на этом останавливается.
            case LEAVES -> PlagueAction.BLIGHTED_LEAVES;
            case LOG,
                 STONE  -> гниль ? PlagueAction.COAT_GROWTH : PlagueAction.NONE;
            case CROP   -> гниль ? PlagueAction.DESTROY_CROP : PlagueAction.TRAMPLE_CROP;
            // Вода в спеке становится «стоячей и тёмной», но своей жидкости
            // у нас нет, а ванильной такой не существует. Пока не трогаем.
            case WATER,
                 OTHER  -> PlagueAction.NONE;
        };
    }
}
