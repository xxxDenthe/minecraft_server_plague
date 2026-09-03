package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Маска поражения: какие из 256 столбцов чанка гниют на данном уровне.
 * Дизайн материализации, раздел 4.1; доли — ядро, раздел 5.
 */
class MaterializationMaskTest {

    private static final long СИД = 0x5EEDL;

    @Test
    void долиСовпадаютСТаблицейСтадий() {
        assertEquals(0.00f, MaterializationMask.fractionFor(0), 0.001f);
        assertEquals(0.10f, MaterializationMask.fractionFor(1), 0.001f);
        assertEquals(0.35f, MaterializationMask.fractionFor(2), 0.001f);
        assertEquals(0.70f, MaterializationMask.fractionFor(3), 0.001f);
        assertEquals(0.95f, MaterializationMask.fractionFor(4), 0.001f);
        assertEquals(1.00f, MaterializationMask.fractionFor(5), 0.001f);
    }

    @Test
    void наЧистомУровнеНеПоражёнНиОдинСтолбец() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertFalse(MaterializationMask.isAffected(СИД, x, z, 0));
            }
        }
    }

    @Test
    void наУровнеСердцаПоражёнКаждыйСтолбец() {
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                assertTrue(MaterializationMask.isAffected(СИД, x, z, 5));
            }
        }
    }

    @Test
    void долиПоражённыхСтолбцовСходятсяСТаблицейСТочностьюТрёхПроцентов() {
        for (int уровень = 1; уровень <= 4; уровень++) {
            int всего = 0, поражено = 0;
            for (int cx = -8; cx < 8; cx++) {
                for (int cz = -8; cz < 8; cz++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            всего++;
                            if (MaterializationMask.isAffected(СИД, (cx << 4) + x, (cz << 4) + z, уровень)) {
                                поражено++;
                            }
                        }
                    }
                }
            }
            float доля = (float) поражено / всего;
            assertEquals(MaterializationMask.fractionFor(уровень), доля, 0.03f,
                "уровень " + уровень);
        }
    }

    @Test
    void маскаДетерминирована() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(
                    MaterializationMask.isAffected(СИД, x, z, 3),
                    MaterializationMask.isAffected(СИД, x, z, 3));
            }
        }
    }

    @Test
    void уровниВложены() {
        for (int x = -32; x < 32; x++) {
            for (int z = -32; z < 32; z++) {
                for (int уровень = 1; уровень < 5; уровень++) {
                    if (MaterializationMask.isAffected(СИД, x, z, уровень)) {
                        assertTrue(MaterializationMask.isAffected(СИД, x, z, уровень + 1),
                            "поражённое на " + уровень + " обязано остаться на " + (уровень + 1));
                    }
                }
            }
        }
    }

    @Test
    void разныеСидыДаютРазныйУзор() {
        int различий = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (MaterializationMask.isAffected(1L, x, z, 2)
                    != MaterializationMask.isAffected(2L, x, z, 2)) {
                    различий++;
                }
            }
        }
        assertTrue(различий > 10, "узор должен зависеть от сида, различий: " + различий);
    }

    @Test
    void весСтолбцаЛежитВНулеИЕдинице() {
        for (int x = -100; x < 100; x++) {
            for (int z = -100; z < 100; z++) {
                float w = MaterializationMask.columnWeight(СИД, x, z);
                assertTrue(w >= 0f && w < 1f, "вес вне диапазона: " + w);
            }
        }
    }

    @Test
    void соседниеСтолбцыНеОдинаковы() {
        float a = MaterializationMask.columnWeight(СИД, 0, 0);
        float b = MaterializationMask.columnWeight(СИД, 1, 0);
        float c = MaterializationMask.columnWeight(СИД, 0, 1);
        assertNotEquals(a, b, 1e-6f);
        assertNotEquals(a, c, 1e-6f);
    }

    @Test
    void весБлокаЛежитВНулеЕдинице() {
        for (int y = -64; y < 128; y += 7) {
            float вес = MaterializationMask.blockWeight(СИД, 13, y, -21);
            assertTrue(вес >= 0f && вес < 1f, "вес " + вес + " на y=" + y);
        }
    }

    @Test
    void весБлокаДетерминирован() {
        assertEquals(MaterializationMask.blockWeight(СИД, 5, 30, 7),
                     MaterializationMask.blockWeight(СИД, 5, 30, 7));
    }

    /** Высота обязана участвовать: иначе весь столбец зарастал бы разом. */
    @Test
    void соседниеПоВысотеБлокиНеОдинаковы() {
        float a = MaterializationMask.blockWeight(СИД, 5, 30, 7);
        float b = MaterializationMask.blockWeight(СИД, 5, 31, 7);
        assertNotEquals(a, b, 1e-6f);
    }

    @Test
    void весБлокаРаспределёнРовно() {
        int ниже = 0, всего = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (MaterializationMask.blockWeight(СИД, x, y, z) < 0.25f) ниже++;
                    всего++;
                }
            }
        }
        float доля = ниже / (float) всего;
        assertTrue(доля > 0.22f && доля < 0.28f, "ждём около четверти, получено " + доля);
    }

    @Test
    void отрицательныеКоординатыРаботаютТакЖе() {
        int поражено = 0;
        for (int x = -16; x < 0; x++) {
            for (int z = -16; z < 0; z++) {
                if (MaterializationMask.isAffected(СИД, x, z, 3)) поражено++;
            }
        }
        assertTrue(поражено > 100 && поражено < 220,
            "на уровне 3 ждём около 70% из 256, получено " + поражено);
    }
}
