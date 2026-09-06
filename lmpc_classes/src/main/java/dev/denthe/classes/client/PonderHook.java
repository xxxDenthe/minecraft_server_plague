package dev.denthe.classes.client;

import dev.denthe.classes.LmpcClasses;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Подключение сцен «размышления» (Ponder) — клиентская половина
 * и единственное место, где мод вообще заговаривает с Ponder.
 *
 * <b>Почему через отдельный класс и проверку {@link ModList}.</b>
 * {@link PurifierPonderPlugin} реализует интерфейс Ponder, поэтому
 * без Ponder на classpath JVM не сможет загрузить сам класс. Здесь
 * ссылок на Ponder нет ни в полях, ни в подписях — только вызов
 * внутри проверки, и без Create мод спокойно грузится дальше без
 * размышлений. Ровно та беда, на которой в 0.3.0 обжёгся кулон
 * Клирика с Curios: там пришлось честно писать `required`.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID, value = Dist.CLIENT)
public final class PonderHook {
    private PonderHook() {}

    @SubscribeEvent
    public static void клиентГотов(FMLClientSetupEvent событие) {
        if (!ModList.get().isLoaded("ponder")) {
            LmpcClasses.LOG.info("Ponder не найден — сцены размышления пропущены");
            return;
        }
        событие.enqueueWork(PurifierPonderPlugin::подключить);
    }
}
