package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Послушать голос больного, не запуская игру и не заражая живого человека.
 *
 * Живёт в тестовых исходниках нарочно: в джарник мода не попадает.
 * Запуск — задача `voicePreview` в build.gradle.
 *
 * Ждёт на входе любой WAV с речью (хоть диктофонная запись с телефона),
 * сам приводит его к 48 кГц моно и пишет рядом обработанный файл.
 */
public final class SickVoiceCli {
    private SickVoiceCli() {}

    private static final AudioFormat ФОРМАТ =
        new AudioFormat(48000f, 16, 1, true, false);

    public static void main(String[] аргументы) throws Exception {
        if (аргументы.length < 1) {
            System.out.println("Нужен путь к WAV. Второй аргумент — сила 0 или 1 (по умолчанию обе).");
            return;
        }
        File вход = new File(аргументы[0]);
        short[] исходник = прочитать(вход);
        System.out.printf("Прочитано %.2f с из %s%n", исходник.length / 48000f, вход);

        if (аргументы.length >= 2) {
            обработать(вход, исходник, Integer.parseInt(аргументы[1]));
        } else {
            for (int сила = 0; сила < SickVoice.УРОВНЕЙ; сила++) обработать(вход, исходник, сила);
        }
    }

    private static void обработать(File вход, short[] исходник, int сила) throws Exception {
        short[] звук = исходник.clone();
        SickVoice фильтр = new SickVoice();
        for (int i = 0; i < звук.length; i += 960) {
            short[] кадр = new short[Math.min(960, звук.length - i)];
            System.arraycopy(звук, i, кадр, 0, кадр.length);
            фильтр.обработать(кадр, сила);
            System.arraycopy(кадр, 0, звук, i, кадр.length);
        }

        String полное = вход.getName();
        int точка = полное.lastIndexOf('.');
        String имя = точка > 0 ? полное.substring(0, точка) : полное;
        File выход = new File(вход.getAbsoluteFile().getParentFile(),
            имя + "-стадия" + (сила + PlagueConstants.VOICE_MIN_STAGE) + ".wav");
        записать(выход, звук);
        System.out.printf("  стадия %d (-%.1f полутона) → %s%n",
            сила + PlagueConstants.VOICE_MIN_STAGE, PlagueConstants.VOICE_SEMITONES[сила], выход);
    }

    private static short[] прочитать(File файл) throws Exception {
        try (AudioInputStream сырой = AudioSystem.getAudioInputStream(файл);
             AudioInputStream поток = AudioSystem.getAudioInputStream(ФОРМАТ, сырой)) {
            byte[] байты = поток.readAllBytes();
            ShortBuffer б = ByteBuffer.wrap(байты).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] звук = new short[б.remaining()];
            б.get(звук);
            return звук;
        }
    }

    private static void записать(File файл, short[] звук) throws Exception {
        ByteBuffer б = ByteBuffer.allocate(звук.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short с : звук) б.putShort(с);
        try (AudioInputStream поток = new AudioInputStream(
                new ByteArrayInputStream(б.array()), ФОРМАТ, звук.length)) {
            AudioSystem.write(поток, AudioFileFormat.Type.WAVE, файл);
        }
    }
}
