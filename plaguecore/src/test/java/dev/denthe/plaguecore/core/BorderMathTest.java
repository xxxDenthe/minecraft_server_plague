package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BorderMathTest {

    /** Сетка 9×9 с началом в (-4,-4): центр приходится ровно на (0,0). */
    private static PlagueGrid сетка() {
        return new PlagueGrid(9, -4, -4);
    }

    @Test
    void очагаНетВообще() {
        assertEquals(-1, BorderMath.ближайшийОчаг(сетка(), 0, 0, 4, 4));
    }

    @Test
    void находитБлижайшийИзДвух() {
        PlagueGrid g = сетка();
        g.setLevel(3, 0, 4);
        g.setLevel(0, 2, 4);

        int найден = BorderMath.ближайшийОчаг(g, 0, 0, 4, 4);
        assertEquals(g.index(0, 2), найден, "ближе тот, что в двух чанках, а не в трёх");
    }

    @Test
    void слабыйОчагНеСчитается() {
        PlagueGrid g = сетка();
        g.setLevel(1, 0, 3);

        assertEquals(-1, BorderMath.ближайшийОчаг(g, 0, 0, 4, 4),
            "уровень 3 — ещё Пограничье, волне взяться неоткуда");
    }

    @Test
    void радиусКруглый() {
        PlagueGrid g = сетка();
        g.setLevel(3, 3, 4);

        assertEquals(-1, BorderMath.ближайшийОчаг(g, 0, 0, 4, 4),
            "угол квадрата 4×4 лежит в 4.24 чанка — дальше радиуса");
        assertEquals(g.index(3, 3), BorderMath.ближайшийОчаг(g, 0, 0, 5, 4));
    }

    @Test
    void заКраемСеткиНеИщется() {
        PlagueGrid g = сетка();
        assertEquals(-1, BorderMath.ближайшийОчаг(g, 100, 100, 4, 4),
            "за краем мира ячеек нет, и падать на этом нельзя");
    }

    @Test
    void уровеньЗаКраемМираДаётНоль() {
        float[] шансы = { 0f, 0.04f, 0.08f, 0.15f, 0.15f };
        assertEquals(0f, BorderMath.поУровню(шансы, -1), 1e-6f);
        assertEquals(0f, BorderMath.поУровню(шансы, 0), 1e-6f);
        assertEquals(0.15f, BorderMath.поУровню(шансы, 4), 1e-6f);
        assertEquals(0.15f, BorderMath.поУровню(шансы, 99), 1e-6f,
            "уровень 5 у Сердца не должен вылетать за таблицу");
    }

    @Test
    void целаяТаблицаЗажимаетсяТакЖе() {
        int[] зомби = { 2, 3, 4, 5, 6 };
        assertEquals(2, BorderMath.поУровню(зомби, -3));
        assertEquals(6, BorderMath.поУровню(зомби, 7));
    }
}
