package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Две новые сетки: до какого уровня чанк уже отрисован в мире.
 * Дизайн материализации, раздел 3.1.
 */
class PlagueGridAppliedTest {

    @Test
    void новаяСеткаНичегоНеОтрисовала() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        assertEquals(0, g.getAppliedSurface(0, 0));
        assertEquals(0, g.getAppliedUnderground(0, 0));
    }

    @Test
    void отрисованныйУровеньЗапоминается() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setAppliedSurface(3, -7, 2);
        g.setAppliedUnderground(3, -7, 1);
        assertEquals(2, g.getAppliedSurface(3, -7));
        assertEquals(1, g.getAppliedUnderground(3, -7));
    }

    @Test
    void двеСеткиНезависимы() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setAppliedSurface(0, 0, 4);
        assertEquals(0, g.getAppliedUnderground(0, 0));
    }

    @Test
    void значенияЗажимаютсяВДиапазон() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setAppliedSurface(0, 0, 99);
        g.setAppliedUnderground(0, 0, -5);
        assertEquals(PlagueConstants.MAX_LEVEL, g.getAppliedSurface(0, 0));
        assertEquals(0, g.getAppliedUnderground(0, 0));
    }

    @Test
    void заПределамиСеткиЧитаетсяНоль() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        assertEquals(0, g.getAppliedSurface(9999, 9999));
        assertEquals(0, g.getAppliedUnderground(9999, 9999));
    }

    @Test
    void заПределамиСеткиЗаписьНеПадает() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        assertDoesNotThrow(() -> g.setAppliedSurface(9999, 9999, 3));
        assertDoesNotThrow(() -> g.setAppliedUnderground(9999, 9999, 3));
    }

    @Test
    void доступПоИндексуСовпадаетСДоступомПоКоординатам() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        int i = g.index(5, 6);
        g.setAppliedSurfaceAt(i, 3);
        assertEquals(3, g.getAppliedSurface(5, 6));
        assertEquals(3, g.getAppliedSurfaceAt(i));
    }

    @Test
    void отстающимСчитаетсяЧанкГдеУровеньВышеОтрисованного() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setLevel(1, 1, 3);
        g.setAppliedSurface(1, 1, 1);
        assertTrue(g.surfaceOutOfDate(1, 1));

        g.setAppliedSurface(1, 1, 3);
        assertFalse(g.surfaceOutOfDate(1, 1));
    }

    @Test
    void отстающимСчитаетсяИЧанкГдеОтрисованоБольшеЧемЕсть() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setLevel(2, 2, 1);
        g.setAppliedSurface(2, 2, 4);
        assertTrue(g.surfaceOutOfDate(2, 2), "очистка тоже требует перерисовки");
    }
}
