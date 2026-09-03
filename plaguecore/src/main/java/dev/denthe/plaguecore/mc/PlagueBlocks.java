package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
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
 * Восемь блоков материализации. Дизайн материализации, раздел 7.
 *
 * Регистрируются на мод-шине, не на игровой. Свойства копируются
 * с ванильных родственников, чтобы не выдумывать прочность и звук
 * с нуля: гнилая земля ведёт себя как земля, нарост — как лишайник.
 *
 * Цвета на карте держат палитру чумы: тёмная и серая, фиолетовый —
 * только там, где по замыслу видна сама зараза (нарост, споровый мешок).
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
            .mapColor(MapColor.COLOR_GRAY)
            .sound(SoundType.ROOTED_DIRT));

    /**
     * Заражённая земля с травяной кромкой. В спеке ядра её не было —
     * добавлена потому, что в мире читается заметно лучше ровного
     * коричневого куба, а стоит одной лишней регистрации.
     */
    public static final DeferredBlock<Block> ROTTED_GRASS = БЛОКИ.registerSimpleBlock(
        "rotted_grass",
        BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)
            .mapColor(MapColor.STONE)
            .sound(SoundType.ROOTED_DIRT));

    /**
     * Заражённый камень. Порода не обрастает плёнкой, а перерождается:
     * плёнка на утёсе терялась из виду, а тёмный камень читается издалека.
     * Идёт вглубь наравне с землёй, поэтому обрыв и карьер видно с обеих
     * сторон.
     */
    public static final DeferredBlock<Block> ROTTED_STONE = БЛОКИ.registerSimpleBlock(
        "rotted_stone",
        BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
            .mapColor(MapColor.COLOR_BLACK));

    /**
     * Заражённая листва. Одна на все породы: дуб, берёза, вишня и всё
     * прочее приходят в правило одним видом LEAVES и уходят этим блоком.
     * Так чума читается издалека одним цветом, а не палитрой леса.
     *
     * Ванильный LeavesBlock взят целиком ради шелеста, ножниц и прозрачности.
     * Ставим его всегда с PERSISTENT: осыпаться от потери ствола он
     * не должен, за его жизнь отвечает уровень чумы, а не дерево.
     */
    public static final DeferredBlock<LeavesBlock> BLIGHTED_LEAVES = БЛОКИ.registerBlock(
        "blighted_leaves",
        LeavesBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)
            .mapColor(MapColor.COLOR_GRAY));

    /**
     * Заражённая травинка. Ставится вместо ванильного пучка травы
     * и папоротника: зелёные кустики посреди Гнили выдавали, что чума
     * прошлась только по кубам.
     */
    public static final DeferredBlock<BlightedGrassBlock> BLIGHTED_GRASS = БЛОКИ.registerBlock(
        "blighted_grass",
        BlightedGrassBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .replaceable()
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .noOcclusion());

    /** Плёнка на любой грани: наросты на камне, дереве и руде. */
    public static final DeferredBlock<PlagueGrowthBlock> PLAGUE_GROWTH = БЛОКИ.registerBlock(
        "plague_growth",
        PlagueGrowthBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_PURPLE)
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
            .mapColor(MapColor.COLOR_GRAY)
            .noCollission()
            .instabreak()
            .sound(SoundType.WEEPING_VINES)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion());

    /**
     * Споровый мешок: бугор на полу пещеры. Не куб — своя модель
     * из Blockbench, поэтому noOcclusion, иначе соседние грани пропадут.
     */
    public static final DeferredBlock<SporeSacBlock> SPORE_SAC = БЛОКИ.registerBlock(
        "spore_sac",
        SporeSacBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_PURPLE)
            .strength(0.5f)
            .sound(SoundType.SLIME_BLOCK)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion());

    public static final DeferredItem<BlockItem> ROTTED_DIRT_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_DIRT);
    public static final DeferredItem<BlockItem> ROTTED_GRASS_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_GRASS);
    public static final DeferredItem<BlockItem> ROTTED_STONE_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_STONE);
    public static final DeferredItem<BlockItem> BLIGHTED_GRASS_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHTED_GRASS);
    public static final DeferredItem<BlockItem> BLIGHTED_LEAVES_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHTED_LEAVES);
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
