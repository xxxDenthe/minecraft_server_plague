package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;
import net.minecraft.client.Minecraft;

/**
 * Единственная точка, куда общий сетевой код обращается за клиентским
 * поведением. На сервере эти методы не выполняются (лямбды-обработчики
 * из GmNetwork ссылаются сюда, но вызываются только на клиенте).
 */
public final class GmMapClientAccess {
    private GmMapClientAccess() {}

    public static void accept(GmNetwork.Positions payload) {
        GmMapData.update(payload.players());
    }

    public static void acceptMarks(GmNetwork.Marks payload) {
        GmMapData.updateMarks(payload.marks());
    }

    /** Настройки Atmospherics от сервера: применяем у себя как есть. */
    public static void acceptAtmo(GmNetwork.Atmo payload) {
        if (payload.settings().isEmpty()) {
            AtmoAccess.resetAll();
            return;
        }
        payload.settings().forEach((path, value) -> AtmoAccess.set("atmo:" + path, value));
    }

    public static void acceptInventory(GmNetwork.Inventory payload) {
        GmInvData.set(payload);
        Minecraft.getInstance().setScreen(new InventoryViewScreen(payload.name()));
    }
}
