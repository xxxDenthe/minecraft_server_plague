package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.mc.Watcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Отрисовка Наблюдателя. Всё, что он умеет, лежит в кадрах анимации,
 * поэтому рендереру делать нечего — кроме тени.
 *
 * Брони он не носит и слоёв её не получает: человек в броне читается
 * как игрок, а он должен читаться как тот, кто когда-то был игроком.
 */
public class WatcherRenderer extends GeoEntityRenderer<Watcher> {

    public WatcherRenderer(EntityRendererProvider.Context контекст) {
        super(контекст, new WatcherGeoModel());
        this.shadowRadius = 0.5F;
    }
}
