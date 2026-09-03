package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTableTest {

    @Test
    void границыФазСоответствуютСпеку() {
        assertEquals(0, PhaseTable.phaseForNight(1));
        assertEquals(0, PhaseTable.phaseForNight(5));
        assertEquals(1, PhaseTable.phaseForNight(6));
        assertEquals(1, PhaseTable.phaseForNight(12));
        assertEquals(2, PhaseTable.phaseForNight(13));
        assertEquals(2, PhaseTable.phaseForNight(20));
        assertEquals(3, PhaseTable.phaseForNight(21));
        assertEquals(3, PhaseTable.phaseForNight(30));
        assertEquals(4, PhaseTable.phaseForNight(31));
        assertEquals(4, PhaseTable.phaseForNight(999));
    }

    @Test
    void ночьНольИОтрицательныеСчитаютсяПервойФазой() {
        assertEquals(0, PhaseTable.phaseForNight(0));
        assertEquals(0, PhaseTable.phaseForNight(-3));
    }

    @Test
    void бюджетыСоответствуютСпеку() {
        assertEquals(25, PhaseTable.paramsFor(0).budget());
        assertEquals(50, PhaseTable.paramsFor(1).budget());
        assertEquals(95, PhaseTable.paramsFor(2).budget());
        assertEquals(150, PhaseTable.paramsFor(3).budget());
        assertEquals(240, PhaseTable.paramsFor(4).budget());
    }

    @Test
    void базоваяВероятностьСоответствуетСпеку() {
        assertEquals(0.04f, PhaseTable.paramsFor(0).base(), 1e-6);
        assertEquals(0.07f, PhaseTable.paramsFor(1).base(), 1e-6);
        assertEquals(0.11f, PhaseTable.paramsFor(2).base(), 1e-6);
        assertEquals(0.16f, PhaseTable.paramsFor(3).base(), 1e-6);
        assertEquals(0.24f, PhaseTable.paramsFor(4).base(), 1e-6);
    }

    @Test
    void ритмРостаНаМестеСоответствуетСпеку() {
        assertEquals(3, PhaseTable.paramsFor(0).growthEveryNights());
        assertEquals(2, PhaseTable.paramsFor(1).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(2).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(3).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(4).growthEveryNights());

        assertEquals(1, PhaseTable.paramsFor(0).growthAmount());
        assertEquals(2, PhaseTable.paramsFor(4).growthAmount());
    }

    @Test
    void бюджетыРастутМонотонно() {
        for (int p = 1; p < PhaseTable.PHASE_COUNT; p++) {
            assertTrue(PhaseTable.paramsFor(p).budget() > PhaseTable.paramsFor(p - 1).budget(),
                "бюджет фазы " + p + " должен быть больше предыдущей");
            assertTrue(PhaseTable.paramsFor(p).base() > PhaseTable.paramsFor(p - 1).base(),
                "base фазы " + p + " должен быть больше предыдущей");
        }
    }

    @Test
    void несуществующаяФазаОбрезаетсяКГраницам() {
        assertEquals(PhaseTable.paramsFor(0), PhaseTable.paramsFor(-1));
        assertEquals(PhaseTable.paramsFor(4), PhaseTable.paramsFor(17));
    }

    /**
     * Внимание: это проверка арифметики таблицы, а не поведения чумы.
     *
     * Бюджет ночи — потолок, а не план, и реальная симуляция выбирает его
     * далеко не полностью: заражение растёт по краю очага, а край растёт
     * медленнее площади. Сумма бюджетов и доля заражённого мира — разные
     * величины, и когда-то их здесь путали, из-за чего расхождение
     * в два с половиной раза пришлось ловить уже на живом сервере.
     *
     * Настоящую кривую стережёт SpreadCurveTest.
     */
    @Test
    void суммарныйБюджетЗаТридцатьНочейСоответствуетСпеку() {
        int сумма = 0;
        for (int night = 1; night <= 30; night++) {
            сумма += PhaseTable.paramsFor(PhaseTable.phaseForNight(night)).budget();
        }
        assertEquals(2735, сумма, "суммарный бюджет за 30 ночей по спеку 6.2");
    }

    /**
     * Фазы приходят из конфига: ими правится кривая распространения,
     * и это главная ручка всей игры. Ночи обязаны идти по возрастанию,
     * иначе фаза схлопнется и следующая не наступит никогда.
     */
    @Test
    void фазыИзКонфигаВыправляютсяПоВозрастанию() {
        int конец0 = PhaseTable.endNightOf(0);
        int конец1 = PhaseTable.endNightOf(1);
        PhaseParams было0 = PhaseTable.paramsFor(0);
        PhaseParams было1 = PhaseTable.paramsFor(1);
        try {
            assertFalse(PhaseTable.задатьФазу(0, 9, new PhaseParams(0.5f, 7, 2, 1)));
            assertEquals(9, PhaseTable.endNightOf(0));
            assertEquals(7, PhaseTable.paramsFor(0).budget());
            assertEquals(0, PhaseTable.phaseForNight(9));

            assertTrue(PhaseTable.задатьФазу(1, 3, было1), "ночь 3 раньше конца нулевой фазы");
            assertEquals(10, PhaseTable.endNightOf(1), "подтянуто на ночь после нулевой");
        } finally {
            PhaseTable.задатьФазу(0, конец0, было0);
            PhaseTable.задатьФазу(1, конец1, было1);
        }
    }

    /** У последней фазы конца нет: она бессрочная, и конфиг его не задаёт. */
    @Test
    void последняяФазаОстаётсяБессрочной() {
        PhaseParams было = PhaseTable.paramsFor(PhaseTable.PHASE_COUNT - 1);
        try {
            PhaseTable.задатьФазу(PhaseTable.PHASE_COUNT - 1, 5, было);
            assertEquals(PhaseTable.PHASE_COUNT - 1, PhaseTable.phaseForNight(100000));
        } finally {
            PhaseTable.задатьФазу(PhaseTable.PHASE_COUNT - 1, Integer.MAX_VALUE, было);
        }
    }
}
