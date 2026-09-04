package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
    public ClassAltarBlock(BlockBehaviour.Properties свойства) {
        super(свойства);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState состояние, Level мир, BlockPos позиция, Player игрок, BlockHitResult попадание) {
        if (мир.isClientSide()) {
            dev.denthe.classes.client.ClassAltarScreen.открыть();
        }
        return InteractionResult.SUCCESS;
    }
}
