package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Математика заражения игрока. Спек подсистемы 2, разделы 2, 3, 5, 6.
 *
 * Числа берутся из PlagueConstants, а не вписываются в тест руками:
 * иначе правка конфига ломала бы тесты, а она обязана быть свободной.
 */
class InfectionMathTest {

    @Test
    void стадияВыводитсяИзОчков() {
        assertEquals(0, InfectionMath.стадия(0f));
        assertEquals(0, InfectionMath.стадия(9.99f));
        assertEquals(1, InfectionMath.стадия(10f));
        assertEquals(1, InfectionMath.стадия(29.99f));
        assertEquals(2, InfectionMath.стадия(30f));
        assertEquals(2, InfectionMath.стадия(59.99f));
        assertEquals(3, InfectionMath.стадия(60f));
        assertEquals(3, InfectionMath.стадия(89.99f));
        assertEquals(4, InfectionMath.стадия(90f));
        assertEquals(4, InfectionMath.стадия(100f));
    }

    @Test
    void подЗемлёйЗаражениеБыстрее() {
        float сверху = InfectionMath.экспозиция(4, false, 0f);
        float снизу = InfectionMath.экспозиция(4, true, 0f);
        assertTrue(снизу > сверху, "под землёй должно быть быстрее");
        assertEquals(сверху * PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER, снизу, 1e-4);
    }

    @Test
    void защитаЗамедляетНоНеЛечит() {
        float безЗащиты = InfectionMath.экспозиция(3, false, 0f);
        float вБроне = InfectionMath.экспозиция(3, false, 0.5f);
        assertEquals(безЗащиты / 2f, вБроне, 1e-4);
        // Полная защита обнуляет набор, но не превращает его в лечение.
        assertEquals(0f, InfectionMath.экспозиция(3, false, 1f), 1e-4);
    }

    @Test
    void чистыйВоздухНеУмножаетсяНаЗащиту() {
        // Броня не мешает выздоравливать: защита действует только на набор.
        assertEquals(InfectionMath.экспозиция(0, false, 0f),
                     InfectionMath.экспозиция(0, false, 0.9f), 1e-4);
    }

    @Test
    void воздухЛечитТолькоДоПотолка() {
        float потолок = InfectionMath.потолокВосстановления();
        assertEquals(30f, потолок, 1e-4);

        // Выше потолка чистый воздух не работает вовсе.
        assertEquals(59f, InfectionMath.следующая(59f, -0.05f), 1e-4);
        assertEquals(30.5f, InfectionMath.следующая(30.5f, -0.05f), 1e-4);

        // На потолке и ниже — лечит.
        assertEquals(29.95f, InfectionMath.следующая(30f, -0.05f), 1e-4);
        assertEquals(9.95f, InfectionMath.следующая(10f, -0.05f), 1e-4);
    }

    @Test
    void набирающаяЭкспозицияПотолкомНеОграничена() {
        // Потолок стережёт только выздоровление. Заражаться можно до сотни.
        assertEquals(59.2f, InfectionMath.следующая(59f, 0.20f), 1e-4);
    }

    @Test
    void заражённостьЗажатаОтНуляДоСотни() {
        assertEquals(0f, InfectionMath.следующая(0.01f, -0.05f), 1e-4);
        assertEquals(100f, InfectionMath.следующая(99.99f, 0.20f), 1e-4);
    }

    @Test
    void силаОтвараПадаетПоТаблице() {
        assertEquals(13f, InfectionMath.силаОтвара(0), 1e-4);
        assertEquals(10f, InfectionMath.силаОтвара(1), 1e-4);
        assertEquals(8f, InfectionMath.силаОтвара(2), 1e-4);
        assertEquals(7f, InfectionMath.силаОтвара(3), 1e-4);
        assertEquals(6f, InfectionMath.силаОтвара(4), 1e-4);
        assertEquals(5f, InfectionMath.силаОтвара(5), 1e-4);
        // Дальше таблицы сила не падает: держится последнее значение.
        assertEquals(5f, InfectionMath.силаОтвара(6), 1e-4);
        assertEquals(5f, InfectionMath.силаОтвара(99), 1e-4);
    }

    @Test
    void счётчикГлотковРастётПодрядИСбрасываетсяПаузой() {
        int сброс = PlagueConstants.PLAYER_BREW_RESET_TICKS;

        // Первый глоток в жизни.
        assertEquals(0, InfectionMath.счётчикГлотков(0, -1L, 1000L));

        // Второй сразу за первым.
        assertEquals(1, InfectionMath.счётчикГлотков(1, 1000L, 1100L));

        // Ровно на границе окна счётчик ещё живёт.
        assertEquals(3, InfectionMath.счётчикГлотков(3, 1000L, 1000L + сброс));

        // Через тик после границы — обнуление.
        assertEquals(0, InfectionMath.счётчикГлотков(3, 1000L, 1001L + сброс));
    }

    @Test
    void сПотолкаВторойСтадииТриОтвараДоводятДоПервой() {
        // Прикидка из спека: 59 → 46 → 36 → 28.
        float очки = 59f;
        очки -= InfectionMath.силаОтвара(0);
        assertEquals(46f, очки, 1e-4);
        очки -= InfectionMath.силаОтвара(1);
        assertEquals(36f, очки, 1e-4);
        очки -= InfectionMath.силаОтвара(2);
        assertEquals(28f, очки, 1e-4);
        assertEquals(1, InfectionMath.стадия(очки));
    }

    @Test
    void постоянныйШтрафНакапливаетсяДоПола() {
        assertEquals(0f, InfectionMath.постоянныйШтраф(0), 1e-4);
        assertEquals(1f, InfectionMath.постоянныйШтраф(1), 1e-4);
        assertEquals(7f, InfectionMath.постоянныйШтраф(7), 1e-4);
        // Пол 6 HP: больше 14 HP смерти не отнимают никогда.
        assertEquals(14f, InfectionMath.постоянныйШтраф(14), 1e-4);
        assertEquals(14f, InfectionMath.постоянныйШтраф(99), 1e-4);
    }

    @Test
    void временныйШтрафНеДоводитЗдоровьеДоНуля() {
        // Здоровый: штрафа нет.
        assertEquals(0f, InfectionMath.временныйШтраф(0, 0f), 1e-4);

        // Обычный больной: полный штраф стадии.
        assertEquals(2f, InfectionMath.временныйШтраф(2, 0f), 1e-4);
        assertEquals(6f, InfectionMath.временныйШтраф(3, 0f), 1e-4);

        // Четырнадцать HP уже потеряно навсегда: осталось 6.
        // Штраф стадии 3 (−6) обнулил бы игрока, поэтому его режут до 2.
        assertEquals(2f, InfectionMath.временныйШтраф(3, 14f), 1e-4);

        // Итог никогда не ниже жёсткого пола.
        float постоянный = InfectionMath.постоянныйШтраф(99);
        float временный = InfectionMath.временныйШтраф(4, постоянный);
        assertTrue(InfectionMath.БАЗА_ЗДОРОВЬЯ - постоянный - временный
                   >= PlagueConstants.PLAYER_HARD_FLOOR - 1e-4);
    }
}
