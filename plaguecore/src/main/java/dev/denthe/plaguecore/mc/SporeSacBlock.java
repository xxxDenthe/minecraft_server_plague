package dev.denthe.plaguecore.mc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Споровый мешок: бугор чумной плоти на полу пещеры. Ядро, раздел 8.4.
 *
 * Модель нарисована владельцем в Blockbench — четырнадцать кубиков,
 * основание на всю клетку и нарост со шляпкой сверху. Форма столкновений
 * повторяет её грубо, тремя коробками: точная развёртка из четырнадцати
 * кусков ничего не даёт игроку, но считается каждый шаг.
 *
 * Мешок держится за пол. Выбили опору — осыпался: висящий в воздухе
 * бугор выглядит поломкой, а не чумой.
 */
public class SporeSacBlock extends Block {

    public static final MapCodec<SporeSacBlock> CODEC = simpleCodec(SporeSacBlock::new);

    /**
     * Силуэт из трёх коробок: подушка на полу, сам бугор и шляпка.
     * Через подушку игрок переступает, в бугор упирается.
     */
    private static final VoxelShape ФОРМА = Shapes.or(
        Block.box(0.0,  0.0, 0.0, 16.0,  3.0, 16.0),   // основание
        Block.box(1.0,  3.0, 2.0, 14.0, 13.0, 15.0),   // бугор
        Block.box(4.0, 11.0, 4.0, 10.0, 16.0, 10.0));  // шляпка

    /** Раз в сколько тиков в среднем срывается одна пылинка. */
    private static final int РЕДКОСТЬ_ПАРТИКЛОВ = 12;

    public SporeSacBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MapCodec<SporeSacBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ФОРМА;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos снизу = pos.below();
        return level.getBlockState(снизу).isFaceSturdy(level, снизу, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Мешок тихо пылит. Метод чисто клиентский и вызывается только для
     * блоков рядом с камерой, поэтому цена нулевая.
     *
     * Пепел выбран вместо споровых частиц ванили намеренно: он тёмно-серый
     * и держит палитру чумы, а варпово-багровые споры цветные.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(РЕДКОСТЬ_ПАРТИКЛОВ) != 0) return;

        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
        double y = pos.getY() + 0.6 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;

        // Пылинка почти стоит на месте и медленно оседает: споры не бьют
        // фонтаном, а висят в воздухе.
        level.addParticle(ParticleTypes.ASH, x, y, z,
            (random.nextDouble() - 0.5) * 0.01, -0.005, (random.nextDouble() - 0.5) * 0.01);
    }
}
