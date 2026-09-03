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
 * Гнилая лоза: свисает вниз с потолка или с такой же лозы над собой.
 *
 * Держится только сверху. Убрали опору — вся плеть осыпается сама,
 * потому что каждый следующий сегмент теряет свою опору следом.
 */
public class BlightVineBlock extends Block {

    public static final MapCodec<BlightVineBlock> CODEC = simpleCodec(BlightVineBlock::new);

    /** Узкий столбик по центру: сквозь лозу можно пройти, но она видна. */
    private static final VoxelShape ФОРМА = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public BlightVineBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MapCodec<BlightVineBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return ФОРМА;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos сверху = pos.above();
        BlockState опора = level.getBlockState(сверху);
        return опора.is(this) || опора.isFaceSturdy(level, сверху, Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
