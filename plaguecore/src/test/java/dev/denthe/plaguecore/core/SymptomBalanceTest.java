package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Обещание симптомов: без подготовки в гнили днём плохо, с подготовкой —
 * терпимо. Заметка `2026-09-06-simptomy-dnyom.md`.
 *
 * Тест сторожит не код, а числа. Экспозиция, защита брони, сила повязки
 * и вдохи спор живут в разных местах конфига, и разъехаться они могут
 * незаметно: каждая правка по отдельности выглядит разумной, а вместе
 * они либо убивают за полминуты, либо перестают чувствоваться вовсе.
 */
class SymptomBalanceTest {

    /** Средняя прибавка за секунду от вдохов спор: шанс, помноженный на очки. */
    private static float отВдохов() {
        return PlagueConstants.GUST_CHANCE * PlagueConstants.GUST_POINTS;
    }

    private static float секундДоСтадии(int стадия, float очковВСекунду) {
        return PlagueConstants.PLAYER_STAGE_THRESHOLDS[стадия - 1] / очковВСекунду;
    }

    @Test
    void безПодготовкиПервыеСимптомыБыстрееМинуты() {
        float ставка = InfectionMath.экспозиция(4, false, 0f) + отВдохов();
        float секунд = секундДоСтадии(1, ставка);

        assertTrue(секунд < 60f,
            "Первая стадия должна приходить меньше чем за минуту, вышло " + секунд);
        assertTrue(секунд > 20f,
            "Быстрее двадцати секунд — это уже не болезнь, а капкан: " + секунд);
    }

    @Test
    void вГнилиДнёмРаботатьНельзяДольшеПятиМинут() {
        float ставка = InfectionMath.экспозиция(4, false, 0f) + отВдохов();
        assertTrue(секундДоСтадии(3, ставка) < 300f,
            "Лихорадка на четвёртом уровне обязана приходить быстрее пяти минут");
    }

    @Test
    void повязкаИЖелезоДаютЧасРаботы() {
        // Полный железный доспех — пятнадцать очков брони.
        float защита = 15 * PlagueConstants.ARMOR_PROTECTION_PER_POINT
                     + PlagueConstants.MASK_PROTECTION;
        assertTrue(защита < 0.9f, "Защита обязана оставаться ниже потолка 0.9");

        // Вдохов спор с повязкой не бывает вовсе — только ровная экспозиция.
        float ставка = InfectionMath.экспозиция(4, false, защита);
        float доЛихорадки = секундДоСтадии(3, ставка);

        assertTrue(доЛихорадки > 600f,
            "С повязкой и железом до лихорадки должно быть больше десяти минут, вышло "
            + доЛихорадки);
    }

    @Test
    void подготовкаДаётХотяБыВтроеБольшеВремени() {
        float безНеё = InfectionMath.экспозиция(4, false, 0f) + отВдохов();
        float сНей = InfectionMath.экспозиция(4, false,
            15 * PlagueConstants.ARMOR_PROTECTION_PER_POINT + PlagueConstants.MASK_PROTECTION);

        assertTrue(безНеё / сНей >= 3f,
            "Подготовка должна окупаться в разы, иначе её никто не возьмёт: "
            + (безНеё / сНей));
    }
}
