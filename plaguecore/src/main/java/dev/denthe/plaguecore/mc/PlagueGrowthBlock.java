package dev.denthe.plaguecore.mc;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Нарост чумы: плёнка на любой грани, как лишайник.
 *
 * Шесть булевых свойств сторон заводит сам MultifaceBlock, нам остаётся
 * отдать кодек и распространитель — оба метода в базовом классе
 * абстрактные. Подводность (WATERLOGGED) в базовый класс не входит
 * и нам не нужна.
 *
 * Сам по себе нарост не расползается: распространение решает сетка чумы,
 * а не блок. Спредер нужен только затем, чтобы ванильный код подбора
 * граней при установке отработал штатно.
 */
public class PlagueGrowthBlock extends MultifaceBlock {

    public static final MapCodec<PlagueGrowthBlock> CODEC = simpleCodec(PlagueGrowthBlock::new);

    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public PlagueGrowthBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MapCodec<PlagueGrowthBlock> codec() {
        return CODEC;
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }
}
