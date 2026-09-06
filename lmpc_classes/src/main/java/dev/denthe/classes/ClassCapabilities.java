package dev.denthe.classes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Возможности блоков для соседних машин. Пока одна: очиститель
 * принимает реагент как обычный контейнер, поэтому его заряжают
 * воронка, лента, кран и рука Create.
 *
 * <p>Зачем это здесь. Очиститель стоит посреди механизмов Create,
 * но до 0.12.0 подключался к ним ровно одним способом — валом.
 * Реагент в него можно было положить только рукой, то есть блок,
 * который должен работать все четыре дня сессии, требовал живого
 * человека каждую ночь. Теперь заряд возят лентой, а сравнитель
 * рядом показывает остаток — из очистителя можно собрать настоящую
 * оборону, а не тумбочку, к которой ходят.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassCapabilities {
    private ClassCapabilities() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCapabilitiesEvent событие) {
        событие.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ClassBlockEntities.PURIFIER.get(),
            (очиститель, сторона) -> очиститель.ворота());
    }
}
