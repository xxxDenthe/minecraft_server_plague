package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.Watcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Наблюдатель. Не призрак и не зомби: человек в последней стадии чумы.
 *
 * Силуэт человеческий, но с тремя поправками, из-за которых «что-то не
 * так» замечаешь не сразу: он на пиксель выше игрока, кисти висят ниже
 * бёдер, правое плечо сломано. В рёбрах рваная дыра, собранная
 * фрагментами по полпикселя, — не аккуратное окно, а разрыв.
 *
 * Геометрия и кадры живут в {@code textures_src/WATCHER/watcher.bbmodel}
 * и правятся в Blockbench; как оттуда выгружать — {@code README.md}
 * в той же папке. Руками geo.json не трогают.
 */
public class WatcherGeoModel extends GeoModel<Watcher> {

    private static final ResourceLocation ГЕОМЕТРИЯ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "geo/watcher.geo.json");
    private static final ResourceLocation ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/watcher.png");
    private static final ResourceLocation АНИМАЦИЯ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "animations/watcher.animation.json");

    @Override
    public ResourceLocation getModelResource(Watcher наблюдатель) {
        return ГЕОМЕТРИЯ;
    }

    @Override
    public ResourceLocation getTextureResource(Watcher наблюдатель) {
        return ТЕКСТУРА;
    }

    @Override
    public ResourceLocation getAnimationResource(Watcher наблюдатель) {
        return АНИМАЦИЯ;
    }

    /**
     * Голова смотрит на игрока. В отличие от ванильной модели GeckoLib
     * сам этого не делает, поворот надо доложить руками — и обязательно
     * прибавкой к тому, что уже наложила анимация: в idle голова
     * дёргается своими кадрами, и присвоение вместо прибавки стёрло бы
     * ровно тот сбой, ради которого всё затевалось.
     *
     * Тело при этом не поворачивается: этим занимается сама сущность,
     * она пишет в {@code yHeadRot} мимо {@code yBodyRot}. Фигура, стоящая
     * к тебе анфас, читается как моб, который агрится; нужна фигура,
     * которая стоит как встала, а голова догнала.
     */
    @Override
    public void setCustomAnimations(Watcher наблюдатель, long ид,
                                    AnimationState<Watcher> состояние) {
        super.setCustomAnimations(наблюдатель, ид, состояние);

        EntityModelData данные = состояние.getData(DataTickets.ENTITY_MODEL_DATA);
        if (данные == null) {
            return;
        }
        GeoBone голова = getBone("head").orElse(null);
        if (голова == null) {
            return;
        }
        голова.setRotX(голова.getRotX() + данные.headPitch() * Mth.DEG_TO_RAD);
        голова.setRotY(голова.getRotY() + данные.netHeadYaw() * Mth.DEG_TO_RAD);
    }
}
