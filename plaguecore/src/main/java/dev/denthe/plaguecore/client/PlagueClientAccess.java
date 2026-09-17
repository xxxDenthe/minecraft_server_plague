package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Единственная точка входа клиента. Обработчик пакета в общем коде
 * ссылается только сюда, поэтому на выделенном сервере ни один
 * клиентский класс не грузится.
 */
public final class PlagueClientAccess {
    private PlagueClientAccess() {}

    /** Стадия чумы у игрока за этим клиентом. Приходит пакетом при каждой смене. */
    private static int стадия = 0;

    public static int стадия() { return стадия; }

    public static void принятьСтадию(PlagueNetwork.Stage пакет) {
        int была = стадия;
        стадия = пакет.стадия();
        HealthMemory.приСмене(была, стадия);
    }

    /**
     * Игровое время начала вспышки, в тиках клиента. Отрицательное —
     * вспышки нет. Рисует {@link PlagueOverlay}.
     */
    private static long вспышкаС = -1L;

    public static long вспышкаС() { return вспышкаС; }

    public static void принятьВспышку(PlagueNetwork.Flash пакет) {
        Minecraft mc = Minecraft.getInstance();
        вспышкаС = mc.level == null ? -1L : mc.level.getGameTime();
    }

    /**
     * Ручки голоса, как их держит сервер. Пусто, пока сервер не прислал:
     * панель мастера игры тогда покажет свои местные значения.
     * Порядок — {@link dev.denthe.plaguecore.VoiceKnobs#ВСЕ}.
     */
    private static float[] голос = new float[0];

    /** Читает панель мастера игры (lmpc_gmtools) рефлексией. Не переименовывать. */
    public static float[] голос() { return голос.clone(); }

    public static void принятьГолос(PlagueNetwork.Voice пакет) {
        голос = пакет.значения();
    }

    /** Видение страха: темнота, сердцебиение или силуэт. Разбирает {@link DreadClient}. */
    public static void видение(PlagueNetwork.Vision пакет) {
        DreadClient.принять(пакет);
    }

    /** Словарь тайнописи. Разбирает и держит {@link SecretText}. */
    public static void принятьСлова(PlagueNetwork.Words пакет) {
        SecretText.принять(пакет);
    }

    /** Пометки Мастера игры. Разбирает {@link HealthMarksClient}. */
    public static void принятьПометки(PlagueNetwork.MarkList пакет) {
        HealthMarksClient.принять(пакет);
    }

    /** Чужая рука на пульте нашего тела. Разбирает {@link PossessionClient}. */
    public static void принятьУправление(PlagueNetwork.Drive пакет) {
        PossessionClient.принять(пакет);
    }

    /** Кем правим мы сами, если сидим за пультом. */
    public static void принятьКуклу(PlagueNetwork.Puppet пакет) {
        PossessionClient.принятьКуклу(пакет);
    }

    /**
     * Впечатление о соседе. Открывает чужой осмотр — но только поверх
     * пустоты: если у игрока уже открыт какой-то экран (инвентарь, чат,
     * свой же осмотр), пакет его не сносит.
     */
    public static void принятьВпечатление(PlagueNetwork.Impression пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;
        if (mc.level.getEntity(пакет.сущность()) instanceof Player кто) {
            HealthScreen.открытьЧужой(кто, пакет.впечатление());
        }
    }

    public static void принятьСнимок(PlagueNetwork.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlagueMapScreen экран) {
            экран.обновить(snapshot);
        } else {
            mc.setScreen(new PlagueMapScreen(snapshot));
        }
    }
}
