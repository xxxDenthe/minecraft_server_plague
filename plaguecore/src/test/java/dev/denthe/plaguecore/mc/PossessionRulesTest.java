package dev.denthe.plaguecore.mc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Правила одержимости. Заметка 2026-09-06-oderzhimost.
 *
 * Проверяется только чистая часть: упаковка нажатий, выбор цели,
 * курс на неё и условие позвать админа. Всё это ломается молча —
 * тело пойдёт не туда или предложение не придёт, а лога не будет.
 */
class PossessionRulesTest {

    // ── нажатия ────────────────────────────────────────────────────────

    @Test
    void флагиХодятТудаИОбратно() {
        int ф = PossessionRules.флаги(true, false, false, true, true, false, true);

        assertTrue(PossessionRules.есть(ф, PossessionRules.ВПЕРЁД));
        assertFalse(PossessionRules.есть(ф, PossessionRules.НАЗАД));
        assertFalse(PossessionRules.есть(ф, PossessionRules.ВЛЕВО));
        assertTrue(PossessionRules.есть(ф, PossessionRules.ВПРАВО));
        assertTrue(PossessionRules.есть(ф, PossessionRules.ПРЫЖОК));
        assertFalse(PossessionRules.есть(ф, PossessionRules.КРАДУЧИСЬ));
        assertTrue(PossessionRules.есть(ф, PossessionRules.УДАР));
    }

    @Test
    void ничегоНеНажатоЭтоНоль() {
        assertEquals(0, PossessionRules.флаги(false, false, false, false, false, false, false));
    }

    @Test
    void всёНажатоВлезаетВодинБайт() {
        int ф = PossessionRules.флаги(true, true, true, true, true, true, true);
        assertEquals(ф, ф & 0xFF, "флаги обязаны влезать в байт: по сети идёт байт");
    }

    // ── выбор цели ─────────────────────────────────────────────────────

    @Test
    void ближайшийБерётСамогоБлизкого() {
        double[] x = { 30.0, 3.0, -50.0 };
        double[] z = { 0.0, 4.0, 0.0 };

        assertEquals(1, PossessionRules.ближайший(0, 0, x, z, 100.0));
    }

    @Test
    void ближайшийНеВидитДальшеРадиуса() {
        double[] x = { 30.0 };
        double[] z = { 0.0 };

        assertEquals(-1, PossessionRules.ближайший(0, 0, x, z, 24.0));
    }

    @Test
    void ближайшийНаПустомСпискеМолчит() {
        assertEquals(-1, PossessionRules.ближайший(0, 0, new double[0], new double[0], 24.0));
    }

    // ── курс ───────────────────────────────────────────────────────────

    @Test
    void курсНаЮгКогдаЦельПоZ() {
        // В Minecraft рыскание 0 — взгляд на юг, то есть в сторону +Z.
        assertEquals(0f, PossessionRules.курс(0, 10), 0.01f);
    }

    @Test
    void курсНаВостокКогдаЦельПоX() {
        // Восток — это +X, и рыскание там -90.
        assertEquals(-90f, PossessionRules.курс(10, 0), 0.01f);
    }

    // ── удар ───────────────────────────────────────────────────────────

    @Test
    void вплотнуюЗначитБить() {
        assertTrue(PossessionRules.дотянуться(1.5, 0.0, 3.0));
    }

    @Test
    void издалиНеБьём() {
        assertFalse(PossessionRules.дотянуться(9.0, 0.0, 3.0));
    }

    // ── звать ли админа ────────────────────────────────────────────────

    @Test
    void зовёмКогдаСтадияДозрелаИСоседРядом() {
        assertTrue(PossessionRules.предлагать(4, 4, true, 10_000L, 0L, 6_000));
    }

    @Test
    void неЗовёмПокаСтадияНеДозрела() {
        assertFalse(PossessionRules.предлагать(3, 4, true, 10_000L, 0L, 6_000));
    }

    @Test
    void неЗовёмКогдаБитьНекого() {
        assertFalse(PossessionRules.предлагать(4, 4, false, 10_000L, 0L, 6_000));
    }

    @Test
    void неЗовёмПокаИдётКулдаун() {
        // Прошлый зов был на тике 8000, кулдаун 6000 — до 14000 молчим.
        assertFalse(PossessionRules.предлагать(4, 4, true, 10_000L, 8_000L, 6_000));
        assertTrue(PossessionRules.предлагать(4, 4, true, 14_000L, 8_000L, 6_000));
    }

    @Test
    void первыйЗовНеЖдётКулдауна() {
        assertTrue(PossessionRules.предлагать(4, 4, true, 0L, Long.MIN_VALUE, 6_000));
    }
}
