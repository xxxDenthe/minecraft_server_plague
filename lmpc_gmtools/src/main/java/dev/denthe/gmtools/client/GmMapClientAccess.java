package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;

/**
 * Единственная точка, куда общий сетевой код обращается за клиентским
 * поведением. Сам класс на сервере не грузится: лямбда-обработчик из
 * GmNetwork ссылается сюда, но на сервере не выполняется.
 */
public final class GmMapClientAccess {
    private GmMapClientAccess() {}

    public static void accept(GmNetwork.Positions payload) {
        GmMapData.update(payload.players());
    }
}
