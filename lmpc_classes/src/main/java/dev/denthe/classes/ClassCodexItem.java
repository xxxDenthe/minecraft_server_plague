package dev.denthe.classes;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Гримуар: личный «дневник призвания». Спек, раздел 4–7 (описания
 * классов). Открывается где угодно, не только у Алтаря — Алтарь
 * это ритуал смены класса, гримуар — то, что носишь с собой.
 * Выдаётся автоматически при первом выборе класса
 * ({@link ClassCommands}), крафта нет.
 */
public class ClassCodexItem extends Item {
    public ClassCodexItem(Properties свойства) {
        super(свойства);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        ItemStack стопка = игрок.getItemInHand(рука);
        if (мир.isClientSide()) {
            dev.denthe.classes.client.ClassCodexScreen.открыть(PlayerClassData.данные(игрок).класс);
        }
        return InteractionResultHolder.success(стопка);
    }
}
