package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class SpreadEngineTest {

    private static RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }

    private static PlagueGrid пустая() {
        return new PlagueGrid(63, -31, -31);
    }

    private static PlagueGrid сОчагомВЦентре(int level) {
        PlagueGrid g = пустая();
        g.setLevel(0, 0, level);
        return g;
    }

    /**
     * Разреженная засевка: заражён каждый третий чанк по диагонали.
     *
     * Так у каждой чистой ячейки ровно три заражённых соседа, попыток за
     * ночь набирается несколько тысяч, и бюджет становится настоящим
     * ограничителем. Сплошная заливка половины сетки для этого не годится:
     * фронт вырождается в одну линию в 63 чанка, попыток выходит меньше
     * двух сотен, при base 0.04 заражается около дюжины ячеек — и до
     * бюджета дело просто не доходит.
     */
    private static void засеятьРазреженно(PlagueGrid g) {
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                if (Math.floorMod(cx + cz, 3) == 0) g.setLevel(cx, cz, 4);
            }
        }
    }

    @Test
    void бюджетНочиНеПревышается() {
        PlagueGrid g = пустая();
        засеятьРазреженно(g);
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(42));
        assertEquals(PhaseTable.paramsFor(0).budget(), r.newlyInfected(),
            "фаза 0 разрешает ровно бюджет фазы новых чанков за ночь");
    }

    @Test
    void сонУдваиваетБюджет() {
        PlagueGrid обычная = пустая();
        PlagueGrid сонная = пустая();
        засеятьРазреженно(обычная);
        засеятьРазреженно(сонная);

        SpreadEngine.NightResult без = SpreadEngine.runNight(обычная, 1, false, rng(7));
        SpreadEngine.NightResult со = SpreadEngine.runNight(сонная, 1, true, rng(7));

        int бюджет = PhaseTable.paramsFor(0).budget();
        assertEquals(бюджет, без.newlyInfected(), "без сна — бюджет фазы 0");
        assertEquals(бюджет * 2, со.newlyInfected(), "сон удваивает бюджет");
    }

    /**
     * Параметры гарантированного заражения: base 1.0 при источнике
     * уровня 4 даёт вероятность ровно 1.0, то есть результат детерминирован
     * и тест не может моргать.
     */
    private static PhaseParams гарантированные() {
        return new PhaseParams(1.0f, 1000, 99, 1);
    }

    /** Быстрые параметры для тестов, где важна форма распространения, а не темп. */
    private static PhaseParams быстрые() {
        return new PhaseParams(0.20f, 500, 1, 1);
    }

    @Test
    void заражениеРаспространяетсяИзОчага() {
        PlagueGrid g = сОчагомВЦентре(4);
        SpreadEngine.runNightWith(g, 1, гарантированные(), 1f, 0, rng(1));
        assertEquals(9, g.countInfected(),
            "при вероятности 1.0 должны заразиться очаг и все 8 соседей");
    }

    @Test
    void заражениеСимметричноПоЧетырёмСторонам() {
        PlagueGrid g = сОчагомВЦентре(4);
        for (int night = 1; night <= 20; night++) {
            SpreadEngine.runNightWith(g, night, быстрые(), 1f, 0, rng(1000 + night));
        }
        int север = 0, юг = 0, запад = 0, восток = 0;
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                if (g.getLevel(cx, cz) == 0) continue;
                if (cz < 0) север++;
                if (cz > 0) юг++;
                if (cx < 0) запад++;
                if (cx > 0) восток++;
            }
        }
        int[] стороны = { север, юг, запад, восток };
        int сумма = север + юг + запад + восток;
        assertTrue(сумма > 100, "за 20 ночей должно заразиться заметное число чанков, вышло " + сумма);
        float среднее = сумма / 4f;
        for (int с : стороны) {
            assertTrue(Math.abs(с - среднее) < среднее * 0.45f,
                "распространение перекошено: " + север + "/" + юг + "/" + запад + "/" + восток);
        }
    }

    @Test
    void местностьЗамедляетЗаражение() {
        PlagueGrid быстрая = сОчагомВЦентре(4);
        PlagueGrid медленная = сОчагомВЦентре(4);
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                быстрая.setTerrain(cx, cz, 1.4f);
                медленная.setTerrain(cx, cz, 0.1f);
            }
        }
        for (int night = 1; night <= 10; night++) {
            SpreadEngine.runNightWith(быстрая, night, быстрые(), 1f, 0, rng(500 + night));
            SpreadEngine.runNightWith(медленная, night, быстрые(), 1f, 0, rng(500 + night));
        }
        assertTrue(быстрая.countInfected() > медленная.countInfected() * 2,
            "лава должна тормозить сильно: трава " + быстрая.countInfected()
                + ", лава " + медленная.countInfected());
    }

    @Test
    void сопротивлениеСнижаетВероятностьЗаражения() {
        PlagueGrid защищённая = сОчагомВЦентре(4);
        PlagueGrid открытая = сОчагомВЦентре(4);
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                защищённая.setResistance(cx, cz, 1.0f);
            }
        }
        for (int night = 1; night <= 10; night++) {
            SpreadEngine.runNightWith(защищённая, night, быстрые(), 1f, 0, rng(900 + night));
            SpreadEngine.runNightWith(открытая, night, быстрые(), 1f, 0, rng(900 + night));
        }
        assertEquals(1, защищённая.countInfected(),
            "при сопротивлении 1.0 заражение не должно распространяться вообще");
        assertTrue(открытая.countInfected() > 1);
    }

    @Test
    void уровеньРастётНаМестеПоРитмуФазы() {
        PlagueGrid g = сОчагомВЦентре(1);
        // фаза 0: рост раз в 3 ночи
        SpreadEngine.runNight(g, 1, false, rng(1));
        SpreadEngine.runNight(g, 2, false, rng(2));
        assertEquals(1, g.getLevel(0, 0), "на ночах 1 и 2 роста быть не должно");
        SpreadEngine.runNight(g, 3, false, rng(3));
        assertEquals(2, g.getLevel(0, 0), "на ночи 3 уровень должен подрасти");
    }

    @Test
    void уровеньНеПревышаетЧетыре() {
        PlagueGrid g = сОчагомВЦентре(4);
        for (int night = 1; night <= 60; night++) {
            SpreadEngine.runNight(g, night, false, rng(night));
        }
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                assertTrue(g.getLevel(cx, cz) <= PlagueConstants.MAX_NATURAL_LEVEL,
                    "естественное распространение не должно давать уровень выше 4");
            }
        }
    }

    @Test
    void шрамыТаютПоОднойНочи() {
        PlagueGrid g = пустая();
        g.setScar(3, 3, 5);
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(4, g.getScar(3, 3));
        assertEquals(0, r.scarsHealed(), "шрам ещё не зажил полностью");

        for (int i = 0; i < 4; i++) SpreadEngine.runNight(g, 2 + i, false, rng(i));
        assertEquals(0, g.getScar(3, 3), "через 5 ночей шрам должен исчезнуть");
    }

    @Test
    void заживлениеШрамаСообщаетсяВРезультате() {
        PlagueGrid g = пустая();
        g.setScar(3, 3, 1);
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(1, r.scarsHealed());
    }

    @Test
    void заражениеСбрасываетШрам() {
        PlagueGrid g = сОчагомВЦентре(4);
        g.setScar(1, 0, 5);
        // при вероятности 1.0 сосед заражается гарантированно за одну ночь
        SpreadEngine.runNightWith(g, 1, гарантированные(), 1f, 0, rng(1));
        assertTrue(g.getLevel(1, 0) > 0, "сосед должен был заразиться");
        assertEquals(0, g.getScar(1, 0), "заражение должно обнулить шрам");
    }

    @Test
    void шрамНеТаетНаЗаражённомЧанке() {
        PlagueGrid g = пустая();
        g.setLevel(3, 3, 2);
        g.setScar(3, 3, 5);
        SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(5, g.getScar(3, 3), "шрам считается только на чистых чанках");
    }

    @Test
    void одинаковыйСидДаётОдинаковыйРезультат() {
        PlagueGrid a = сОчагомВЦентре(3);
        PlagueGrid b = сОчагомВЦентре(3);
        for (int night = 1; night <= 15; night++) {
            SpreadEngine.runNight(a, night, false, rng(night));
            SpreadEngine.runNight(b, night, false, rng(night));
        }
        assertArrayEquals(a.levelsCopy(), b.levelsCopy(), "симуляция должна быть детерминированной");
    }

    @Test
    void пустаяСеткаОстаётсяПустой() {
        PlagueGrid g = пустая();
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(0, r.newlyInfected());
        assertEquals(0, g.countInfected());
    }

    @Test
    void фазаВозвращаетсяВРезультате() {
        PlagueGrid g = пустая();
        assertEquals(0, SpreadEngine.runNight(g, 3, false, rng(1)).phase());
        assertEquals(3, SpreadEngine.runNight(g, 25, false, rng(1)).phase());
    }
}
