package dev.denthe.plaguecore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Список ручек голоса. Ломается тут молча: имя вида «semitones2» режется
 * на «semitones» + номер уровня строковой арифметикой, и опечатка в switch
 * не уронит ничего — ручка просто перестанет что-либо менять.
 */
class VoiceKnobsTest {

    @Test
    @DisplayName("каждая ручка читается и пишется")
    void каждаяРучкаЖивая() {
        for (VoiceKnobs.Ручка р : VoiceKnobs.ВСЕ) {
            double было = VoiceKnobs.прочитать(р.id());
            double проба = (р.минимум() + р.максимум()) / 2;
            if (р.целое()) проба = Math.round(проба);

            VoiceKnobs.записать(р.id(), проба);
            assertEquals(проба, VoiceKnobs.прочитать(р.id()), 1e-4,
                "ручка " + р.id() + " не дошла до констант");

            VoiceKnobs.записать(р.id(), было);
        }
    }

    @Test
    @DisplayName("значение за границей зажимается, а не пролезает")
    void границыДержат() {
        for (VoiceKnobs.Ручка р : VoiceKnobs.ВСЕ) {
            double было = VoiceKnobs.прочитать(р.id());

            VoiceKnobs.записать(р.id(), р.максимум() + 100);
            assertTrue(VoiceKnobs.прочитать(р.id()) <= р.максимум() + 1e-4,
                "ручка " + р.id() + " пустила значение выше потолка");

            VoiceKnobs.записать(р.id(), р.минимум() - 100);
            assertTrue(VoiceKnobs.прочитать(р.id()) >= р.минимум() - 1e-4,
                "ручка " + р.id() + " пустила значение ниже пола");

            VoiceKnobs.записать(р.id(), было);
        }
    }

    @Test
    @DisplayName("снимок идёт в том же порядке, что и список")
    void снимокСовпадает() {
        float[] с = VoiceKnobs.снимок();
        assertEquals(VoiceKnobs.ВСЕ.length, с.length);
        for (int i = 0; i < с.length; i++) {
            assertEquals(VoiceKnobs.прочитать(VoiceKnobs.ВСЕ[i].id()), с[i], 1e-4);
        }
    }

    @Test
    @DisplayName("у каждого уровня силы есть полный набор ручек")
    void уровниПолные() {
        for (int у = 1; у <= PlagueConstants.VOICE_LEVELS; у++) {
            for (String имя : new String[] { "semitones", "muffle", "rasp", "breath", "tremor" }) {
                assertNotNull(VoiceKnobs.найти(имя + у), "нет ручки " + имя + у);
            }
        }
    }
}
