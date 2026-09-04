package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.SurfaceRule;
import dev.denthe.plaguecore.core.SurfaceRule.BlockKind;
import dev.denthe.plaguecore.core.SurfaceRule.PlagueAction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.Tags;

/**
 * Переводчик между чистым правилом и блоками Minecraft. Дизайн
 * материализации, раздел 4.2.
 *
 * Вся таблица «было → стало» живёт в core/SurfaceRule и тестируется без
 * игры. Здесь только два перевода: BlockState → вид блока и действие →
 * новый BlockState.
 *
 * Различаем два рода изменений:
 *   замена  — блок становится другим блоком на том же месте;
 *   нарост  — плёнка кладётся на соседний воздух, сам блок цел.
 */
public final class BlockTransforms {
    private BlockTransforms() {}

    /** Чем стал блок, или null — если менять нечего. */
    public static BlockState replacement(BlockState было, int level) {
        PlagueAction действие = SurfaceRule.actionFor(kindOf(было), level);
        if (действие.isNothing() || действие.isCoating()) return null;

        BlockState стало = поДействию(действие, было);
        // Заражённая листва возвращает вид LEAVES и потому приходит сюда
        // каждый проход. Чтобы не переставлять её впустую, гасим замену
        // блока самим собой.
        return (стало == null || стало.equals(было)) ? null : стало;
    }

    private static BlockState поДействию(PlagueAction действие, BlockState было) {
        return switch (действие) {
            case PODZOL        -> Blocks.PODZOL.defaultBlockState();
            case ROTTED_GRASS  -> PlagueBlocks.ROTTED_GRASS.get().defaultBlockState();
            case ROTTED_DIRT   -> PlagueBlocks.ROTTED_DIRT.get().defaultBlockState();
            case ROTTED_STONE  -> PlagueBlocks.ROTTED_STONE.get().defaultBlockState();
            case ROTTED_LOG    -> сгнитьСтвол(было);
            case BLIGHTED_GRASS -> PlagueBlocks.BLIGHTED_GRASS.get().defaultBlockState();
            case BLIGHTED_TALL_GRASS -> высокаяТрава(было);
            case DESTROY_PLANT -> Blocks.AIR.defaultBlockState();
            case BLIGHTED_LEAVES -> PlagueBlocks.BLIGHTED_LEAVES.get().defaultBlockState()
                                       .setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
            case WITHERED_LEAVES -> PlagueBlocks.WITHERED_LEAVES.get().defaultBlockState()
                                       .setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
            case DESTROY_CROP  -> Blocks.AIR.defaultBlockState();
            case TRAMPLE_CROP  -> вытоптать(было);
            default            -> null;
        };
    }

    /** Нужно ли обрастить этот блок наростом на соседнем воздухе. */
    public static boolean needsCoating(BlockState было, int level) {
        return SurfaceRule.actionFor(kindOf(было), level).isCoating();
    }

    public static BlockState coating() {
        return PlagueBlocks.PLAGUE_GROWTH.get().defaultBlockState();
    }

    /**
     * Половину высокой травы меняем на свою такую же половину.
     *
     * Ключ ко всему блоку: половины не связываем и соседей не трогаем.
     * Верхняя и нижняя лежат в одном столбце подряд, проход берёт их
     * за один заход, и в промежутке между двумя setBlock никто не успеет
     * заметить, что половины разного рода.
     */
    private static BlockState высокаяТрава(BlockState было) {
        BlockState стало = PlagueBlocks.BLIGHTED_TALL_GRASS.get().defaultBlockState();
        return было.hasProperty(DoublePlantBlock.HALF)
            ? стало.setValue(DoublePlantBlock.HALF, было.getValue(DoublePlantBlock.HALF))
            : стало;
    }

    /** Ось бревна сохраняем: лежачие брёвны иначе встали бы торчком. */
    private static BlockState сгнитьСтвол(BlockState было) {
        BlockState стало = PlagueBlocks.ROTTED_LOG.get().defaultBlockState();
        return было.hasProperty(BlockStateProperties.AXIS)
            ? стало.setValue(BlockStateProperties.AXIS, было.getValue(BlockStateProperties.AXIS))
            : стало;
    }

    /**
     * Вытоптанные посевы — это посевы, отброшенные в нулевой возраст.
     * Урожай не пропадает совсем, но и не поспевает.
     */
    private static BlockState вытоптать(BlockState было) {
        if (!(было.getBlock() instanceof CropBlock crop)) return null;
        IntegerProperty возраст = BlockStateProperties.AGE_7;
        if (!было.hasProperty(возраст)) return null;
        if (было.getValue(возраст) == 0) return null; // и так вытоптаны
        return было.setValue(возраст, 0);
    }

