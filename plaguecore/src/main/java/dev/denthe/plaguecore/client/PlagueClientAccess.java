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

    public static void принятьСнимок(PlagueNetwork.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlagueMapScreen экран) {
            экран.обновить(snapshot);
        } else {
            mc.setScreen(new PlagueMapScreen(snapshot));
        }
    }
}
