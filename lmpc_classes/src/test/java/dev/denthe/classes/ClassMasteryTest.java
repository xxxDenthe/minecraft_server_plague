package dev.denthe.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassMasteryTest {

    @Test
    void тирыПоПорогам() {
        assertEquals(1, ClassMastery.тир(0, 25, 60));
        assertEquals(1, ClassMastery.тир(24, 25, 60));
        assertEquals(2, ClassMastery.тир(25, 25, 60));
        assertEquals(2, ClassMastery.тир(59, 25, 60));
        assertEquals(3, ClassMastery.тир(60, 25, 60));
        assertEquals(3, ClassMastery.тир(100, 25, 60));
    }

    /** Пороги приходят из конфига: переставленные местами не должны давать третий тир на нуле. */
    @Test
    void перепутанныеПорогиНеЛомаютТир() {
        assertEquals(1, ClassMastery.тир(0, 60, 25));
        assertEquals(2, ClassMastery.тир(30, 60, 25));
        assertEquals(3, ClassMastery.тир(60, 60, 25));
    }

    @Test
    void следующийПорогИПотолок() {
        assertEquals(25, ClassMastery.следующийПорог(0, 25, 60));
        assertEquals(60, ClassMastery.следующийПорог(25, 25, 60));
        assertEquals(-1, ClassMastery.следующийПорог(60, 25, 60));
    }

    @Test
    void прибавитьЗажимаетДиапазон() {
        assertEquals(0, ClassMastery.прибавить(0, -5));
        assertEquals(7, ClassMastery.прибавить(4, 3));
        assertEquals(ClassMastery.МАКСИМУМ, ClassMastery.прибавить(99, 50));
    }

    @Test
    void множителиРастутИПадаютПоТиру() {
        assertEquals(1.0f, ClassMastery.множительСилы(1, 0.15), 1e-6);
        assertEquals(1.15f, ClassMastery.множительСилы(2, 0.15), 1e-6);
        assertEquals(1.30f, ClassMastery.множительСилы(3, 0.15), 1e-6);

        assertEquals(1.0f, ClassMastery.множительКулдауна(1, 0.2), 1e-6);
        assertEquals(0.8f, ClassMastery.множительКулдауна(2, 0.2), 1e-6);
        assertEquals(0.6f, ClassMastery.множительКулдауна(3, 0.2), 1e-6);
    }

    /** Кулдаун не должен исчезать целиком, каким бы щедрым ни был конфиг. */
    @Test
    void кулдаунНеПадаетНиже10Процентов() {
        assertEquals(0.1f, ClassMastery.множительКулдауна(3, 0.9), 1e-6);
    }
}
