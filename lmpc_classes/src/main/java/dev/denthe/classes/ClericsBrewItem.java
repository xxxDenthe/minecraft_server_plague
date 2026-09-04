package dev.denthe.classes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Улучшенный отвар Клирика. Спек — 2026-09-04-klassy-design.md, раздел 4.
 *
 * В отличие от обычного {@code plaguecore:plague_brew} работает на
 * любой стадии — цена в том, что варить и пить его с толком может
 * только Клирик: у остальных классов бутылка просто пропадает без
 * эффекта. Гейт — на использовании, не на крафте: crafting-рецепт
 * доступен всем (give/лут не должны давать бесплатный обход), но
 * бесполезен в чужих руках.
 */
public class ClericsBrewItem extends Item {
    public ClericsBrewItem(Properties свойства) {
        super(свойства);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack стопка) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack стопка, LivingEntity кто) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        return ItemUtils.startUsingInstantly(мир, игрок, рука);
    }

    /**
     * ПКМ по игроку — это «напоить», не «выпить самому». Без этого
     * ваниль считает клик по сущности промахом и тут же откатывается
     * на обычное использование предмета в руке: Клирик, целящийся
     * в союзника, выпивал бутылку сам. {@link BrewTargeting}
     * (клиент) и {@link ClassNetwork} (сервер) — единственный путь
     * напоить кого-то этим предметом.
     */
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack стопка, Player игрок, LivingEntity цель, InteractionHand рука) {
        return цель instanceof Player ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack стопка, Level мир, LivingEntity кто) {
        if (кто instanceof ServerPlayer игрок) выпить(игрок, мир);

        if (кто instanceof Player игрок && !игрок.hasInfiniteMaterials()) {
            стопка.shrink(1);
            if (стопка.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            игрок.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
        }
        return стопка;
    }

    private static void выпить(ServerPlayer игрок, Level мир) {
        PlayerClassData д = PlayerClassData.данные(игрок);

        if (д.класс != PlayerClassData.Класс.CLERIC) {
            игрок.displayClientMessage(
                Component.literal("Только Клирик знает, как этим пользоваться."), true);
            мир.playSound(null, игрок.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
            return;
        }

        long сейчас = мир.getGameTime();
        if (сейчас < д.отварГотовТик) {
            long осталосьТиков = д.отварГотовТик - сейчас;
            игрок.displayClientMessage(Component.literal(
                "Отвар ещё не настоялся: " + (осталосьТиков / 1200 + 1) + " мин."), true);
            return;
        }

        PlagueBridge.cure(игрок, ClassesConfig.отварЛечение());
        PlagueBridge.grantImmunity(игрок, ClassesConfig.отварИммунитетТики());
        д.отварГотовТик = сейчас + ClassesConfig.отварКулдаунТики();

        мир.playSound(null, игрок.blockPosition(),
            SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}
