package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.core.SurfaceRule.BlockKind;
import dev.denthe.plaguecore.core.SurfaceRule.PlagueAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Таблица «было → стало». Ядро, раздел 8.3.
 *
 * Правило чистое: оно знает про виды блоков, а не про сами блоки.
 * Перевод «BlockState → вид» и «действие → BlockState» живёт в mc.
 */
class SurfaceRuleTest {

    @Test
    void наЧистомУровнеНичегоНеМеняется() {
        for (BlockKind вид : BlockKind.values()) {
            assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(вид, 0), вид.name());
        }
    }

    @Test
    void траваСначалаСтановитсяПодзоломПотомГниёт() {
        assertEquals(PlagueAction.PODZOL, SurfaceRule.actionFor(BlockKind.GRASS, 1));
        assertEquals(PlagueAction.PODZOL, SurfaceRule.actionFor(BlockKind.GRASS, 2));
        assertEquals(PlagueAction.ROTTED_GRASS, SurfaceRule.actionFor(BlockKind.GRASS, 3));
        assertEquals(PlagueAction.ROTTED_GRASS, SurfaceRule.actionFor(BlockKind.GRASS, 4));
    }

    @Test
    void земляГниётСразу() {
        for (int уровень = 1; уровень <= 4; уровень++) {
            assertEquals(PlagueAction.ROTTED_DIRT, SurfaceRule.actionFor(BlockKind.DIRT, уровень));
        }
    }

    @Test
    void листваГниётИНаЭтомОстанавливается() {
        // Лоза на поверхности отменена: она осталась только в пещерах.
        for (int уровень = 1; уровень <= 4; уровень++) {
            assertEquals(PlagueAction.BLIGHTED_LEAVES,
                SurfaceRule.actionFor(BlockKind.LEAVES, уровень), "уровень " + уровень);
        }
    }

    @Test
    void стволГниётТолькоНаГнили() {
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.LOG, 1));
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.LOG, 2));
        assertEquals(PlagueAction.ROTTED_LOG, SurfaceRule.actionFor(BlockKind.LOG, 3));
        assertEquals(PlagueAction.ROTTED_LOG, SurfaceRule.actionFor(BlockKind.LOG, 4));
    }

    /**
     * Доска — почти всегда чья-то постройка: её только обносит наростом.
     * Превращать сруб игрока в гнилой лес мы не подписывались.
     */
    @Test
    void доскиТолькоОбрастают() {
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.PLANKS, 2));
        assertEquals(PlagueAction.COAT_GROWTH, SurfaceRule.actionFor(BlockKind.PLANKS, 3));
        assertEquals(PlagueAction.COAT_GROWTH, SurfaceRule.actionFor(BlockKind.PLANKS, 4));
    }

    /**
     * Камень перерождается, а не обрастает: плёнка пятнами на утёсе
     * терялась из виду, и владелец сообщил, что камень «вообще
     * не заражается».
     */
    @Test
    void каменьПерерождаетсяНаГнили() {
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.STONE, 1));
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.STONE, 2));
        assertEquals(PlagueAction.ROTTED_STONE, SurfaceRule.actionFor(BlockKind.STONE, 3));
        assertEquals(PlagueAction.ROTTED_STONE, SurfaceRule.actionFor(BlockKind.STONE, 4));
    }

    @Test
    void посевыСначалаВытаптываютсяПотомГибнут() {
        assertEquals(PlagueAction.TRAMPLE_CROP, SurfaceRule.actionFor(BlockKind.CROP, 1));
        assertEquals(PlagueAction.TRAMPLE_CROP, SurfaceRule.actionFor(BlockKind.CROP, 2));
        assertEquals(PlagueAction.DESTROY_CROP, SurfaceRule.actionFor(BlockKind.CROP, 3));
        assertEquals(PlagueAction.DESTROY_CROP, SurfaceRule.actionFor(BlockKind.CROP, 4));
    }

    /**
     * Пучок травы заражается с первого же уровня: зелёные кустики посреди
     * Гнили выдавали, что чума прошлась только по кубам.
     */
    @Test
    void траваЗаражаетсяСразуИДальшеНеМеняется() {
        for (int уровень = 1; уровень <= 5; уровень++) {
            assertEquals(PlagueAction.BLIGHTED_GRASS,
                SurfaceRule.actionFor(BlockKind.PLANT, уровень), "уровень " + уровень);
        }
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.PLANT, 0));
    }

    @Test
    void прочиеБлокиНеТрогаемНикогда() {
        for (int уровень = 0; уровень <= 5; уровень++) {
            assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.OTHER, уровень));
            assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.WATER, уровень));
        }
    }

    @Test
    void уровеньСердцаВедётСебяКакГниль() {
        assertEquals(PlagueAction.ROTTED_GRASS, SurfaceRule.actionFor(BlockKind.GRASS, 5));
        assertEquals(PlagueAction.ROTTED_STONE, SurfaceRule.actionFor(BlockKind.STONE, 5));
        assertEquals(PlagueAction.ROTTED_LOG, SurfaceRule.actionFor(BlockKind.LOG, 5));
    }

    @Test
    void обрастаниеОтмеченоОтдельно() {
        assertTrue(PlagueAction.COAT_GROWTH.isCoating(),
            "нарост кладётся на соседний воздух, а не вместо блока");
        assertFalse(PlagueAction.ROTTED_DIRT.isCoating());
        assertFalse(PlagueAction.ROTTED_LOG.isCoating(),
            "бревно подменяется на месте, а не кладётся рядом");
        assertFalse(PlagueAction.NONE.isCoating());
    }

    @Test
    void бездействиеОтмеченоОтдельно() {
        assertTrue(PlagueAction.NONE.isNothing());
        assertFalse(PlagueAction.PODZOL.isNothing());
    }

    @Test
    void отрицательныйУровеньБезопасен() {
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.GRASS, -1));
    }

    /**
     * Высокая трава — те же кустики, только в две половины. Правило
     * про половины не знает вовсе: их различает уже переводчик блоков.
     */
    @Test
    void высокаяТраваЗаражаетсяСразу() {
        for (int уровень = 1; уровень <= 5; уровень++) {
            assertEquals(PlagueAction.BLIGHTED_TALL_GRASS,
                SurfaceRule.actionFor(BlockKind.TALL_PLANT, уровень), "уровень " + уровень);
        }
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.TALL_PLANT, 0));
    }

    /**
     * Цветы не гниют, а исчезают: своих цветов у чумы нет, а живое
     * жёлтое пятно посреди Гнили ломает картинку сильнее всего.
     */
    @Test
    void цветыИсчезаютСПервогоЖеУровня() {
        for (int уровень = 1; уровень <= 5; уровень++) {
            assertEquals(PlagueAction.DESTROY_PLANT,
                SurfaceRule.actionFor(BlockKind.FLOWER, уровень), "уровень " + уровень);
        }
        assertEquals(PlagueAction.NONE, SurfaceRule.actionFor(BlockKind.FLOWER, 0));
    }

    /** Мешок растёт только на Гнили и только на редких местах. */
    @Test
    void споровыйМешокТолькоНаГнилиИРедко() {
        assertFalse(SurfaceRule.sporeSacAt(2, 0.0f, 0.012f), "на подзоле мешков нет");
        assertTrue(SurfaceRule.sporeSacAt(3, 0.005f, 0.012f));
        assertFalse(SurfaceRule.sporeSacAt(3, 0.5f, 0.012f), "место не выпало");
        assertFalse(SurfaceRule.sporeSacAt(4, 0.0f, 0.0f), "доля ноль — мешков нет вовсе");
    }
}
