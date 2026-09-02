package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Страж кривой распространения.
 *
 * PhaseTableTest проверяет только арифметику бюджетов — сумму чисел из
 * таблицы. Этого мало: бюджет ночи это потолок, а не план, и живой прогон
 * показал 30% мира вместо 75-85%. Причина геометрическая: заражение растёт
 * по краю очага, край растёт как корень из площади, а бюджеты в спеке
 * растут линейно. Разбор: docs/superpowers/notes/2026-09-03-krivaya-rasprostraneniya.md
 *
 * Здесь прогоняется настоящая симуляция. Если кто-то поменяет числа фаз,
 * множители местности или число стартовых очагов и кривая уедет — падать
 * будет тут, а не на сервере за день до сессии.
 */
class SpreadCurveTest {

    private static RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }

    /** Доля мира на заданную ночь при стартовых настройках сессии. */
    private static float доляНаНочь(int ночей, long seed) {
        PlagueGrid g = new PlagueGrid(
            PlagueConstants.GRID_SIZE_CHUNKS,
            -(PlagueConstants.GRID_SIZE_CHUNKS / 2),
            -(PlagueConstants.GRID_SIZE_CHUNKS / 2));

        long[] очаги = StartGenerator.scatterEpicenters(
            g, PlagueConstants.START_EPICENTERS, rng(seed));
        StartGenerator.generate(g, PlagueConstants.START_INFECTION_PERCENT, очаги, rng(seed));

        for (int night = 1; night <= ночей; night++) {
            SpreadEngine.runNight(g, night, false, rng(night * 7919L + seed));
        }
        return g.infectedFraction() * 100f;
    }

    @Test
    void кТридцатойНочиМирЗаражёнНаТриЧетверти() {
        float сумма = 0;
        int прогонов = 5;
        StringBuilder подробности = new StringBuilder();

        for (int seed = 0; seed < прогонов; seed++) {
            float доля = доляНаНочь(30, 1234 + seed);
            сумма += доля;
            подробности.append(String.format("  сид %d: %.1f%%%n", 1234 + seed, доля));
            assertTrue(доля > 65f,
                "отдельный прогон не должен проваливаться ниже 65%:\n" + подробности);
        }

        float среднее = сумма / прогонов;
        assertTrue(среднее >= 72f && среднее <= 85f,
            String.format("к ночи 30 ожидается 72-85%% мира, вышло %.1f%%:%n%s", среднее, подробности));
    }

    @Test
    void кДесятойНочиЧумаЗаметнаНоМирЕщёЖив() {
        float доля = доляНаНочь(10, 1234);
        assertTrue(доля > 15f, "к ночи 10 чума должна быть заметна, вышло " + доля);
        assertTrue(доля < 60f, "к ночи 10 мир не должен быть уже потерян, вышло " + доля);
    }

    @Test
    void стартовоеЗаражениеОколоДесятиПроцентов() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        long[] очаги = StartGenerator.scatterEpicenters(g, PlagueConstants.START_EPICENTERS, rng(1));
        StartGenerator.generate(g, PlagueConstants.START_INFECTION_PERCENT, очаги, rng(1));
        assertEquals(10f, g.infectedFraction() * 100f, 2f);
    }
}
