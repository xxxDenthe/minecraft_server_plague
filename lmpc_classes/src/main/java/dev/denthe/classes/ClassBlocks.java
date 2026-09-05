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
            .mapColor(MapColor.STONE));

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
        BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE).mapColor(MapColor.STONE));

    public static final DeferredItem<BlockItem> ANDESITE_PURIFIER_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ANDESITE_PURIFIER);

    public static void register(IEventBus modEventBus) {
        БЛОКИ.register(modEventBus);
        ПРЕДМЕТЫ.register(modEventBus);
    }
}
