package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.RottenHeart;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Три пути к файлам Сердца — больше модели знать нечего.
 *
 * Исходник и разбор устройства модели — rotten_heart/README.md.
 * Второй текстуры «потрескавшегося» Сердца нет намеренно: куски и так
 * отваливаются целиком, разбитость читается по силуэту.
 */
public class RottenHeartModel extends GeoModel<RottenHeart> {

    private static final ResourceLocation ГЕОМЕТРИЯ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "geo/rotten_heart.geo.json");
    private static final ResourceLocation ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/rotten_heart.png");
    private static final ResourceLocation АНИМАЦИЯ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "animations/rotten_heart.animation.json");

    @Override
    public ResourceLocation getModelResource(RottenHeart сердце) {
        return ГЕОМЕТРИЯ;
    }

    @Override
    public ResourceLocation getTextureResource(RottenHeart сердце) {
        return ТЕКСТУРА;
    }

    @Override
    public ResourceLocation getAnimationResource(RottenHeart сердце) {
        return АНИМАЦИЯ;
    }
}
