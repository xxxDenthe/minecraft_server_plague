package dev.denthe.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Договор об упаковке сетки снимка. Проверяется ровно то, что молча
 * разъезжается между отправителем и экраном: знак байта и порядок
 * клеток.
 */
class SnapshotGridTest {

    @Test
    void сторонаНечётнаяИСЦентром() {
        assertEquals(9, SnapshotGrid.сторона(4, 21));
        assertEquals(3, SnapshotGrid.сторона(1, 21));
        assertEquals(1, SnapshotGrid.сторона(0, 21), "нулевой радиус — всё равно один чанк");
    }

    @Test
    void сторонаНеПревышаетПотолок() {
        assertEquals(21, SnapshotGrid.сторона(50, 21));
    }

    @Test
    void уровниПереживаютУпаковку() {
        for (int уровень = 0; уровень <= 5; уровень++) {
            assertEquals(уровень, SnapshotGrid.распаковать(SnapshotGrid.упаковать(уровень)));
        }
    }

    /** Байт в Java знаковый: 0xFF без маски читается как −1 и ломает цвет клетки. */
    @Test
    void чанкВнеМираНеПутаетсяСУровнем() {
        int распакованный = SnapshotGrid.распаковать(SnapshotGrid.упаковать(-1));
        assertEquals(SnapshotGrid.НЕТ_ДАННЫХ, распакованный);
        assertNotEquals(-1, распакованный);
        for (int уровень = 0; уровень <= 5; уровень++) {
            assertNotEquals(уровень, распакованный);
        }
    }

    @Test
    void сеткаПострочная() {
        assertEquals(0, SnapshotGrid.индекс(0, 0, 9));
        assertEquals(8, SnapshotGrid.индекс(8, 0, 9));
        assertEquals(9, SnapshotGrid.индекс(0, 1, 9), "вторая строка начинается сразу за первой");
        assertEquals(80, SnapshotGrid.индекс(8, 8, 9));
    }
}
