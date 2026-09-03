package dev.denthe.plaguecore.mc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Заражённая травинка: то, во что превращается обычный пучок травы.
 *
 * Ванильный TallGrassBlock не подошёл по двум причинам. Он умеет
 * костную муку и вырастает в ванильную высокую траву — то есть чуму
 * можно было бы «вылечить» удобрением. И держится он только на блоках
 * из тега земли, а гнилой дёрн в этот тег не входит: травинка осыпалась
 * бы в тот же миг, как её поставили.
 *
 * Добавлять наши блоки в ванильный тег земли было бы дешевле на файл,
 * но тег читают и трава, и деревья, и кости — побочных действий больше,
 * чем пользы.
 */
public class BlightedGrassBlock extends Block {

    public static final MapCodec<BlightedGrassBlock> CODEC = simpleCodec(BlightedGrassBlock::new);

    /** Пучок ниже полного блока и уже его: сквозь него ходят. */
    private static final VoxelShape ФОРМА = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public BlightedGrassBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MapCodec<BlightedGrassBlock> codec() {
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
}
