package dev.denthe.classes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Андезитовый очиститель поверхности. Спек ядра, раздел 10.1.
 *
 * Блок нарочно без меню-контейнера: реагент закладывается правым
 * кликом с реагентом в руке, состояние читается правым кликом пустой
 * рукой. Экран здесь ничего бы не добавил — внутри один слот, и ради
 * него заводить меню, сеть и клиентскую половину незачем.
 *
 * Работу делает {@link PurifierBlockEntity}; тут только взаимодействие
 * и возврат содержимого при сломе.
 */
public class PurifierBlock extends BaseEntityBlock {

    public static final MapCodec<PurifierBlock> CODEC = simpleCodec(PurifierBlock::new);

    public PurifierBlock(Properties свойства) {
        super(свойства);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos позиция, BlockState состояние) {
        return new PurifierBlockEntity(позиция, состояние);
    }

    @Override
    protected RenderShape getRenderShape(BlockState состояние) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level мир, BlockState состояние, BlockEntityType<T> тип) {
        return мир.isClientSide() ? null
            : createTickerHelper(тип, ClassBlockEntities.PURIFIER.get(), PurifierBlockEntity::тик);
    }

    /** Реагент в руке — заложить внутрь; всё прочее пропускаем дальше. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack стопка, BlockState состояние, Level мир, BlockPos позиция,
            Player игрок, InteractionHand рука, BlockHitResult попадание) {
        if (!стопка.is(ClassItems.CLEANSING_AGENT.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (мир.isClientSide()) return ItemInteractionResult.SUCCESS;

        if (!(мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель)) {
            return ItemInteractionResult.FAIL;
        }
        int взято = очиститель.принятьРеагент(стопка);
        игрок.displayClientMessage(очиститель.состояние(мир, позиция), true);
        return взято > 0 ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
    }

    /** Пустая рука — доклад о состоянии: есть ли вращение, реагент и Кузнец в партии. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState состояние, Level мир, BlockPos позиция, Player игрок, BlockHitResult попадание) {
        if (!мир.isClientSide() && мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель) {
            игрок.displayClientMessage(очиститель.состояние(мир, позиция), false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Реагент внутри — не расходник блока, а вложение партии. Тихо
     * съедать его при сломе значило бы наказывать за перестройку
     * обороны, чего спек как раз не хочет: оборону положено двигать
     * вперёд, а не ставить один раз навсегда.
     */
    @Override
    protected void onRemove(
            BlockState состояние, Level мир, BlockPos позиция, BlockState новое, boolean двигали) {
        if (!состояние.is(новое.getBlock())
                && мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель) {
            Block.popResource(мир, позиция, очиститель.вынутьВсё());
        }
        super.onRemove(состояние, мир, позиция, новое, двигали);
    }
}
