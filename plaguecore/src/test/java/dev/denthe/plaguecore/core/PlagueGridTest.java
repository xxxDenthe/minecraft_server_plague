package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlagueGridTest {

    private PlagueGrid сетка() {
        return new PlagueGrid(63, -31, -31);
    }

    @Test
    void размерыИКоличествоЯчеек() {
        PlagueGrid g = сетка();
        assertEquals(63, g.size());
        assertEquals(-31, g.originX());
        assertEquals(-31, g.originZ());
        assertEquals(63 * 63, g.cellCount());
    }

    @Test
    void границыСеткиОпределяютсяВерно() {
        PlagueGrid g = сетка();
        assertTrue(g.contains(-31, -31));
        assertTrue(g.contains(31, 31));
        assertTrue(g.contains(0, 0));
        assertFalse(g.contains(-32, 0));
        assertFalse(g.contains(32, 0));
        assertFalse(g.contains(0, 32));
    }

    @Test
    void уровеньСохраняетсяИЧитается() {
        PlagueGrid g = сетка();
        g.setLevel(5, -7, 3);
        assertEquals(3, g.getLevel(5, -7));
        assertEquals(0, g.getLevel(6, -7));
    }

    @Test
    void уровеньОграниченДиапазоном() {
        PlagueGrid g = сетка();
        g.setLevel(0, 0, 99);
        assertEquals(PlagueConstants.MAX_LEVEL, g.getLevel(0, 0));
        g.setLevel(0, 0, -5);
        assertEquals(0, g.getLevel(0, 0));
    }

    @Test
    void чтениеЗаГраницейВозвращаетНольБезИсключения() {
        PlagueGrid g = сетка();
        assertEquals(0, g.getLevel(999, 999));
        assertEquals(0f, g.getResistance(999, 999));
        assertEquals(0, g.getScar(999, 999));
    }

    @Test
    void записьЗаГраницейИгнорируется() {
        PlagueGrid g = сетка();
        assertDoesNotThrow(() -> g.setLevel(999, 999, 4));
        assertEquals(0, g.getLevel(999, 999));
    }

    @Test
    void сопротивлениеХранитсяСТочностьюДоСотой() {
        PlagueGrid g = сетка();
        g.setResistance(2, 2, 0.5f);
        assertEquals(0.5f, g.getResistance(2, 2), 0.01f);
        g.setResistance(2, 2, 1.0f);
        assertEquals(1.0f, g.getResistance(2, 2), 0.01f);
        g.setResistance(2, 2, 5.0f);
        assertEquals(1.0f, g.getResistance(2, 2), 0.01f, "значение выше единицы обрезается");
    }

    @Test
    void местностьХранитсяСТочностьюДоДесятой() {
        PlagueGrid g = сетка();
        g.setTerrain(1, 1, 1.4f);
        assertEquals(1.4f, g.getTerrain(1, 1), 0.05f);
        g.setTerrain(1, 2, 0.1f);
        assertEquals(0.1f, g.getTerrain(1, 2), 0.05f);
    }

    @Test
    void местностьПоУмолчаниюЕдиница() {
        PlagueGrid g = сетка();
        assertEquals(1.0f, g.getTerrain(0, 0), 0.05f);
    }

    @Test
    void подсчётЗаражённыхЯчеек() {
        PlagueGrid g = сетка();
        assertEquals(0, g.countInfected());
        g.setLevel(0, 0, 1);
        g.setLevel(1, 0, 4);
        g.setLevel(2, 0, 0);
        assertEquals(2, g.countInfected());
        assertEquals(2f / (63 * 63), g.infectedFraction(), 1e-6);
    }

    @Test
    void копияУровнейНеСвязанаСОригиналом() {
        PlagueGrid g = сетка();
        g.setLevel(0, 0, 3);
        byte[] copy = g.levelsCopy();
        g.setLevel(0, 0, 1);
        int idx = (0 - g.originZ()) * g.size() + (0 - g.originX());
        assertEquals(3, copy[idx]);
        assertEquals(1, g.getLevel(0, 0));
    }
}
