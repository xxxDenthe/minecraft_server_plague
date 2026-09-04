package dev.denthe.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchTest {

    @Test
    void перваяСменаВсегдаРазрешена() {
        assertTrue(ClassSwitch.можноСменить(-1, 0, 36000));
        assertTrue(ClassSwitch.можноСменить(-1, 999_999, 36000));
    }

    @Test
    void доИстеченияКулдаунаНельзя() {
        assertFalse(ClassSwitch.можноСменить(100, 100 + 36000 - 1, 36000));
    }

    @Test
    void поИстеченииКулдаунаМожно() {
        assertTrue(ClassSwitch.можноСменить(100, 100 + 36000, 36000));
        assertTrue(ClassSwitch.можноСменить(100, 100 + 36001, 36000));
    }

    @Test
    void осталосьТиковСчитаетсяТолькоПокаКулдаунЖив() {
        assertEquals(0, ClassSwitch.осталосьТиков(-1, 500, 36000));
        assertEquals(0, ClassSwitch.осталосьТиков(100, 100 + 36000, 36000));
        assertEquals(1, ClassSwitch.осталосьТиков(100, 100 + 35999, 36000));
        assertEquals(36000, ClassSwitch.осталосьТиков(100, 100, 36000));
    }

    /** «Через 0 минут» на живом кулдауне читалось бы как ошибка — округляем вверх. */
    @Test
    void минутыОкругляютсяВверх() {
        assertEquals(0, ClassSwitch.минутОсталось(0));
        assertEquals(1, ClassSwitch.минутОсталось(1));
        assertEquals(1, ClassSwitch.минутОсталось(1200));
        assertEquals(2, ClassSwitch.минутОсталось(1201));
        assertEquals(30, ClassSwitch.минутОсталось(36000));
    }
}
