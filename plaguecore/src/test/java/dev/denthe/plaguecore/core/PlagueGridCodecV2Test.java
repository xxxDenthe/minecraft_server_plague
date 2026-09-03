package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Формат сетки версии 2: добавились appliedSurface и appliedUnderground.
 * Старые сохранения читаются, новые сетки в них заполняются нулями.
 */
class PlagueGridCodecV2Test {

    private PlagueGrid заполненная() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setLevel(0, 0, 4);
        g.setResistance(5, 5, 0.75f);
        g.setScar(-7, 3, 4);
        g.setTerrain(2, 2, 1.4f);
        g.setAppliedSurface(0, 0, 3);
        g.setAppliedUnderground(0, 0, 2);
        g.setAppliedSurface(-31, -31, 1);
        return g;
    }

    @Test
    void версияФорматаТеперьВторая() {
        assertEquals(2, PlagueGridCodec.FORMAT_VERSION);
    }

    @Test
    void отрисованныеУровниПереживаютКруг() {
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(заполненная()));
        assertEquals(3, копия.getAppliedSurface(0, 0));
        assertEquals(2, копия.getAppliedUnderground(0, 0));
        assertEquals(1, копия.getAppliedSurface(-31, -31));
    }

    @Test
    void размерБлобаЭтоШестьМассивов() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        assertEquals(1 + 4 + 4 + 4 + 6 * (63 * 63), blob.length);
    }

    @Test
    void старыйБлобВерсииОдинЧитаетсяСНулевымиОтрисовками() {
        PlagueGrid оригинал = заполненная();
        byte[] старый = блобВерсии1(оригинал);

        PlagueGrid читанная = PlagueGridCodec.decode(старый);

        assertEquals(4, читанная.getLevel(0, 0), "старые данные не должны потеряться");
        assertEquals(0.75f, читанная.getResistance(5, 5), 0.01f);
        assertEquals(4, читанная.getScar(-7, 3));
        assertEquals(1.4f, читанная.getTerrain(2, 2), 0.05f);
        assertEquals(0, читанная.getAppliedSurface(0, 0), "мир перерисуется целиком");
        assertEquals(0, читанная.getAppliedUnderground(0, 0));
    }

    @Test
    void старыйБлобНеправильнойДлиныОтвергается() {
        byte[] мусор = new byte[]{1, 0, 0, 0, 63, 0, 0, 0, 0, 0, 0, 0, 0, 7, 7};
        assertThrows(IllegalArgumentException.class, () -> PlagueGridCodec.decode(мусор));
    }

    /** Ручная сборка блоба старого формата: четыре массива и версия 1. */
    private static byte[] блобВерсии1(PlagueGrid g) {
        int cells = g.cellCount();
        ByteBuffer buf = ByteBuffer.allocate(13 + cells * 4);
        buf.put((byte) 1);
        buf.putInt(g.size());
        buf.putInt(g.originX());
        buf.putInt(g.originZ());
        buf.put(g.rawLevels());
        buf.put(g.rawResistance());
        buf.put(g.rawScar());
        buf.put(g.rawTerrain());
        return buf.array();
    }
}
