package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Алтарь призвания. Спек — 2026-09-04-klassy-design.md, раздел 2.
 *
 * Правый клик открывает экран выбора класса ({@link
 * dev.denthe.classes.client.ClassAltarScreen}). Своего меню-контейнера
 * нет — кнопки экрана шлют команду {@code /lmpcclasses choose}, тем же
 * приёмом, что панель `lmpc_gmtools` шлёт ванильные команды.
 */
public class ClassAltarBlock extends Block {

    /**
     * Куда алтарь смотрит лицом. Ванильное свойство, а не своё: модель
     * несимметрична (заспинник сзади, чаша и свечи спереди), и ставить
     * её всегда на север значило бы, что игрок подстраивает себя
     * под блок.
     */
    public static final DirectionProperty СТОРОНА = BlockStateProperties.HORIZONTAL_FACING;

    /** Фитиль западной свечи в долях блока при {@code facing=north}. */
    private static final double СВЕЧА_Z = 7.0 / 16;
    private static final double СВЕЧА_ЗАПАД_X = 3.0 / 16;
    private static final double СВЕЧА_ВОСТОК_X = 13.0 / 16;
    /** Верх свечи по модели — 20.5 из 16, алтарь выше блока. */
    private static final double ВЫСОТА_ФИТИЛЯ = 20.5 / 16;

    public ClassAltarBlock(BlockBehaviour.Properties свойства) {
        super(свойства);
        registerDefaultState(stateDefinition.any().setValue(СТОРОНА, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> строитель) {
        строитель.add(СТОРОНА);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext контекст) {
        // Лицом к тому, кто ставит, — как у печки и наковальни.
        return defaultBlockState()
            .setValue(СТОРОНА, контекст.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState состояние, Rotation поворот) {
        return состояние.setValue(СТОРОНА, поворот.rotate(состояние.getValue(СТОРОНА)));
    }

    @Override
    protected BlockState mirror(BlockState состояние, Mirror зеркало) {
        return состояние.rotate(зеркало.getRotation(состояние.getValue(СТОРОНА)));
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState состояние, Level мир, BlockPos позиция, Player игрок, BlockHitResult попадание) {
        if (мир.isClientSide()) {
            dev.denthe.classes.client.ClassAltarScreen.открыть();
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Огоньки на свечах. Раньше пламя было нарисовано в модели двумя
     * крестами — плоские, всегда одинаковые и не светятся. Партикл
     * {@code SMALL_FLAME} тот же самый, что у ванильной свечи: он живой
     * и виден со всех сторон, а лишних граней в модели не держит.
     */
    @Override
    public void animateTick(BlockState состояние, Level мир, BlockPos позиция, RandomSource случай) {
        огонёк(мир, позиция, состояние.getValue(СТОРОНА), СВЕЧА_ЗАПАД_X, случай);
        огонёк(мир, позиция, состояние.getValue(СТОРОНА), СВЕЧА_ВОСТОК_X, случай);
    }

    private static void огонёк(
            Level мир, BlockPos позиция, Direction сторона, double x, RandomSource случай) {
        // Смещения заданы для facing=north и доворачиваются так же,
        // как blockstate доворачивает саму модель.
        double dx = x - 0.5;
        double dz = СВЕЧА_Z - 0.5;
        switch (сторона) {
            case EAST -> { double t = dx; dx = -dz; dz = t; }
            case SOUTH -> { dx = -dx; dz = -dz; }
            case WEST -> { double t = dx; dx = dz; dz = -t; }
            default -> { }
        }
        мир.addParticle(
            ParticleTypes.SMALL_FLAME,
            позиция.getX() + 0.5 + dx,
            позиция.getY() + ВЫСОТА_ФИТИЛЯ,
            позиция.getZ() + 0.5 + dz,
            0, 0, 0);
        if (случай.nextInt(8) == 0) {
            мир.addParticle(
                ParticleTypes.SMOKE,
                позиция.getX() + 0.5 + dx,
                позиция.getY() + ВЫСОТА_ФИТИЛЯ + 0.1,
                позиция.getZ() + 0.5 + dz,
                0, 0.01, 0);
        }
    }
}
