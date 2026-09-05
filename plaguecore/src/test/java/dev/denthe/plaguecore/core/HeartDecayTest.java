package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartDecayTest {

    @Test
    void целоеСердцеБезДырок() {
        assertEquals(0, HeartDecay.маска(200f, 200f));
    }

    @Test
    void мёртвоеСердцеОсыпалосьЦеликом() {
        assertEquals(HeartDecay.ВСЕ_КУСКИ, HeartDecay.маска(0f, 200f));
        assertEquals(HeartDecay.КУСКОВ, Integer.bitCount(HeartDecay.маска(0f, 200f)));
    }

    @Test
    void половинаЗдоровьяПоловинаКусков() {
        assertEquals(HeartDecay.КУСКОВ / 2, Integer.bitCount(HeartDecay.маска(100f, 200f)));
    }

    @Test
    void кускиОтваливаютсяПодряд() {
        // Ровно пять кусков — это младшие пять битов, а не пять случайных.
        assertEquals(0b11111, HeartDecay.маска(150f, 200f));
    }

    @Test
    void маскаТолькоРастётПоМереУрона() {
        int прежняя = 0;
        for (int hp = 200; hp >= 0; hp--) {
            int текущая = HeartDecay.маска(hp, 200f);
            assertEquals(прежняя, прежняя & текущая,
                "при " + hp + " HP кусок вернулся на место");
            прежняя = текущая;
        }
    }

    @Test
    void числаЗаГраницамиНеЛомаютМаску() {
        assertEquals(0, HeartDecay.маска(999f, 200f));
        assertEquals(HeartDecay.ВСЕ_КУСКИ, HeartDecay.маска(-50f, 200f));
        // Нулевой максимум приходит от неправильного атрибута, а не от игры.
        assertEquals(0, HeartDecay.маска(0f, 0f));
    }

    @Test
    void скоростьБиенияПадаетСоЗдоровьем() {
        assertEquals(1.0f, HeartDecay.скоростьБиения(200f, 200f), 1e-4);
        assertEquals(0.35f, HeartDecay.скоростьБиения(0f, 200f), 1e-4);
        assertTrue(HeartDecay.скоростьБиения(100f, 200f) < HeartDecay.скоростьБиения(200f, 200f));
    }

    @Test
    void имёнКостейРовноСтолькоЖеСколькоКусков() {
        assertEquals(HeartDecay.КУСКОВ, HeartDecay.КОСТИ.length);
    }
}
