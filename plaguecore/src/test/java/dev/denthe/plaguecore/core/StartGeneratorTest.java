package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class StartGeneratorTest {

    private static RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }

    private static PlagueGrid пустая() {
        return new PlagueGrid(63, -31, -31);
    }

    @Test
    void упаковкаКоординатЧанкаОбратима() {
        long p = StartGenerator.packChunk(-17, 42);
        assertEquals(-17, StartGenerator.unpackX(p));
        assertEquals(42, StartGenerator.unpackZ(p));
    }

    @Test
    void генераторДостигаетЗаданнойДолиСТочностьюДвухПроцентов() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.10f, очаги, rng(123));

        assertEquals(0.10f, r.achievedFraction(), 0.02f,
            "должно получиться 10% ±2%, вышло " + r.achievedFraction());
        assertEquals(0.10f, g.infectedFraction(), 0.02f);
    }

    @Test
    void генераторРаботаетДляРазныхДолей() {
        for (float цель : new float[] { 0.05f, 0.10f, 0.25f, 0.50f }) {
            PlagueGrid g = пустая();
            long[] очаги = { StartGenerator.packChunk(0, 0), StartGenerator.packChunk(-15, 12) };
            StartGenerator.generate(g, цель, очаги, rng(7));
            assertEquals(цель, g.infectedFraction(), 0.02f,
                "цель " + цель + ", получено " + g.infectedFraction());
        }
    }

    @Test
    void одинаковыйСидДаётОдинаковыйМир() {
        long[] очаги = { StartGenerator.packChunk(3, -4) };
        PlagueGrid a = пустая();
        PlagueGrid b = пустая();
        StartGenerator.generate(a, 0.15f, очаги, rng(999));
        StartGenerator.generate(b, 0.15f, очаги, rng(999));
        assertArrayEquals(a.levelsCopy(), b.levelsCopy());
    }

    @Test
    void разныеСидыДаютРазныйМир() {
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        PlagueGrid a = пустая();
        PlagueGrid b = пустая();
        StartGenerator.generate(a, 0.15f, очаги, rng(1));
        StartGenerator.generate(b, 0.15f, очаги, rng(2));
        assertFalse(java.util.Arrays.equals(a.levelsCopy(), b.levelsCopy()));
    }

    @Test
    void очагиСтановятсяЗаражённымиСразу() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(10, 10), StartGenerator.packChunk(-10, -10) };
        StartGenerator.generate(g, 0.05f, очаги, rng(5));
        assertTrue(g.getLevel(10, 10) > 0);
        assertTrue(g.getLevel(-10, -10) > 0);
    }

    @Test
    void заражениеРастётВокругОчага() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.generate(g, 0.10f, очаги, rng(3));

        int рядом = 0;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                if (g.getLevel(cx, cz) > 0) рядом++;
            }
        }
        assertTrue(рядом > 50, "вокруг очага должно быть плотно, вышло " + рядом);
    }

    @Test
    void безОчаговНичегоНеПроисходит() {
        PlagueGrid g = пустая();
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.10f, new long[0], rng(1));
        assertEquals(0, g.countInfected());
        assertEquals(0, r.nightsSimulated());
    }

    @Test
    void генераторНеЗависаетПриНедостижимойЦели() {
        PlagueGrid g = пустая();
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) g.setTerrain(cx, cz, 0f);
        }
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.90f, очаги, rng(1));
        assertTrue(r.nightsSimulated() <= 2000, "должен упереться в предохранитель");
    }

    @Test
    void местностьВлияетНаФормуОчага() {
        PlagueGrid g = пустая();
        // западная половина — лава, восточная — трава
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                g.setTerrain(cx, cz, cx < 0 ? 0.1f : 1.4f);
            }
        }
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.generate(g, 0.10f, очаги, rng(11));

        int запад = 0, восток = 0;
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                if (g.getLevel(cx, cz) == 0) continue;
                if (cx < 0) запад++; else if (cx > 0) восток++;
            }
        }
        assertTrue(восток > запад * 2,
            "по траве должно уйти заметно дальше: запад " + запад + ", восток " + восток);
    }
}
