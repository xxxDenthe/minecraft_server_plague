package dev.denthe.classes;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Алтарь призвания — единственный блок мода. В творческую вкладку — {@link ClassCreativeTab}. */
public final class ClassBlocks {
    private ClassBlocks() {}

    public static final DeferredRegister.Blocks БЛОКИ =
        DeferredRegister.createBlocks(LmpcClasses.MODID);

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(LmpcClasses.MODID);

    public static final DeferredBlock<ClassAltarBlock> CLASS_ALTAR = БЛОКИ.registerBlock(
        "class_altar",
        ClassAltarBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS)
            .mapColor(MapColor.STONE)
            // Модель не заполняет куб (ножка тоньше, стол выступает),
            // поэтому блок не должен считаться сплошным: иначе игра
            // срезает верхнюю грань пола под алтарём и сквозь щели
            // видно пещеры.
            .noOcclusion());

    public static final DeferredItem<BlockItem> CLASS_ALTAR_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(CLASS_ALTAR);

    /**
     * Грядка бутона чумы — эксклюзив Фермера (спек классов, раздел 6).
     * Предмета-блока у неё нет: в руки грядка не берётся, её сажают
     * бутоном ({@link PlagueBloomSeedItem}), как ванильную пшеницу
     * семенами.
     */
    public static final DeferredBlock<PlagueBloomCropBlock> PLAGUE_BLOOM_CROP = БЛОКИ.registerBlock(
        "plague_bloom_crop",
        PlagueBloomCropBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT).mapColor(MapColor.COLOR_PURPLE));

    /**
     * Андезитовый очиститель поверхности — первый тир из спека ядра,
     * раздел 10.1. Ставить может кто угодно: это прямое требование
     * спека, чтобы компания без Create не выпадала из защиты. Класс
     * входит через мастерство партии, а не через право поставить блок.
     */
    public static final DeferredBlock<PurifierBlock> ANDESITE_PURIFIER = БЛОКИ.registerBlock(
        "andesite_purifier",
        PurifierBlock::new,
        // noOcclusion обязателен: модель уже не куб — тулово вдвинуто
        // на пиксель со всех сторон, и без него игра прячет грани
        // соседних блоков, а сквозь щели видно пустоту.
        BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE)
            .mapColor(MapColor.STONE)
            // Работающий очиститель светится вполсилы: ночью это
            // единственный способ увидеть с другого конца базы, что
            // он не встал без реагента.
            .lightLevel(состояние -> состояние.getValue(PurifierBlock.РАБОТАЕТ) ? 7 : 0)
            .noOcclusion());

    public static final DeferredItem<BlockItem> ANDESITE_PURIFIER_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ANDESITE_PURIFIER);

    /**
     * Латунный очиститель — второй тир спека ядра 10.1: шире область,
     * выше требуемая скорость, вчетверо больше расход реагента.
     *
     * Тот же класс блока и та же сущность блока, что у андезитового:
     * различия целиком в числах конфига, а не в поведении. Тир читается
     * из состояния ({@link PurifierBlock#латунный}), поэтому второго
     * класса, второго тикера и второй копии ночного шага не заведено.
     *
     * <b>Ставит только Кузнец</b> — единственное поведенческое отличие,
     * и оно тоже в {@link PurifierBlock}. Это требование спека:
     * доступным без класса должен быть первый тир, а не все.
     */
    public static final DeferredBlock<PurifierBlock> BRASS_PURIFIER = БЛОКИ.registerBlock(
        "brass_purifier",
        PurifierBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE)
            .mapColor(MapColor.GOLD)
            .lightLevel(состояние -> состояние.getValue(PurifierBlock.РАБОТАЕТ) ? 9 : 0)
            .noOcclusion());

    public static final DeferredItem<BlockItem> BRASS_PURIFIER_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BRASS_PURIFIER);

    public static void register(IEventBus modEventBus) {
        БЛОКИ.register(modEventBus);
        ПРЕДМЕТЫ.register(modEventBus);
    }
}
