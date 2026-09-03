package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;

/** Последний присланный сервером снимок чужого инвентаря. */
public final class GmInvData {
    private GmInvData() {}

    private static volatile GmNetwork.Inventory current;

    public static void set(GmNetwork.Inventory inv) {
        current = inv;
    }

    public static GmNetwork.Inventory get() {
        return current;
    }
}
