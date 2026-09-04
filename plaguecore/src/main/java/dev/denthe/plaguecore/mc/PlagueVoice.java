package dev.denthe.plaguecore.mc;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голос лихорадящего звучит больным. Спек подсистемы 2, раздел 7.
 * Разведка API — заметка `2026-09-03-golos-cherez-voicechat-api.md`.
 *
 * Портим пакет на сервере, а не на клиенте: у пакета есть сеттер,
 * поэтому исправленный звук получат все слушатели разом, и ни один
 * клиент не решает сам, каким ему слышать товарища.
 *
 * Тона не понижаем: кадр обязан остаться ровно 20 мс, а настоящий сдвиг
 * тона — это фазовый вокодер и отдельная большая работа. Глухота, хрип
 * и дрожь дают девять десятых впечатления даром.
 *
 * Зависимость необязательная: без Simple Voice Chat класс просто никогда
 * не грузится, остальной мод работает как обычно.
 */
@ForgeVoicechatPlugin
public class PlagueVoice implements VoicechatPlugin {

    /** Ниже этой стадии голос не трогаем вообще: обычное общение обязано остаться обычным. */
    private static final int ПОРОГ_СТАДИИ = 3;

    /** Частота дискретизации голосового чата: 48 кГц, кадр 20 мс = 960 сэмплов. */
    private static final float ЧАСТОТА = 48000f;

    // Настройки искажения по стадиям 3 и 4. Индекс — стадия минус ПОРОГ_СТАДИИ.
    /** Сила фильтра низких частот, 0..1: меньше — глуше. */
    private static final float[] ГЛУХОТА   = { 0.35f, 0.18f };
    /** Жёсткость мягкого ограничения: больше — сильнее хрип. */
    private static final float[] ХРИП      = { 1.6f, 3.0f };
    /** Доля шума от громкости самого голоса. */
    private static final float[] ШУМ       = { 0.03f, 0.10f };
    /** Глубина дрожи, 0..1. */
    private static final float[] ДРОЖЬ     = { 0f, 0.45f };
    /** Частота дрожи в герцах. */
    private static final float ДРОЖЬ_ГЦ = 5.5f;
    /** Вероятность, что кадр пропадёт целиком. */
    private static final float[] ОБРЫВ     = { 0f, 0.06f };

    /** Состояние на говорящего. Opus держит поток, поэтому кодек у каждого свой. */
    private static final Map<UUID, Голос> ГОЛОСА = new ConcurrentHashMap<>();

    private static final class Голос {
        final OpusDecoder декодер;
        final OpusEncoder кодер;
        final Random случай = new Random();
        float память;   // предыдущее значение фильтра низких частот
        double фаза;    // фаза дрожи, чтобы она не рвалась между кадрами

        Голос(VoicechatApi api) {
            декодер = api.createDecoder();
            кодер = api.createEncoder();
        }

        void закрыть() {
            if (!декодер.isClosed()) декодер.close();
            if (!кодер.isClosed()) кодер.close();
        }
    }

    @Override
    public String getPluginId() {
        return PlagueCore.MODID;
    }

    @Override
    public void registerEvents(EventRegistration регистрация) {
        регистрация.registerEvent(MicrophonePacketEvent.class, PlagueVoice::приРечи);
        регистрация.registerEvent(PlayerDisconnectedEvent.class, PlagueVoice::приВыходе);
        PlagueCore.LOG.info("Плагин голосового чата подключён: голос больного будет искажён");
    }

    private static void приВыходе(PlayerDisconnectedEvent событие) {
        Голос г = ГОЛОСА.remove(событие.getPlayerUuid());
        if (г != null) г.закрыть();
    }

    /**
     * Обработка идёт в потоке голосового чата, не в главном потоке сервера:
     * на TPS не влияет. Стадию читаем до декодирования — пока никто тяжело
     * не болен, плагин не делает вообще ничего.
     */
    private static void приРечи(MicrophonePacketEvent событие) {
        VoicechatConnection связь = событие.getSenderConnection();
        if (связь == null || связь.getPlayer() == null) return;
        if (!(связь.getPlayer().getPlayer() instanceof ServerPlayer говорящий)) return;

        int стадия = PlagueApi.getStage(говорящий);
        if (стадия < ПОРОГ_СТАДИИ) return;
        int сила = Math.min(стадия - ПОРОГ_СТАДИИ, ГЛУХОТА.length - 1);

        MicrophonePacket пакет = событие.getPacket();
        byte[] сжатое = пакет.getOpusEncodedData();
        if (сжатое == null || сжатое.length == 0) return;

        Голос г = ГОЛОСА.computeIfAbsent(говорящий.getUUID(), u -> new Голос(событие.getVoicechat()));

        try {
            if (г.случай.nextFloat() < ОБРЫВ[сила]) {
                // Кадр пропал целиком. Декодер всё равно надо продвинуть,
                // иначе следующий кадр он развернёт с щелчком.
                г.декодер.decode(сжатое);
                пакет.setOpusEncodedData(г.кодер.encode(new short[960]));
                return;
            }

            short[] звук = г.декодер.decode(сжатое);
            if (звук == null || звук.length == 0) return;
            исказить(звук, г, сила);
            пакет.setOpusEncodedData(г.кодер.encode(звук));
        } catch (Exception e) {
            // Сломанный кодек не должен обрывать разговор: отдаём как есть
            // и забываем состояние, чтобы следующий кадр начал с чистого.
            PlagueCore.LOG.warn("Не вышло исказить голос {}: {}",
                говорящий.getGameProfile().getName(), e.toString());
            ГОЛОСА.remove(говорящий.getUUID());
            г.закрыть();
        }
    }

    /**
     * Вся обработка — арифметика над массивом, без единой библиотеки.
     * Глухота — однополюсный фильтр низких частот, хрип — мягкое
     * ограничение плюс шум по громкости, дрожь — умножение на синус.
     */
    private static void исказить(short[] звук, Голос г, int сила) {
        float a = ГЛУХОТА[сила];
        float k = ХРИП[сила];
        float шум = ШУМ[сила];
        float глубина = ДРОЖЬ[сила];
        float шагФазы = (float) (2 * Math.PI * ДРОЖЬ_ГЦ / ЧАСТОТА);
        float нормировка = (float) Math.tanh(k);

        for (int i = 0; i < звук.length; i++) {
            float x = звук[i] / 32768f;

            г.память += a * (x - г.память);
            float y = г.память;

            y = (float) Math.tanh(k * y) / нормировка;
            if (шум > 0f) y += (г.случай.nextFloat() * 2f - 1f) * шум * Math.abs(y);

            if (глубина > 0f) {
                г.фаза += шагФазы;
                y *= 1f - глубина * (0.5f + 0.5f * (float) Math.sin(г.фаза));
            }

            звук[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y * 32768f)));
        }

        if (г.фаза > 2 * Math.PI) г.фаза %= 2 * Math.PI;
    }
}
