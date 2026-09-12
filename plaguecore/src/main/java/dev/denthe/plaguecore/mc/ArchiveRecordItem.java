package dev.denthe.plaguecore.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Запись архива: носитель тайнописи. Спек «Запись как предмет», 2026-09-12.
 *
 * Начинка — ровно та же, что у подписанной книги: {@code pages}, {@code title},
 * {@code author} лежат в компоненте {@link DataComponents#WRITTEN_BOOK_CONTENT}.
 * Это главное решение замысла: экран чтения берёт страницы со стека через
 * {@code BookViewScreen.BookAccess.fromItem}, который смотрит на компонент и
 * не спрашивает, что за предмет. Значит врезка тайнописи
 * ({@code BookAccessMixin}) работает по записи без единой правки.
 *
 * Свой предмет нужен ради трёх вещей, которых у подписанной книги нет:
 * три вида по архивам через {@code CustomModelData}, невозможность
 * подделать запись пером и книгой, и собственный путь открытия экрана —
 * ванильный {@code ServerPlayer.openItemGui} зашит на {@code WRITTEN_BOOK}.
 */
public class ArchiveRecordItem extends Item {

    public ArchiveRecordItem(Properties свойства) {
        super(свойства);
    }

    /** Название берётся из заголовка записи, как у подписанной книги. */
    @Override
    public Component getName(ItemStack стопка) {
        WrittenBookContent начинка = стопка.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (начинка != null && !начинка.title().raw().isBlank()) {
            return Component.literal(начинка.title().raw());
        }
        return super.getName(стопка);
    }

    /** Под названием — чья рука. Больше в подсказке ничего: это бумага, не карточка предмета. */
    @Override
    public void appendHoverText(ItemStack стопка, TooltipContext контекст,
                                List<Component> строки, TooltipFlag флаг) {
        WrittenBookContent начинка = стопка.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (начинка != null && !начинка.author().isBlank()) {
            строки.add(Component.literal(начинка.author())
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Правый клик открывает экран чтения прямо на клиенте.
     *
     * Сервер здесь не участвует намеренно: содержимое уже лежит в стеке,
     * а ванильный путь через {@code openItemGui} принимает только
     * {@code Items.WRITTEN_BOOK} и по нашей записи промолчал бы.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        ItemStack стопка = игрок.getItemInHand(рука);
        if (мир.isClientSide) {
            dev.denthe.plaguecore.client.ArchiveRecordScreen.открыть(стопка);
        }
        return InteractionResultHolder.sidedSuccess(стопка, мир.isClientSide);
    }
}
