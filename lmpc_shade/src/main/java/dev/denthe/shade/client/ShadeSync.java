package dev.denthe.shade.client;

import dev.denthe.shade.LmpcShade;
import dev.denthe.shade.ShadeApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Клиент: серверные значения цветокора накладываются поверх локального
 * конфига. При первом наложении локальные значения запоминаются и
 * возвращаются при выходе с сервера — одиночная игра без сервера не
 * затрагивается.
 *
 * Небо (overcast/cloudHeight) синхронизируется, но визуально применится
 * только при следующем заходе — эффекты измерения регистрируются один
 * раз при старте клиента.
 */
@EventBusSubscriber(modid = LmpcShade.MODID, value = Dist.CLIENT)
public final class ShadeSync {
    private ShadeSync() {}

    private static Map<String, Object> localBackup;

    /** Вызывается из обработчика пакета {@code ShadeNet.Sync}. */
    public static void apply(Map<String, String> serverValues) {
        if (localBackup == null) {
            localBackup = new LinkedHashMap<>();
            for (String id : ShadeApi.ids()) localBackup.put(id, ShadeApi.get(id));
        }
        for (String id : ShadeApi.ids()) {
            String sv = serverValues.get(id);
            ShadeApi.set(id, sv != null ? sv : localBackup.get(id));
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        if (localBackup == null) return;
        localBackup.forEach(ShadeApi::set);
        localBackup = null;
    }
}
