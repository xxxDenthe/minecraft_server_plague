package dev.denthe.plaguecore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denthe.plaguecore.core.HeartDecay;
import dev.denthe.plaguecore.mc.RottenHeart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Отрисовка Сердца чумы. Вся работа — спрятать кости отвалившихся кусков.
 *
 * {@code setHidden} выставляется каждый кадр и для скрытых, и для видимых.
 * Это не перестраховка: {@link BakedGeoModel} один на все Сердца в мире,
 * и флаг, выставленный один раз, продырявил бы соседнее целое Сердце.
 */
public class RottenHeartRenderer extends GeoEntityRenderer<RottenHeart> {

    public RottenHeartRenderer(EntityRendererProvider.Context контекст) {
        super(контекст, new RottenHeartModel());
        this.shadowRadius = 1.4F;
    }

    @Override
    public void preRender(PoseStack стек, RottenHeart сердце, BakedGeoModel модель,
                          MultiBufferSource буферы, VertexConsumer вершины,
                          boolean повторно, float частичныйТик,
                          int свет, int наложение, int цвет) {

        int маска = сердце.маскаКусков();
        for (int кусок = 0; кусок < HeartDecay.КУСКОВ; кусок++) {
            boolean спрятан = (маска & (1 << кусок)) != 0;
            модель.getBone(HeartDecay.КОСТИ[кусок])
                .ifPresent(кость -> кость.setHidden(спрятан));
        }

        super.preRender(стек, сердце, модель, буферы, вершины,
            повторно, частичныйТик, свет, наложение, цвет);
    }
}
