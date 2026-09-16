package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Пометки Мастера игры. Спек «Пометки Мастера игры в экране здоровья»,
 * разделы 6 и 7.
 *
 * Тест стережёт то же правило, что у {@link Wellbeing}: наружу уходят
 * ключи локализации, а своя строка ГМ — как есть, но очищенная.
 */
class MarksTest {

    private static Marks.Пометка заготовка(int id, Marks.Место место, boolean заменяет,
                                           Marks.Заготовка з) {
        return new Marks.Пометка(id, место, заменяет, з.идентификатор(), "");
    }

    private static Marks.Пометка своя(int id, Marks.Место место, boolean заменяет, String текст) {
        return new Marks.Пометка(id, место, заменяет, "", текст);
    }

    @Test
    void безПометокОстаётсяТолькоАвтотекст() {
        List<Marks.Вывод> вывод = Marks.строки(
            Marks.Место.ARMS, "авто.руки", List.of(), false, false);
        assertEquals(1, вывод.size());
        assertEquals("авто.руки", вывод.get(0).ключ());
    }

    @Test
    void добавкаИдётПослеАвтотекста() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.ARMS, false, Marks.Заготовка.FRACTURE)), false, false);
        assertEquals(2, вывод.size());
        assertEquals("авто.руки", вывод.get(0).ключ());
        assertEquals(Marks.ключ(Marks.Заготовка.FRACTURE, false), вывод.get(1).ключ());
    }

    @Test
    void заменаСъедаетАвтотекст() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.ARMS, true, Marks.Заготовка.BURN)), false, false);
        assertEquals(1, вывод.size());
        assertEquals(Marks.ключ(Marks.Заготовка.BURN, false), вывод.get(0).ключ());
    }

    @Test
    void чужойВзглядБерётДругойКлюч() {
        List<Marks.Вывод> своё = Marks.строки(Marks.Место.LEGS, null,
            List.of(заготовка(1, Marks.Место.LEGS, false, Marks.Заготовка.WOUND)), false, false);
        List<Marks.Вывод> чужое = Marks.строки(Marks.Место.LEGS, null,
            List.of(заготовка(1, Marks.Место.LEGS, false, Marks.Заготовка.WOUND)), true, false);
        assertNotEquals(своё.get(0).ключ(), чужое.get(0).ключ());
    }

    @Test
    void клирикВидитВторуюСтрокуТусклой() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.HEAD, null,
            List.of(заготовка(1, Marks.Место.HEAD, false, Marks.Заготовка.CONCUSSION)), true, true);
        assertEquals(2, вывод.size());
        assertFalse(вывод.get(0).тусклый());
        assertTrue(вывод.get(1).тусклый());
        assertEquals(Marks.ключКлирика(Marks.Заготовка.CONCUSSION), вывод.get(1).ключ());
    }

    @Test
    void свояСтрокаОтдаётсяТекстомИОдинаковаВсем() {
        var п = своя(1, Marks.Место.OVERALL, false, "Рука в лубке.");
        List<Marks.Вывод> своё = Marks.строки(Marks.Место.OVERALL, null, List.of(п), false, false);
        List<Marks.Вывод> чужое = Marks.строки(Marks.Место.OVERALL, null, List.of(п), true, true);
        assertNull(своё.get(0).ключ());
        assertEquals("Рука в лубке.", своё.get(0).текст());
        assertEquals(своё, чужое, "у своей строки нет ни чужого варианта, ни строки Клирика");
    }

    @Test
    void строкаПомнитНомерПометкиААвтотекстНет() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(7, Marks.Место.ARMS, false, Marks.Заготовка.FRACTURE)), false, true);
        assertEquals(-1, вывод.get(0).id(), "у автотекста снимать нечего");
        assertEquals(7, вывод.get(1).id());
        assertEquals(-1, вывод.get(2).id(), "строка Клирика уйдёт вместе со своей пометкой");
    }

    @Test
    void чужиеМестаНеПопадаютВВывод() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.LEGS, true, Marks.Заготовка.FRACTURE)), false, false);
        assertEquals(1, вывод.size(), "пометка ног не трогает руки — ни заменой, ни добавкой");
        assertEquals("авто.руки", вывод.get(0).ключ());
    }

    @Test
    void порядокДобавленияСохраняется() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.TORSO, null, List.of(
            заготовка(1, Marks.Место.TORSO, false, Marks.Заготовка.WOUND),
            заготовка(2, Marks.Место.TORSO, false, Marks.Заготовка.BLEEDING)), false, false);
        assertEquals(Marks.ключ(Marks.Заготовка.WOUND, false), вывод.get(0).ключ());
        assertEquals(Marks.ключ(Marks.Заготовка.BLEEDING, false), вывод.get(1).ключ());
    }

    @Test
    void укаждойЗаготовкиСвоиКлючи() {
        for (Marks.Заготовка а : Marks.Заготовка.values()) {
            for (Marks.Заготовка б : Marks.Заготовка.values()) {
                if (а == б) continue;
                assertNotEquals(Marks.ключ(а, false), Marks.ключ(б, false));
                assertNotEquals(Marks.имя(а), Marks.имя(б));
            }
            assertNotEquals(Marks.ключ(а, false), Marks.ключ(а, true));
            assertNotEquals(Marks.ключ(а, false), Marks.ключКлирика(а));
        }
    }

    @Test
    void неизвестноеИмяМолчит() {
        assertNull(Marks.заготовка("somemod:quantum_flux"));
        assertNull(Marks.заготовка(null));
        assertNull(Marks.место("ухо"));
    }

    @Test
    void разборИмёнНеЧувствителенКРегистру() {
        assertEquals(Marks.Заготовка.FEVER, Marks.заготовка("FeVeR"));
        assertEquals(Marks.Место.HEAD, Marks.место("Head"));
    }

    @Test
    void чисткаСрезаетФорматированиеИДлину() {
        assertEquals("Рука в лубке.", Marks.чистить("  Рука в лубке.  "));
        assertEquals("красное", Marks.чистить("§cкрасное"));
        assertEquals("две строки", Marks.чистить("две\nстроки"));
        assertEquals(Marks.ДЛИНА_СТРОКИ, Marks.чистить("я".repeat(500)).length());
        assertEquals("", Marks.чистить(null));
    }
}
