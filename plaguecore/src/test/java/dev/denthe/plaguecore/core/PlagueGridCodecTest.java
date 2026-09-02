package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlagueGridCodecTest {

    private PlagueGrid заполненная() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setLevel(0, 0, 4);
        g.setLevel(-31, -31, 1);
        g.setLevel(31, 31, 5);
        g.setResistance(5, 5, 0.75f);
        g.setScar(-7, 3, 4);
        g.setTerrain(2, 2, 1.4f);
        g.setTerrain(3, 3, 0.1f);
        return g;
    }

    @Test
    void кодированиеИДекодированиеСохраняютВсёСодержимое() {
        PlagueGrid оригинал = заполненная();
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(оригинал));

        assertEquals(оригинал.size(), копия.size());
        assertEquals(оригинал.originX(), копия.originX());
        assertEquals(оригинал.originZ(), копия.originZ());

        assertEquals(4, копия.getLevel(0, 0));
        assertEquals(1, копия.getLevel(-31, -31));
        assertEquals(5, копия.getLevel(31, 31));
        assertEquals(0.75f, копия.getResistance(5, 5), 0.01f);
        assertEquals(4, копия.getScar(-7, 3));
        assertEquals(1.4f, копия.getTerrain(2, 2), 0.05f);
        assertEquals(0.1f, копия.getTerrain(3, 3), 0.05f);
    }

    @Test
    void всеЯчейкиСовпадаютПослеКруга() {
        PlagueGrid оригинал = заполненная();
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(оригинал));
        assertArrayEquals(оригинал.levelsCopy(), копия.levelsCopy());
    }

    @Test
    void размерБлобаПредсказуем() {
        PlagueGrid g = заполненная();
        byte[] blob = PlagueGridCodec.encode(g);
        int ожидаемо = 1 + 4 + 4 + 4 + 4 * (63 * 63);
        assertEquals(ожидаемо, blob.length,
            "версия + размер + originX + originZ + четыре массива");
        assertTrue(blob.length < 20_000, "весь мир должен весить меньше 20 КБ");
    }

    @Test
    void перваяЯчейкаЭтоВерсияФормата() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        assertEquals(PlagueGridCodec.FORMAT_VERSION, blob[0]);
    }

    @Test
    void неизвестнаяВерсияОтвергается() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        blob[0] = 99;
        assertThrows(IllegalArgumentException.class, () -> PlagueGridCodec.decode(blob));
    }

    @Test
    void обрезанныйБлобОтвергается() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        byte[] обрезанный = java.util.Arrays.copyOf(blob, blob.length / 2);
        assertThrows(IllegalArgumentException.class, () -> PlagueGridCodec.decode(обрезанный));
    }

    @Test
    void пустаяСеткаПереживаетКруг() {
        PlagueGrid пустая = new PlagueGrid(63, -31, -31);
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(пустая));
        assertEquals(0, копия.countInfected());
        assertEquals(1.0f, копия.getTerrain(0, 0), 0.05f);
    }
}
