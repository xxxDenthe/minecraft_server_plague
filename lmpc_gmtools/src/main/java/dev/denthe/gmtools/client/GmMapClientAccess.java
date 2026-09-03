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

    public static void acceptInventory(GmNetwork.Inventory payload) {
        GmInvData.set(payload);
        Minecraft.getInstance().setScreen(new InventoryViewScreen(payload.name()));
    }
}
