package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FightPhasesTest {

    @Test
    void целоеСердцеПерваяФаза() {
        assertEquals(1, FightPhases.фаза(200f, 200f));
    }

    @Test
    void мёртвоеСердцеТретьяФаза() {
        assertEquals(3, FightPhases.фаза(0f, 200f));
    }

    @Test
    void границаОтдаётсяСледующейФазе() {
        // Ровно две трети — это уже вторая фаза, а не хвост первой.
        assertEquals(2, FightPhases.фаза(200f * 2f / 3f, 200f));
        assertEquals(3, FightPhases.фаза(200f / 3f, 200f));
    }

    @Test
    void делениеЕдетЗаМаксимумом() {
        // Здоровье правится в конфиге; фазы должны считаться от доли.
        assertEquals(1, FightPhases.фаза(600f, 600f));
        assertEquals(2, FightPhases.фаза(300f, 600f));
        assertEquals(3, FightPhases.фаза(60f, 600f));
    }

    @Test
    void мусорНеРоняет() {
        assertEquals(1, FightPhases.фаза(999f, 200f));
        assertEquals(3, FightPhases.фаза(-5f, 200f));
        assertEquals(1, FightPhases.фаза(10f, 0f));
    }

    @Test
    void фазаТолькоРастётПоМереУрона() {
        int прежняя = 1;
        for (int hp = 200; hp >= 0; hp--) {
            int текущая = FightPhases.фаза(hp, 200f);
            assertTrue(текущая >= прежняя, "при " + hp + " HP фаза откатилась");
            прежняя = текущая;
        }
        assertEquals(FightPhases.ФАЗ, прежняя);
    }
}
