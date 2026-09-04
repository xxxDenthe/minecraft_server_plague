package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Заражённая скотина. Первые сущности мода — до них тут были одни блоки.
 *
 * Размеры взяты у ванильных родственников один в один: геометрия у обеих
 * заражённых версий ванильная, менять хитбокс не за чем, а разошедшийся
 * с моделью хитбокс потом ловится тяжело.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueEntities {
    private PlagueEntities() {}

    public static final DeferredRegister<EntityType<?>> СУЩНОСТИ =
        DeferredRegister.create(Registries.ENTITY_TYPE, PlagueCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<InfectedAnimal>> INFECTED_PIG =
        СУЩНОСТИ.register("infected_pig", () -> EntityType.Builder
            .of(InfectedAnimal::new, MobCategory.MONSTER)
            .sized(0.9F, 0.9F)
            .eyeHeight(0.765F)
            .clientTrackingRange(10)
            .build("infected_pig"));

    public static final DeferredHolder<EntityType<?>, EntityType<InfectedAnimal>> INFECTED_COW =
        СУЩНОСТИ.register("infected_cow", () -> EntityType.Builder
            .of(InfectedAnimal::new, MobCategory.MONSTER)
            .sized(0.9F, 1.4F)
            .eyeHeight(1.3F)
            .clientTrackingRange(10)
            .build("infected_cow"));

    /**
     * Мутировавший зомби. Хитбокс ванильный, один в один зомбиный: наросты
     * торчат за его пределы, но расширять коробку под них незачем — по ним
     * не бьют, они украшение, а разошедшийся с моделью хитбокс потом
     * ловится тяжело.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MutatedZombie>> MUTATED_ZOMBIE =
        СУЩНОСТИ.register("mutated_zombie", () -> EntityType.Builder
            .of(MutatedZombie::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .eyeHeight(1.74F)
            .clientTrackingRange(8)
            .build("mutated_zombie"));

    public static void register(IEventBus modEventBus) {
        СУЩНОСТИ.register(modEventBus);
    }

    @SubscribeEvent
    public static void атрибуты(EntityAttributeCreationEvent событие) {
        событие.put(INFECTED_PIG.get(), InfectedAnimal.атрибутыСвиньи().build());
        событие.put(INFECTED_COW.get(), InfectedAnimal.атрибутыКоровы().build());
        событие.put(MUTATED_ZOMBIE.get(), MutatedZombie.атрибуты().build());
    }
}
