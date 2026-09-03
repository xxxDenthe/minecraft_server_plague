package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.core.CaveRule.CaveAction;
import dev.denthe.plaguecore.core.CaveRule.CaveSpot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Таблица подземелья. Ядро, раздел 8.4.
 *
 * Правило чистое: оно знает про место («стена», «потолок», «пол», «руда»)
 * и про вес места — число от нуля до единицы, посчитанное по координатам.
 * Перевод «BlockState → место» и «действие → BlockState» живёт в mc.
 */
class CaveRuleTest {

    /** Веса, которыми удобно проверять пороги: почти ноль и почти единица. */
    private static final float ПОЧТИ_НОЛЬ = 0.001f;
    private static final float ПОЧТИ_ЕДИНИЦА = 0.999f;

    @Test
    void наЧистомУровнеНичегоНеМеняется() {
        for (CaveSpot место : CaveSpot.values()) {
            assertEquals(CaveAction.NONE, CaveRule.actionFor(место, 0, ПОЧТИ_НОЛЬ), место.name());
            assertEquals(CaveAction.NONE, CaveRule.actionFor(место, 0, 0.5f), место.name());
        }
    }

    @Test
    void сплошнойКаменьНеТрогаемНикогда() {
        for (int уровень = 0; уровень <= 5; уровень++) {
            assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.SOLID, уровень, ПОЧТИ_НОЛЬ));
        }
    }

    @Test
    void стеныНаМалыхУровняхПокрываютсяРедкимиПятнами() {
        assertEquals(CaveAction.COAT_GROWTH, CaveRule.actionFor(CaveSpot.WALL, 1, ПОЧТИ_НОЛЬ));
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.WALL, 1, 0.5f));
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.WALL, 2, ПОЧТИ_ЕДИНИЦА));
    }

    @Test
    void стеныНаГнилиЗарастаютПочтиСплошь() {
        assertEquals(CaveAction.COAT_GROWTH, CaveRule.actionFor(CaveSpot.WALL, 3, ПОЧТИ_НОЛЬ));
        assertEquals(CaveAction.COAT_GROWTH, CaveRule.actionFor(CaveSpot.WALL, 3, 0.5f));
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.WALL, 4, ПОЧТИ_ЕДИНИЦА));
    }

    /**
     * Уровни вложены: всё, что заросло на втором, заросло и на третьем.
     * Иначе при росте эпидемии стена местами «выздоравливала» бы.
     */
    @Test
    void покрытиеСтенВложеноПоУровням() {
        for (int i = 0; i < 1000; i++) {
            float вес = i / 1000.0f;
            boolean наДвойке = CaveRule.actionFor(CaveSpot.WALL, 2, вес) == CaveAction.COAT_GROWTH;
            boolean наТройке = CaveRule.actionFor(CaveSpot.WALL, 3, вес) == CaveAction.COAT_GROWTH;
            if (наДвойке) assertTrue(наТройке, "вес " + вес + " зарос на 2, но не на 3");
        }
    }

    @Test
    void лозыСвисаютТолькоСГнили() {
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.CEILING, 1, ПОЧТИ_НОЛЬ));
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.CEILING, 2, ПОЧТИ_НОЛЬ));
        assertEquals(CaveAction.HANG_VINE, CaveRule.actionFor(CaveSpot.CEILING, 3, ПОЧТИ_НОЛЬ));
        assertEquals(CaveAction.HANG_VINE, CaveRule.actionFor(CaveSpot.CEILING, 4, ПОЧТИ_НОЛЬ));
    }

    @Test
    void потолокЗарастаетНеСплошь() {
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.CEILING, 4, ПОЧТИ_ЕДИНИЦА));
    }

    @Test
    void полГниётТолькоНаГнили() {
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.FLOOR, 1, 0.2f));
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.FLOOR, 2, 0.2f));
        assertEquals(CaveAction.ROTTED_DIRT, CaveRule.actionFor(CaveSpot.FLOOR, 3, 0.2f));
    }

    @Test
    void споровыйМешокРежеЧемГнилойПол() {
        assertEquals(CaveAction.SPORE_SAC, CaveRule.actionFor(CaveSpot.FLOOR, 3, ПОЧТИ_НОЛЬ));

        int мешков = 0, гнили = 0;
        for (int i = 0; i < 1000; i++) {
            CaveAction действие = CaveRule.actionFor(CaveSpot.FLOOR, 4, i / 1000.0f);
            if (действие == CaveAction.SPORE_SAC) мешков++;
            if (действие == CaveAction.ROTTED_DIRT) гнили++;
        }
        assertTrue(мешков > 0, "мешки не выпадают вовсе");
        assertTrue(мешков < гнили, "мешков " + мешков + ", гнили " + гнили + " — мешок должен быть редким");
    }

    @Test
    void полНеЗарастаетЦеликом() {
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.FLOOR, 4, ПОЧТИ_ЕДИНИЦА));
    }

    /**
     * Руда на Гнили покрывается коркой сплошь, без единого просвета:
     * в этом весь смысл — шахта становится не страшной, а менее выгодной.
     */
    @Test
    void рудаНаГнилиЗарастаетВся() {
        assertEquals(CaveAction.NONE, CaveRule.actionFor(CaveSpot.ORE, 2, ПОЧТИ_НОЛЬ));
        for (int i = 0; i < 1000; i++) {
            assertEquals(CaveAction.COAT_GROWTH, CaveRule.actionFor(CaveSpot.ORE, 3, i / 1000.0f));
        }
    }

    @Test
    void пятыйУровеньВедётСебяКакЧетвёртый() {
        for (CaveSpot место : CaveSpot.values()) {
            assertEquals(CaveRule.actionFor(место, 4, 0.1f), CaveRule.actionFor(место, 5, 0.1f),
                место.name());
        }
    }
}
