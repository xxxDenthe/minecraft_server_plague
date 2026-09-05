package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;

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
        стадия = пакет.стадия();
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

    public static void принятьСнимок(PlagueNetwork.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlagueMapScreen экран) {
            экран.обновить(snapshot);
        } else {
            mc.setScreen(new PlagueMapScreen(snapshot));
        }
    }
}
