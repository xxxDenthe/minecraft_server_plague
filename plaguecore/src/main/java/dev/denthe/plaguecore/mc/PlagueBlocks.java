package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.BlockItem;

/**
 * Пять блоков материализации. Дизайн материализации, раздел 7.
 *
 * Регистрируются на мод-шине, не на игровой. Свойства копируются
 * с ванильных родственников, чтобы не выдумывать прочность и звук
 * с нуля: гнилая земля ведёт себя как земля, нарост — как лишайник.
 */
public final class PlagueBlocks {
    private PlagueBlocks() {}

    public static final DeferredRegister.Blocks БЛОКИ =
        DeferredRegister.createBlocks(PlagueCore.MODID);

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(PlagueCore.MODID);

    /** Гнилая земля: база Гнили и пола пещер. */
    public static final DeferredBlock<Block> ROTTED_DIRT = БЛОКИ.registerSimpleBlock(
        "rotted_dirt",
        BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)
            .mapColor(MapColor.TERRACOTTA_BROWN)
            .sound(SoundType.ROOTED_DIRT));

    /**
     * Заражённая земля с травяной кромкой. В спеке ядра её не было —
     * добавлена потому, что в мире читается заметно лучше ровного
     * коричневого куба, а стоит одной лишней регистрации.
     */
    public static final DeferredBlock<Block> ROTTED_GRASS = БЛОКИ.registerSimpleBlock(
        "rotted_grass",
        BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)
            .mapColor(MapColor.COLOR_GREEN)
            .sound(SoundType.ROOTED_DIRT));

    /** Плёнка на любой грани: наросты на камне, дереве и руде. */
    public static final DeferredBlock<PlagueGrowthBlock> PLAGUE_GROWTH = БЛОКИ.registerBlock(
        "plague_growth",
        PlagueGrowthBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .replaceable()
            .noCollission()
            .strength(0.2f)
            .sound(SoundType.GLOW_LICHEN)
            .pushReaction(PushReaction.DESTROY));

    /** Свисающая лоза. */
    public static final DeferredBlock<BlightVineBlock> BLIGHT_VINE = БЛОКИ.registerBlock(
        "blight_vine",
        BlightVineBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .noCollission()
            .instabreak()
            .sound(SoundType.WEEPING_VINES)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion());

    /** Споровый мешок. Обычный куб, но мягкий и глухой. */
    public static final DeferredBlock<Block> SPORE_SAC = БЛОКИ.registerSimpleBlock(
        "spore_sac",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(0.5f)
            .sound(SoundType.SLIME_BLOCK));

    public static final DeferredItem<BlockItem> ROTTED_DIRT_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_DIRT);
    public static final DeferredItem<BlockItem> ROTTED_GRASS_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_GRASS);
    public static final DeferredItem<BlockItem> PLAGUE_GROWTH_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(PLAGUE_GROWTH);
    public static final DeferredItem<BlockItem> BLIGHT_VINE_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHT_VINE);
    public static final DeferredItem<BlockItem> SPORE_SAC_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(SPORE_SAC);

    public static void register(IEventBus modEventBus) {
        БЛОКИ.register(modEventBus);
        ПРЕДМЕТЫ.register(modEventBus);
    }
}
