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
        LOG,     // стволы: живые деревья
        PLANKS,  // доски и прочая обработанная древесина: постройки
        STONE,   // камень и руда
        CROP,    // посевы
        PLANT,   // пучок травы, папоротник — мелочь, растущая на земле
        TALL_PLANT, // высокая трава и большой папоротник: две половины
        FLOWER,  // цветы, одиночные и высокие
        WATER,   // вода
        OTHER    // всё прочее — не трогаем
    }

    /** Что сделать. */
    public enum PlagueAction {
        NONE,
        PODZOL,
        ROTTED_GRASS,
        ROTTED_DIRT,
        ROTTED_STONE,
        ROTTED_LOG,
        BLIGHTED_GRASS,
        BLIGHTED_TALL_GRASS,
        BLIGHTED_LEAVES,
        TRAMPLE_CROP,
        DESTROY_CROP,
        DESTROY_PLANT,
        COAT_GROWTH;

        /** Нарост не заменяет блок, а кладётся плёнкой на соседний воздух. */
        public boolean isCoating() { return this == COAT_GROWTH; }

        public boolean isNothing() { return this == NONE; }
    }

    /** Граница между косметикой и Гнилью. */
    private static final int ГНИЛЬ = 3;

    /**
     * Растёт ли на этом месте споровый мешок. Поверхностный близнец
     * правила пещер (CaveRule, действие SPORE_SAC).
     *
     * Только на Гнили и только редко: мешок — не украшение земли,
     * а знак, что здесь чума уже своя.
     *
     * @param weight вес места из маски, число в [0, 1)
     * @param доля   какая часть мест получает мешок
     */
    public static boolean sporeSacAt(int level, float weight, float доля) {
        return level >= ГНИЛЬ && weight < доля;
    }

    public static PlagueAction actionFor(BlockKind kind, int level) {
        if (level <= 0) return PlagueAction.NONE;
        boolean гниль = level >= ГНИЛЬ;

        return switch (kind) {
            case GRASS  -> гниль ? PlagueAction.ROTTED_GRASS : PlagueAction.PODZOL;
            case DIRT   -> PlagueAction.ROTTED_DIRT;
            // Лоза на поверхности отменена решением владельца: она осталась
            // только в пещерах. Листва гниёт и на этом останавливается.
            case LEAVES -> PlagueAction.BLIGHTED_LEAVES;
            // Камень на поверхности перерождается, а не обрастает: плёнка
            // пятнами на утёсе терялась из виду.
            case STONE  -> гниль ? PlagueAction.ROTTED_STONE : PlagueAction.NONE;
            // Ствол на Гнили тоже перерождается: у нас есть своё гнилое
            // бревно, и целое дерево в мёртвом лесу выдавало, что чума
            // прошлась только по земле.
            case LOG    -> гниль ? PlagueAction.ROTTED_LOG : PlagueAction.NONE;
            // Доски — почти всегда чья-то постройка. Их только обносит
            // наростом: превращать сруб игрока в лес мы не подписывались.
            case PLANKS -> гниль ? PlagueAction.COAT_GROWTH : PlagueAction.NONE;
            case CROP   -> гниль ? PlagueAction.DESTROY_CROP : PlagueAction.TRAMPLE_CROP;
            // Трава заражается с первого же уровня и дальше не меняется:
            // зелёные кустики посреди Гнили выдавали, что чума прошлась
            // только по кубам.
            case PLANT  -> PlagueAction.BLIGHTED_GRASS;
            // Высокая трава — те же кустики, только в две половины.
            // Каждая половина подменяется сама по себе: обе лежат в одном
            // столбце, и проход берёт их подряд.
            case TALL_PLANT -> PlagueAction.BLIGHTED_TALL_GRASS;
            // Цветы не гниют, а исчезают. Своих цветов у чумы нет, а живое
            // жёлтое пятно посреди Гнили ломает картинку сильнее всего —
            // решение владельца.
            case FLOWER -> PlagueAction.DESTROY_PLANT;
            // Вода в спеке становится «стоячей и тёмной», но своей жидкости
            // у нас нет, а ванильной такой не существует. Пока не трогаем.
            case WATER,
                 OTHER  -> PlagueAction.NONE;
        };
    }
}
