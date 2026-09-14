package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Выбор субъективного текста. Спек интерфейса «Состояние здоровья»,
 * раздел 6.
 *
 * Тест стережёт главное правило: наружу уходят только ключи
 * локализации, и ни в одном из них нет ни стадии, ни заражения.
 */
class WellbeingTest {

    @Test
    void ступеньПовторяетСтадиюИНеВыходитЗаКрая() {
        assertEquals(0, Wellbeing.ступень(0));
        assertEquals(3, Wellbeing.ступень(3));
        assertEquals(4, Wellbeing.ступень(4));
        assertEquals(0, Wellbeing.ступень(-7), "мусор снизу прижимается к нулю");
        assertEquals(4, Wellbeing.ступень(99), "мусор сверху прижимается к краю");
    }

    @Test
    void укаждойСтупениСвойКлюч() {
        for (int a = 0; a < Wellbeing.СТУПЕНЕЙ; a++) {
            for (int b = a + 1; b < Wellbeing.СТУПЕНЕЙ; b++) {
                assertNotEquals(Wellbeing.общее(a), Wellbeing.общее(b));
                assertNotEquals(Wellbeing.общееКратко(a), Wellbeing.общееКратко(b));
            }
        }
    }

    @Test
    void укаждойЧастиСвойКлюч() {
        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            for (Wellbeing.Часть другая : Wellbeing.Часть.values()) {
                if (часть == другая) continue;
                assertNotEquals(Wellbeing.часть(часть, 2), Wellbeing.часть(другая, 2));
            }
        }
    }

    @Test
    void чужойОсмотрГоворитДругимиКлючами() {
        assertNotEquals(Wellbeing.общее(2), Wellbeing.чужоеОбщее(2));
        assertNotEquals(
            Wellbeing.часть(Wellbeing.Часть.ARMS, 2),
            Wellbeing.чужаяЧасть(Wellbeing.Часть.ARMS, 2));
    }

    @Test
    void вКлючахНетСловМеханики() {
        for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
            проверить(Wellbeing.общее(с));
            проверить(Wellbeing.общееКратко(с));
            проверить(Wellbeing.чужоеОбщее(с));
            for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
                проверить(Wellbeing.часть(часть, с));
                проверить(Wellbeing.чужаяЧасть(часть, с));
            }
        }
    }

    private static void проверить(String ключ) {
        assertFalse(ключ.contains("stage"), "в ключе не должно быть стадии: " + ключ);
        assertFalse(ключ.contains("infection"), "в ключе не должно быть заражения: " + ключ);
        assertTrue(ключ.startsWith("plaguecore.health."), "чужой корень ключа: " + ключ);
    }
}
