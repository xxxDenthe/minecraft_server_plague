package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поведение Наблюдателя построено на взгляде. Спек хоррора, раздел 3.
 */
class WatcherMathTest {

    private static final double РАСТВОРЕНИЕ = 10.0;

    @Test
    void подВзглядомСтоит() {
        assertEquals(WatcherMath.Решение.СТОЯТЬ,
            WatcherMath.решение(true, 30.0, false, РАСТВОРЕНИЕ));
    }

    @Test
    void безВзглядаПодходит() {
        assertEquals(WatcherMath.Решение.ПОДОЙТИ,
            WatcherMath.решение(false, 30.0, false, РАСТВОРЕНИЕ));
    }

    @Test
    void вблизиРастворяется() {
        assertEquals(WatcherMath.Решение.РАСТВОРИТЬСЯ,
            WatcherMath.решение(true, 8.0, false, РАСТВОРЕНИЕ));
        assertEquals(WatcherMath.Решение.РАСТВОРИТЬСЯ,
            WatcherMath.решение(false, 8.0, false, РАСТВОРЕНИЕ));
    }

    @Test
    void настоящийВблизиНападает() {
        assertEquals(WatcherMath.Решение.НАПАСТЬ,
            WatcherMath.решение(true, 8.0, true, РАСТВОРЕНИЕ));
    }

    @Test
    void настоящийИздалиВедётСебяТакЖе() {
        // Пока он далеко, настоящий неотличим от двух предыдущих —
        // в этом весь фокус: игроки уже решили, что он безвреден.
        assertEquals(WatcherMath.Решение.СТОЯТЬ,
            WatcherMath.решение(true, 25.0, true, РАСТВОРЕНИЕ));
    }

    @Test
    void настоящимСтановитсяПоследнийЗаСессию() {
        assertFalse(WatcherMath.настоящий(0, 3));
        assertFalse(WatcherMath.настоящий(1, 3));
        assertTrue(WatcherMath.настоящий(2, 3),
            "третье явление из трёх обязано быть настоящим");
    }

    @Test
    void приЛимитеВОдноЯвлениеОноИНастоящее() {
        assertTrue(WatcherMath.настоящий(0, 1));
    }

    @Test
    void взглядСчитаетсяПоНаправлению() {
        // Смотрим ровно на него — видим.
        assertTrue(WatcherMath.смотрит(0.0, 0.0, 1.0, 0.0, 0.0, 20.0, 0.5));
        // Отвернулись на девяносто градусов — нет.
        assertFalse(WatcherMath.смотрит(1.0, 0.0, 0.0, 0.0, 0.0, 20.0, 0.5));
        // Спиной — тем более нет.
        assertFalse(WatcherMath.смотрит(0.0, 0.0, -1.0, 0.0, 0.0, 20.0, 0.5));
    }
}