    /** Во что чума ставит блок. Порядок проверок важен: тег земли ловит грядку. */
    public static BlockKind kindOf(BlockState state) {
        Block block = state.getBlock();
        if (state.isAir()) return BlockKind.OTHER;
        if (block == Blocks.GRASS_BLOCK) return BlockKind.GRASS;
        if (state.is(BlockTags.CROPS)) return BlockKind.CROP;
        // Одноблочная мелочь.
        if (block == Blocks.SHORT_GRASS || block == Blocks.FERN
            || block == PlagueBlocks.BLIGHTED_GRASS.get()) return BlockKind.PLANT;
        // Двухблочная. Половины меняются каждая сама по себе, с сохранением
        // своего HALF: связывать их не нужно, они лежат в одном столбце.
        if (block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN
            || block == PlagueBlocks.BLIGHTED_TALL_GRASS.get()) return BlockKind.TALL_PLANT;
        // Цветы — оба тега сразу, и одиночные, и высокие в две половины.
        // Тег #flowers брать нельзя: в нём цветущая азалия, а это листва.
        if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) {
            return BlockKind.FLOWER;
        }
        if (state.is(BlockTags.LEAVES) || block == PlagueBlocks.BLIGHTED_LEAVES.get()
            || block == PlagueBlocks.WITHERED_LEAVES.get()) {
            return BlockKind.LEAVES;
        }
        // Ствол и доска разошлись: живое дерево гниёт целиком, а доска
        // почти всегда чья-то постройка и только обрастает.
        if (state.is(BlockTags.LOGS) || block == PlagueBlocks.ROTTED_LOG.get()) {
            return BlockKind.LOG;
        }
        if (state.is(BlockTags.PLANKS)) return BlockKind.PLANKS;
        if (state.is(BlockTags.DIRT) || block == Blocks.FARMLAND) return BlockKind.DIRT;
        if (state.is(Tags.Blocks.STONES) || state.is(Tags.Blocks.ORES)
            || state.is(BlockTags.BASE_STONE_OVERWORLD)) return BlockKind.STONE;
        if (!state.getFluidState().isEmpty()) return BlockKind.WATER;
        return BlockKind.OTHER;
    }

    /**
     * Часть ли это дерева: ствол или листва, живые или уже сгнившие.
     *
     * Нужно поверхностному проходу: столбец с деревом начинается высоко
     * над землёй, и дерево надо пройти целиком, прежде чем считать глубину.
     * Доски сюда не входят намеренно — крыша дома не крона.
     */
    public static boolean isWood(BlockState state) {
        BlockKind вид = kindOf(state);
        return вид == BlockKind.LOG || вид == BlockKind.LEAVES;
    }

    /** Наш ли это блок — чтобы не перерисовывать уже сгнившее. */
    public static boolean isPlagueBlock(BlockState state) {
        return state.is(PlagueBlocks.ROTTED_DIRT.get())
            || state.is(PlagueBlocks.ROTTED_GRASS.get())
            || state.is(PlagueBlocks.ROTTED_STONE.get())
            || state.is(PlagueBlocks.ROTTED_LOG.get())
            || state.is(PlagueBlocks.BLIGHTED_GRASS.get())
            || state.is(PlagueBlocks.BLIGHTED_TALL_GRASS.get())
            || state.is(PlagueBlocks.PLAGUE_GROWTH.get())
            || state.is(PlagueBlocks.BLIGHT_VINE.get())
            || state.is(PlagueBlocks.SPORE_SAC.get());
    }

    // ── подземелье ────────────────────────────────────────────────────
    // Ядро, раздел 8.4. Геометрию («стена», «пол», «потолок») считает
    // CaveMaterializer: она зависит от соседей, а не от самого блока.
    // Здесь только вопросы к одному BlockState.

    /** Рудная жила: на Гнили покрывается коркой целиком. */
    public static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES);
    }

    /**
     * Может ли чума вообще работать с этим блоком под землёй.
     *
     * Список нарочно узкий: природный камень, земля и стволы. Гнить
     * и обрастать по всему, что подвернулось, нельзя — под землёй стоят
     * механизмы Create, сундуки и постройки игроков, и превращать их
     * в грязь мы не подписывались.
     */
    public static boolean isCaveSubstrate(BlockState state) {
        if (state.isAir() || isPlagueBlock(state)) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        if (!state.getFluidState().isEmpty()) return false;
        BlockKind вид = kindOf(state);
        return вид == BlockKind.STONE || вид == BlockKind.DIRT
            || вид == BlockKind.GRASS || вид == BlockKind.LOG;
    }

    /** Годится ли блок под гнилой пол: только то, по чему ходят. */
    public static boolean isCaveFloorMaterial(BlockState state) {
        BlockKind вид = kindOf(state);
        return вид == BlockKind.STONE || вид == BlockKind.DIRT || вид == BlockKind.GRASS;
    }

    public static BlockState rottedDirt() {
        return PlagueBlocks.ROTTED_DIRT.get().defaultBlockState();
    }

    public static BlockState vine() {
        return PlagueBlocks.BLIGHT_VINE.get().defaultBlockState();
    }

    public static BlockState sporeSac() {
        return PlagueBlocks.SPORE_SAC.get().defaultBlockState();
    }
}
