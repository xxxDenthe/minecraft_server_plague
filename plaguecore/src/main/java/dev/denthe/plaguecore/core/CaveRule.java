package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

/**
 * Что делать с блоком под землёй. Ядро, раздел 8.4.
 *
 * Правило знает про место, а не про блок: «стена пещеры», «потолок»,
 * «пол», «руда». Перевод «BlockState → место» и «действие → BlockState»
 * живёт в mc/CaveTransforms. Так вся таблица остаётся чистой функцией
 * и проверяется обычным JUnit.
 *
 * Второй довод — вес места: число от нуля до единицы, посчитанное по
 * координатам блока хэшем. Через него выражаются и «редкие пятна»,
 * и «сплошь», и редкость спорового мешка, причём детерминированно:
 * тот же блок при том же уровне всегда получает то же решение.
 *
 * Пороги растут вместе с уровнем, поэтому покрытие вложено: всё, что
 * заросло на втором уровне, заросло и на третьем. Иначе при росте
 * эпидемии стена местами выздоравливала бы.
 *
 * Сплошной массив камня не трогаем вообще — чума растёт там, где есть
 * полость. Это и дешевле, и логичнее.
 */
public final class CaveRule {
    private CaveRule() {}

    /** Где стоит блок с точки зрения пещеры. */
    public enum CaveSpot {
        WALL,     // граничит с воздухом сбоку
        CEILING,  // под ним воздух — с него свисают лозы
        FLOOR,    // над ним воздух — по нему ходят
        ORE,      // рудная жила, открытая в полость
        SOLID     // замурован со всех сторон — не наше дело
    }

    /** Что сделать. */
    public enum CaveAction {
        NONE,
        /** Плёнка нароста на соседнем воздухе; сам блок цел. */
        COAT_GROWTH,
        /** Лоза в воздухе под блоком. */
        HANG_VINE,
        /** Блок пола становится гнилой землёй. */
        ROTTED_DIRT,
        /** Гнилой пол плюс мешок в воздухе над ним. */
        SPORE_SAC;

        public boolean isNothing() { return this == NONE; }
    }

    /** Граница между косметикой и Гнилью — та же, что на поверхности. */
    private static final int ГНИЛЬ = 3;

    /**
     * @param spot  где стоит блок
     * @param level уровень заражения чанка, 0..5
     * @param weight вес места, от нуля до единицы
     */
    public static CaveAction actionFor(CaveSpot spot, int level, float weight) {
        if (level <= 0) return CaveAction.NONE;
        boolean гниль = level >= ГНИЛЬ;

        return switch (spot) {
            case SOLID -> CaveAction.NONE;

            case WALL -> {
                float порог = гниль
                    ? PlagueConstants.CAVE_WALL_DENSE
                    : PlagueConstants.CAVE_WALL_SPARSE;
                yield weight < порог ? CaveAction.COAT_GROWTH : CaveAction.NONE;
            }

            // Корка на руде сплошная и без просветов: смысл не в страхе,
            // а в том, что шахта становится менее выгодной — сперва сними
            // нарост, потом добывай.
            case ORE -> гниль ? CaveAction.COAT_GROWTH : CaveAction.NONE;

            case CEILING -> гниль && weight < PlagueConstants.CAVE_CEILING_VINES
                ? CaveAction.HANG_VINE
                : CaveAction.NONE;

            case FLOOR -> {
                if (!гниль) yield CaveAction.NONE;
                if (weight < PlagueConstants.CAVE_SPORE_SAC) yield CaveAction.SPORE_SAC;
                yield weight < PlagueConstants.CAVE_FLOOR_ROT
                    ? CaveAction.ROTTED_DIRT
                    : CaveAction.NONE;
            }
        };
    }
}
