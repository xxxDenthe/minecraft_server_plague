package dev.denthe.classes.client;

import dev.denthe.classes.ClassBlockEntities;
import dev.denthe.classes.LmpcClasses;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиентские рендереры блок-сущностей мода.
 *
 * Пока он здесь один — вал очистителя. Отдельный класс всё равно нужен:
 * событие приходит на модовую шину, а не на игровую, и вешать его
 * на что-то из уже существующих клиентских классов значило бы смешать
 * две разные шины в одном файле.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID, value = Dist.CLIENT)
public final class ClassRenderers {

    private ClassRenderers() {}

    @SubscribeEvent
    public static void рендереры(EntityRenderersEvent.RegisterRenderers событие) {
        событие.registerBlockEntityRenderer(
            ClassBlockEntities.PURIFIER.get(), PurifierRenderer::new);
    }
}
