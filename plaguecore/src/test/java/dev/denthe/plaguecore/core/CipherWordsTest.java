package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CipherWordsTest {

    private static final Set<String> КОРНИ = Set.of("телег", "бессонн", "колыбельн", "онисим");

    @Test
    void разбитьСклеиваетсяОбратно() {
        String текст = "Была телега с солью, и человек — вёз её 4 дня.";
        assertEquals(текст, String.join("", CipherWords.разбить(текст)));
    }

    @Test
    void разбитьНеДаётПустыхКусков() {
        for (String к : CipherWords.разбить("а, б!! в")) assertFalse(к.isEmpty());
    }

    @Test
    void корняНетУОбычногоСлова() {
        assertNull(CipherWords.корень("соль", КОРНИ));
    }

    @Test
    void кореньЛовитВсеПадежи() {
        for (String форма : List.of("телега", "телеги", "телегу", "Телегой", "ТЕЛЕГАМИ")) {
            assertEquals("телег", CipherWords.корень(форма, КОРНИ), форма);
        }
    }

    @Test
    void ёИЕОдноИТоЖе() {
        assertEquals("онисим", CipherWords.корень("Онисим", КОРНИ));
        assertEquals("телег", CipherWords.корень("тёлеги", КОРНИ));
    }

    @Test
    void длинныйКореньПобеждаетКороткий() {
        Set<String> оба = Set.of("сонн", "бессонн");
        assertEquals("бессонн", CipherWords.корень("бессонные", оба));
    }

    @Test
    void догадкаЛовитсяПосредиФразы() {
        assertEquals(List.of("телег"),
            CipherWords.угаданные("да это же телега, блин!", КОРНИ));
    }

    @Test
    void обычнаяБолтовняНичегоНеОткрывает() {
        assertTrue(CipherWords.угаданные("иду копать железо, ждите", КОРНИ).isEmpty());
    }

    @Test
    void двеДогадкиЗаРаз() {
        assertEquals(2, CipherWords.угаданные("бессонные пели колыбельную", КОРНИ).size());
    }

    @Test
    void повторВСообщенииНеДублируется() {
        assertEquals(List.of("телег"), CipherWords.угаданные("телега телега телегу", КОРНИ));
    }
}
