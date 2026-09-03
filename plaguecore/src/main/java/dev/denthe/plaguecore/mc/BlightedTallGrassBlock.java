package dev.denthe.plaguecore.mc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Заражённая высокая трава: то, во что превращаются ванильная высокая
 * трава и большой папоротник.
 *
 * Долго не трогали именно её: блок из двух половин, а мы ставим блоки
 * без обновления соседей, и подмена нижней оставила бы верхнюю висеть.
 * Решение оказалось проще, чем казалось: половины не связаны при
 * подмене вовсе. Каждая меняется сама на себя, только с сохранением
 * своего значения HALF, а лежат они в одном столбце подряд — проход
 * берёт обе за один заход.
 *
 * От ванильного DoublePlantBlock отличается только опорой, ровно как
 * BlightedGrassBlock от ванильного пучка: гнилой дёрн не входит в тег
 * земли, и на нём ванильная проверка осыпала бы траву в тот же миг.
 */
public class BlightedTallGrassBlock extends DoublePlantBlock {

    public static final MapCodec<BlightedTallGrassBlock> CODEC =
        simpleCodec(BlightedTallGrassBlock::new);

    public BlightedTallGrassBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MapCodec<BlightedTallGrassBlock> codec() {
        return CODEC;
    }

    /**
     * Верхняя половина держится за нижнюю, нижняя — за любой твёрдый
     * блок под собой. Тег земли не спрашиваем: чума растёт и на гнили.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState низ = level.getBlockState(pos.below());
            return низ.is(this) && низ.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        BlockPos снизу = pos.below();
        return level.getBlockState(снизу).isFaceSturdy(level, снизу, Direction.UP);
    }
}
