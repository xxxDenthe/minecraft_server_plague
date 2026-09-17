package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ритм страха. Спек `2026-09-17-horror-rezhissyor-design.md`, раздел 1.
 *
 * Здесь проверяется только арифметика: что копится, когда срабатывает
 * и когда обязано молчать. Ощущения — ритм и сила — проверяются на живой
 * сессии, а не тестом.
 */
class DreadMathTest {

    /** Веса по умолчанию из спека — те же числа, что уйдут в конфиг. */
    private static DreadMath.Веса веса() {
        return new DreadMath.Веса(
            0.8f,   // темнота
            0.3f,   // сумерки
            0.6f,   // гниль
            0.3f,   // пограничье
            0.4f,   // глубина
            0.3f,   // ночь
            0.2f,   // за стадию
            1.5f,   // одиночество (множитель)
            0.15f,  // за фазу (множитель)
            0.3f,   // успокоение (множитель)
            1.0f,   // падение в секунду
            40);    // ниже этого Y место считается подземельем
    }

    /** Полдень в городе: светло, чисто, рядом двое, здоров. */
    private static DreadMath.Факторы город() {
        return new DreadMath.Факторы(15, 0, false, 70, false, 0, false, 0, 2);
    }

    /** Ночь, один, в темноте, на Гнили, под землёй, вторая стадия. */
    private static DreadMath.Факторы гниль() {
        return new DreadMath.Факторы(0, 3, false, 20, true, 2, true, 2, 0);
    }

    @Test
    void вГородеДнёмНапряжениеПадает() {
        assertTrue(DreadMath.прирост(город(), веса()) < 0f,
            "светло, людно и чисто — человек должен успокаиваться");
    }

    @Test
    void вГнилиНочьюНапряжениеРастёт() {
        assertTrue(DreadMath.прирост(гниль(), веса()) > 1f,
            "темнота, одиночество и Гниль обязаны копить заметно");
    }

    @Test
    void одиночествоУсиливаетТоЖеМесто() {
        DreadMath.Факторы один = гниль();
        DreadMath.Факторы вдвоём = new DreadMath.Факторы(
            один.свет(), один.уровеньЧанка(), один.пограничье(), один.y(),
            один.ночь(), один.стадия(), false, один.фаза(), 1);
        assertTrue(DreadMath.прирост(один, веса()) > DreadMath.прирост(вдвоём, веса()),
            "одному в том же месте должно быть страшнее");
    }

    @Test
    void светлоеЛюдноеМестоГаситСтрах() {
        // Тот же лес ночью, но рядом двое и горит костёр.
        DreadMath.Факторы один = new DreadMath.Факторы(2, 0, false, 70, true, 0, true, 1, 0);
        DreadMath.Факторы вкомпании = new DreadMath.Факторы(12, 0, false, 70, true, 0, false, 1, 2);
        assertTrue(DreadMath.прирост(один, веса()) > DreadMath.прирост(вкомпании, веса()),
            "компания и свет обязаны заметно гасить прирост");
    }

    @Test
    void напряжениеЗажатоВГраницах() {
        assertEquals(0f, DreadMath.копить(0.5f, -10f), 1e-4f);
        assertEquals(100f, DreadMath.копить(99f, 50f), 1e-4f);
    }

    @Test
    void уровеньВыбираетсяПоНапряжению() {
        assertEquals(DreadMath.Уровень.НЕТ, DreadMath.уровень(10f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ШОРОХ, DreadMath.уровень(70f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ВИДЕНИЕ, DreadMath.уровень(90f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ЯВЛЕНИЕ, DreadMath.уровень(97f, 60f, 85f, 95f, true));
    }

    @Test
    void безБюджетаЯвлениеПадаетДоВидения() {
        assertEquals(DreadMath.Уровень.ВИДЕНИЕ,
            DreadMath.уровень(97f, 60f, 85f, 95f, false),
            "исчерпанный лимит явлений не должен глушить событие совсем");
    }

    @Test
    void вДолинеСобытийНет() {
        assertTrue(DreadMath.вДолине(1000L, 900L, 200));
        assertFalse(DreadMath.вДолине(1200L, 900L, 200));
    }

    @Test
    void бюджетЧасаЗакрывается() {
        assertTrue(DreadMath.бюджетЕсть(5, 6));
        assertFalse(DreadMath.бюджетЕсть(6, 6));
    }

    @Test
    void шорохСтавитсяЗаСпиной() {
        // Смотрим на восток (yaw = -90), значит источник должен
        // оказаться западнее игрока.
        float[] точка = DreadMath.заСпиной(100f, 64f, 100f, -90f, 4f);
        assertTrue(точка[0] < 100f, "звук обязан быть позади, а не перед лицом");
        assertEquals(64f, точка[1], 1e-3f);
    }

    @Test
    void шорохДержитЗаданнуюДальность() {
        float[] точка = DreadMath.заСпиной(0f, 64f, 0f, 37f, 5f);
        double дальность = Math.sqrt(точка[0] * точка[0] + точка[2] * точка[2]);
        assertEquals(5.0, дальность, 1e-3);
    }
}
