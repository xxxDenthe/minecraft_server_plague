package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Голос больного. Главное, что тут можно сломать молча, — сдвиг тона:
 * линия задержки продолжает выдавать похожий на речь звук даже когда
 * скорость чтения посчитана неверно. Поэтому меряем частоту на выходе.
 */
class SickVoiceTest {

    private static final int ЧАСТОТА = 48000;
    private static final int КАДР = 960;

    /** Синус заданной частоты, поданный кадрами по 20 мс. */
    private static short[] прогнать(SickVoice фильтр, float герц, int сила, int кадров) {
        short[] всё = new short[кадров * КАДР];
        short[] кадр = new short[КАДР];
        for (int к = 0; к < кадров; к++) {
            for (int i = 0; i < КАДР; i++) {
                double t = (double) (к * КАДР + i) / ЧАСТОТА;
                кадр[i] = (short) Math.round(Math.sin(2 * Math.PI * герц * t) * 12000);
            }
            фильтр.обработать(кадр, сила);
            System.arraycopy(кадр, 0, всё, к * КАДР, КАДР);
        }
        return всё;
    }

    /**
     * Частота по автокорреляции на второй половине записи. Переходы через
     * ноль тут не годятся: шум дыхания добавляет лишние пересечения у самого
     * нуля и завышает счёт на проценты, а мы ловим разницу в проценты.
     */
    private static float частота(short[] звук) {
        int с = звук.length / 2;
        int n = звук.length - с;
        double лучшая = -1;
        int лучшийЛаг = 0;
        for (int лаг = 100; лаг < 400; лаг++) {
            double сумма = 0;
            for (int i = 0; i + лаг < n; i++) сумма += (double) звук[с + i] * звук[с + i + лаг];
            сумма /= (n - лаг);
            if (сумма > лучшая) { лучшая = сумма; лучшийЛаг = лаг; }
        }
        return (float) ЧАСТОТА / лучшийЛаг;
    }

    @Test
    @DisplayName("тон опускается ровно на заявленные полутона")
    void тонОпускается() {
        for (int сила = 0; сила < SickVoice.УРОВНЕЙ; сила++) {
            // Шум и дрожь убрали бы переходы через ноль на счётчике, поэтому
            // меряем на 220 Гц с запасом амплитуды: остальные эффекты форму
            // мнут, но период не трогают.
            SickVoice ф = new SickVoice(new Random(7));
            float ожидание = 220f * (float) Math.pow(2.0, -PlagueConstants.VOICE_SEMITONES[сила] / 12.0);
            float вышло = частота(прогнать(ф, 220f, сила, 60));
            assertEquals(ожидание, вышло, ожидание * 0.06f,
                "сила " + сила + ": ждали " + ожидание + " Гц, вышло " + вышло);
        }
    }

    @Test
    @DisplayName("тишина на входе не рождает звука на выходе")
    void тишинаОстаётсяТишиной() {
        SickVoice ф = new SickVoice(new Random(7));
        short[] кадр = new short[КАДР];
        for (int к = 0; к < 30; к++) ф.обработать(кадр, 1);
        for (short с : кадр) assertEquals(0, с, "шум дыхания звучит без голоса");
    }

    @Test
    @DisplayName("громкость не улетает в клиппинг")
    void громкостьНеРастёт() {
        SickVoice ф = new SickVoice(new Random(7));
        short[] звук = прогнать(ф, 220f, 1, 60);
        int пик = 0;
        for (int i = звук.length / 2; i < звук.length; i++) пик = Math.max(пик, Math.abs(звук[i]));
        assertTrue(пик < 32000, "выход упирается в потолок: " + пик);
        assertTrue(пик > 3000, "выход слишком тихий: " + пик);
    }
}
