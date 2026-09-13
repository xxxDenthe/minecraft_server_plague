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

    /**
     * Сетка, сдвинутая под произвольный центр мира (/plague center),
     * кладёт центральный чанк ровно в среднюю ячейку — иначе карта
     * в /plague gui рисовала бы центр не по центру, а граница мира
     * не совпадала бы с сеткой.
     */
    @Test
    void центральныйЧанкПопадаетВСереднююЯчейку() {
        int размер = PlagueConstants.GRID_SIZE_CHUNKS;
        for (int центр : new int[] { 0, 125, -125, 1000 }) {
            PlagueGrid g = new PlagueGrid(размер, центр - размер / 2, центр - размер / 2);
            int середина = (размер / 2) * размер + (размер / 2);
            assertEquals(середина, g.index(центр, центр));
            assertEquals(центр, g.chunkXOf(середина));
            assertEquals(центр, g.chunkZOf(середина));
            // граница мира влезает в сетку целиком, с запасом
            int половина = размер / 2;
            assertTrue(g.contains(центр - половина, центр - половина));
            assertTrue(g.contains(центр + половина, центр + половина));
            assertFalse(g.contains(центр - половина - 1, центр));
            assertTrue(размер * 16 >= PlagueConstants.WORLD_SIZE_BLOCKS,
                "сетка обязана покрывать границу мира: "
                + (размер * 16) + " < " + PlagueConstants.WORLD_SIZE_BLOCKS);
        }
    }

    /**
     * Перенос центра (/plague center) не стирает эпидемию: всё, что попало
     * в пересечение старого и нового квадрата, остаётся на тех же
     * абсолютных координатах чанка. Ради этого перенос и делается посреди
     * сессии — чуме нужна чистая земля за бывшим краем, а не рестарт.
     */
    @Test
    void переносЦентраСохраняетПересечение() {
        PlagueGrid g = new PlagueGrid(9, 0, 0); // чанки 0..8
        g.setLevel(4, 4, 3);
        g.setLevel(8, 8, 2);
        g.setLevel(0, 0, 4);     // выедет за новый квадрат
        g.setScar(4, 4, 5);
        g.setResistance(4, 4, 0.5f);
        g.setTerrain(4, 4, 1.4f);
        g.setAppliedSurface(4, 4, 2);
        g.setAppliedUnderground(4, 4, 1);

        PlagueGrid n = g.movedTo(4, 4); // чанки 4..12

        assertEquals(9, n.size());
        assertEquals(4, n.originX());
        assertEquals(3, n.getLevel(4, 4), "заражение переезжает по координатам чанка");
        assertEquals(2, n.getLevel(8, 8));
        assertEquals(5, n.getScar(4, 4));
        assertEquals(0.5f, n.getResistance(4, 4), 0.011f);
        assertEquals(1.4f, n.getTerrain(4, 4), 0.011f);
        assertEquals(2, n.getAppliedSurface(4, 4));
        assertEquals(1, n.getAppliedUnderground(4, 4));

        assertFalse(n.contains(0, 0), "старый угол остался за краем");
        assertEquals(2, n.countInfected(), "за краем осталось ровно то, что не влезло");

        // новые чанки — чистые, с нейтральным множителем местности
        assertEquals(0, n.getLevel(12, 12));
        assertEquals(1.0f, n.getTerrain(12, 12), 0.011f);

        // старая сетка не тронута
        assertEquals(4, g.getLevel(0, 0));
    }
}
