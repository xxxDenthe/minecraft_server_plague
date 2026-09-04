package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/**
 * Десять блоков материализации. Дизайн материализации, раздел 7.
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
     * Гнилое бревно. Одно на все породы, как и листва: дуб, берёза и вишня
     * приходят в правило одним видом LOG и уходят этим блоком, чтобы
     * мёртвый лес читался одним цветом.
     *
     * Столб, а не куб: ось сохраняется при подмене, иначе лежачие брёвна
     * в развалинах встали бы торчком.
     */
    public static final DeferredBlock<RotatedPillarBlock> ROTTED_LOG = БЛОКИ.registerBlock(
        "rotted_log",
        RotatedPillarBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)
            .mapColor(MapColor.COLOR_GRAY));

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
     * Иссохшая листва — вторая, поздняя стадия того же увядания. Заражённая
     * листва выше ещё живая и держит цвет шкуры заражённой скотины; эта
     * досуха мертва и сидит в нижней половине той же серой лестницы.
     *
     * Правило поверхности ставит её с уровня Гнили (3 и выше), там же,
     * где гниют ствол и камень. До Гнили листва остаётся заражённой.
     */
    public static final DeferredBlock<LeavesBlock> WITHERED_LEAVES = БЛОКИ.registerBlock(
        "withered_leaves",
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

    /**
     * Заражённая высокая трава: ванильная высокая трава и большой
     * папоротник. Свойства взяты с ванильной высокой травы целиком —
     * шелест, мгновенный слом, проходимость.
     */
    public static final DeferredBlock<BlightedTallGrassBlock> BLIGHTED_TALL_GRASS =
        БЛОКИ.registerBlock(
            "blighted_tall_grass",
            BlightedTallGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)
                .mapColor(MapColor.COLOR_GRAY)
                .offsetType(BlockBehaviour.OffsetType.XZ));

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
    public static final DeferredItem<BlockItem> ROTTED_LOG_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(ROTTED_LOG);
    public static final DeferredItem<BlockItem> BLIGHTED_GRASS_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHTED_GRASS);
    public static final DeferredItem<BlockItem> BLIGHTED_TALL_GRASS_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHTED_TALL_GRASS);
    public static final DeferredItem<BlockItem> BLIGHTED_LEAVES_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHTED_LEAVES);
    public static final DeferredItem<BlockItem> WITHERED_LEAVES_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(WITHERED_LEAVES);
    public static final DeferredItem<BlockItem> PLAGUE_GROWTH_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(PLAGUE_GROWTH);
    public static final DeferredItem<BlockItem> BLIGHT_VINE_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(BLIGHT_VINE);
    public static final DeferredItem<BlockItem> SPORE_SAC_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(SPORE_SAC);

    /**
     * Яйца призыва заражённой скотины. Естественный способ достать моба
     * в творческом режиме: без них остаётся только команда summon.
     *
     * Цвета держат палитру чумы — серая основа, фиолетовая крапина.
     */
    public static final DeferredItem<DeferredSpawnEggItem> INFECTED_PIG_SPAWN_EGG =
        ПРЕДМЕТЫ.registerItem("infected_pig_spawn_egg",
            свойства -> new DeferredSpawnEggItem(
                PlagueEntities.INFECTED_PIG, 0x585048, 0x5C3A5C, свойства));

    public static final DeferredItem<DeferredSpawnEggItem> INFECTED_COW_SPAWN_EGG =
        ПРЕДМЕТЫ.registerItem("infected_cow_spawn_egg",
            свойства -> new DeferredSpawnEggItem(
                PlagueEntities.INFECTED_COW, 0x3E3A36, 0x5C3A5C, свойства));

    public static final DeferredItem<DeferredSpawnEggItem> MUTATED_ZOMBIE_SPAWN_EGG =
        ПРЕДМЕТЫ.registerItem("mutated_zombie_spawn_egg",
            свойства -> new DeferredSpawnEggItem(
                PlagueEntities.MUTATED_ZOMBIE, 0x2A272F, 0x5C3A5C, свойства));

    /**
     * Отвар от чумы. Не блок, но живёт здесь же: заводить отдельный
     * DeferredRegister ради одного предмета — лишний файл на пустом месте,
     * а творческая вкладка всё равно берёт содержимое из ПРЕДМЕТЫ целиком.
     */
    public static final DeferredItem<net.minecraft.world.item.Item> PLAGUE_BREW =
        ПРЕДМЕТЫ.registerItem("plague_brew",
            свойства -> new BrewItem(свойства.stacksTo(16)));

    public static void register(IEventBus modEventBus) {
        БЛОКИ.register(modEventBus);
        ПРЕДМЕТЫ.register(modEventBus);
    }
}
