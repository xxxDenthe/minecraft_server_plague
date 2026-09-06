package dev.denthe.classes;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Сущности блоков мода. Пока одна — очиститель, единственный блок с работой внутри. */
public final class ClassBlockEntities {
    private ClassBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> ТИПЫ =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LmpcClasses.MODID);

    /**
     * Один тип на оба очистителя: андезитовый и латунный отличаются
     * числами конфига, а не поведением, и делить их на два типа значило бы
     * держать две копии одного тикера. Имя типа осталось прежним
     * (`andesite_purifier`) намеренно — смена ключа обнулила бы содержимое
     * уже поставленных блоков в мире владельца.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurifierBlockEntity>> PURIFIER =
        ТИПЫ.register("andesite_purifier", () -> BlockEntityType.Builder.of(
            PurifierBlockEntity::new,
            ClassBlocks.ANDESITE_PURIFIER.get(), ClassBlocks.BRASS_PURIFIER.get()).build(null));

    public static void register(IEventBus modEventBus) {
        ТИПЫ.register(modEventBus);
    }
}
