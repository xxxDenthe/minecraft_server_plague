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
import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.SickVoice;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
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
 * Здесь только водопровод: раскодировать, отдать в {@link SickVoice},
 * закодировать обратно. Вся математика — там, в чистом пакете core,
 * и проверяется обычным JUnit без запуска игры.
 *
 * Зависимость необязательная: без Simple Voice Chat класс просто никогда
 * не грузится, остальной мод работает как обычно.
 */
@ForgeVoicechatPlugin
public class PlagueVoice implements VoicechatPlugin {

    /** Состояние на говорящего. И Opus, и фильтры держат поток, поэтому у каждого своё. */
    private static final Map<UUID, Голос> ГОЛОСА = new ConcurrentHashMap<>();

    private static final class Голос {
        final OpusDecoder декодер;
        final OpusEncoder кодер;
        final SickVoice фильтр = new SickVoice();

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

        int порог = PlagueConstants.VOICE_MIN_STAGE;
        int стадия = PlagueApi.getStage(говорящий);
        if (стадия < порог) return;

        MicrophonePacket пакет = событие.getPacket();
        byte[] сжатое = пакет.getOpusEncodedData();
        if (сжатое == null || сжатое.length == 0) return;

        Голос г = ГОЛОСА.computeIfAbsent(говорящий.getUUID(), u -> new Голос(событие.getVoicechat()));

        try {
            short[] звук = г.декодер.decode(сжатое);
            if (звук == null || звук.length == 0) return;
            г.фильтр.обработать(звук, стадия - порог);
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
}
