package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.InfectedAnimal;
import dev.denthe.plaguecore.mc.MutatedZombie;
import dev.denthe.plaguecore.mc.PlagueEntities;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Отрисовка заражённых сущностей.
 *
 * У скотины своих моделей нет и не нужно: геометрия обеих заражённых
 * версий — ванильная, вплоть до UV, это проверено чтением прямоугольников
 * из проекта Blockbench. Поэтому берём ванильные {@link PigModel} и
 * {@link CowModel} с ванильными слоями и подменяем только текстуру.
 *
 * У зомби геометрия своя — на нём наросты, — но класс модели всё равно
 * ванильный {@link ZombieModel}: он ищет части по именам и не знает, что
 * к ним подвешено. Отличается только определение слоя, оно в
 * {@link MutatedZombieModel}. Так анимация зомби достаётся даром.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class PlagueEntityRenderers {
    private PlagueEntityRenderers() {}

    private static final ResourceLocation ТЕКСТУРА_СВИНЬИ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/infected_pig.png");
    private static final ResourceLocation ТЕКСТУРА_КОРОВЫ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/infected_cow.png");
    private static final ResourceLocation ТЕКСТУРА_ЗОМБИ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "textures/entity/mutated_zombie.png");

    @SubscribeEvent
    public static void рендереры(EntityRenderersEvent.RegisterRenderers событие) {
        событие.registerEntityRenderer(PlagueEntities.INFECTED_PIG.get(),
            контекст -> new Рендерер(контекст,
                new PigModel<>(контекст.bakeLayer(ModelLayers.PIG)), 0.7F, ТЕКСТУРА_СВИНЬИ));

        событие.registerEntityRenderer(PlagueEntities.INFECTED_COW.get(),
            контекст -> new Рендерер(контекст,
                new CowModel<>(контекст.bakeLayer(ModelLayers.COW)), 0.7F, ТЕКСТУРА_КОРОВЫ));

        событие.registerEntityRenderer(PlagueEntities.MUTATED_ZOMBIE.get(), РендерерЗомби::new);

        // Сердце рисует GeckoLib: у него своя геометрия, своя анимация
        // и куски, которые прячутся по одному. Слой ванильной модели
        // ему не нужен — GeckoLib читает geo.json сам.
        событие.registerEntityRenderer(PlagueEntities.ROTTEN_HEART.get(), RottenHeartRenderer::new);
    }

    @SubscribeEvent
    public static void слои(EntityRenderersEvent.RegisterLayerDefinitions событие) {
        событие.registerLayerDefinition(MutatedZombieModel.СЛОЙ, MutatedZombieModel::создатьСлой);
    }

    /**
     * Зомби. Брони не носит и слоёв её не получает: наросты торчат ровно
     * там, где ванильная броня рисует нагрудник и наплечники, и одно
     * налезало бы на другое.
     */
    private static final class РендерерЗомби
            extends HumanoidMobRenderer<MutatedZombie, ZombieModel<MutatedZombie>> {

        РендерерЗомби(EntityRendererProvider.Context контекст) {
            super(контекст, new ZombieModel<>(контекст.bakeLayer(MutatedZombieModel.СЛОЙ)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(MutatedZombie сущность) {
            return ТЕКСТУРА_ЗОМБИ;
        }
    }

    /** Ванильная модель плюс наша текстура — больше рендереру знать нечего. */
    private static final class Рендерер
            extends MobRenderer<InfectedAnimal, EntityModel<InfectedAnimal>> {

        private final ResourceLocation текстура;

        Рендерер(EntityRendererProvider.Context контекст,
                 EntityModel<InfectedAnimal> модель, float тень, ResourceLocation текстура) {
            super(контекст, модель, тень);
            this.текстура = текстура;
        }

        @Override
        public ResourceLocation getTextureLocation(InfectedAnimal сущность) {
            return текстура;
        }
    }
}
