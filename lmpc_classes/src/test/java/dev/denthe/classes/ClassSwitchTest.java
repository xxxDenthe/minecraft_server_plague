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
}
