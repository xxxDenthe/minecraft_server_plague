package dev.denthe.plaguecore.mc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Очередь чанков на перерисовку. Дизайн материализации, раздел 4.3.
 * Кода Minecraft внутри нет, поэтому проверяется обычным JUnit.
 */
class MaterializationQueueTest {

    @Test
    void новаяОчередьПуста() {
        MaterializationQueue q = new MaterializationQueue(512);
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
        assertEquals(-1, q.head());
    }

    @Test
    void поставленныйЧанкСтановитсяГоловой() {
        MaterializationQueue q = new MaterializationQueue(512);
        assertTrue(q.enqueue(42));
        assertEquals(1, q.size());
        assertEquals(42, q.head());
    }

    @Test
    void повторнаяПостановкаНичегоНеДелает() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(42);
        assertFalse(q.enqueue(42), "дедуп по чанку");
        assertEquals(1, q.size());
    }

    @Test
    void очередьНеРастётВышеПотолка() {
        MaterializationQueue q = new MaterializationQueue(8);
        for (int i = 0; i < 100; i++) q.enqueue(i);
        assertEquals(8, q.size());
    }

    @Test
    void приПереполненииНовыеЧанкиОтвергаются() {
        MaterializationQueue q = new MaterializationQueue(2);
        q.enqueue(1);
        q.enqueue(2);
        assertFalse(q.enqueue(3), "лишний приедет при следующей загрузке чанка");
        assertFalse(q.contains(3));
    }

    @Test
    void чанкиОбрабатываютсяВПорядкеПостановки() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(7);
        q.enqueue(8);
        assertEquals(7, q.head());
        q.finishHead();
        assertEquals(8, q.head());
        q.finishHead();
        assertTrue(q.isEmpty());
    }

    @Test
    void курсорНовогоЧанкаНулевой() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(5);
        assertEquals(0, q.cursor());
    }

    @Test
    void курсорПереживаетТикИСбрасываетсяНаСледующемЧанке() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(5);
        q.enqueue(6);
        q.setCursor(137);
        assertEquals(137, q.cursor());
        q.finishHead();
        assertEquals(6, q.head());
        assertEquals(0, q.cursor(), "новый чанк начинается с начала");
    }

    @Test
    void доделанныйЧанкУходитИзОчередиИМожетБытьПоставленСнова() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(5);
        q.finishHead();
        assertFalse(q.contains(5));
        assertTrue(q.enqueue(5), "после перерисовки чанк снова можно ставить");
    }

    @Test
    void очисткаОпустошаетОчередьИКурсор() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.enqueue(1);
        q.setCursor(50);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.cursor());
        assertFalse(q.contains(1));
    }

    @Test
    void головаПустойОчередиЭтоМинусЕдиница() {
        MaterializationQueue q = new MaterializationQueue(512);
        q.finishHead();
        assertEquals(-1, q.head());
    }

    @Test
    void местоОсвобождаетсяПослеОбработки() {
        MaterializationQueue q = new MaterializationQueue(2);
        q.enqueue(1);
        q.enqueue(2);
        assertFalse(q.enqueue(3));
        q.finishHead();
        assertTrue(q.enqueue(3), "место освободилось");
        assertEquals(2, q.size());
    }
}
