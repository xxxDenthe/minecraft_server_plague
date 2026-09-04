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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Улучшенный отвар Клирика. Спек — 2026-09-04-klassy-design.md, раздел 4.
 *
 * В отличие от обычного {@code plaguecore:plague_brew} работает на
 * любой стадии — цена в том, что варить и пить его с толком может
 * только Клирик: у остальных классов бутылка просто пропадает без
 * эффекта. Гейт — на использовании, не на крафте: crafting-рецепт
 * доступен всем (give/лут не должны давать бесплатный обход), но
 * бесполезен в чужих руках.
 *
 * Сам эффект — {@link #применить}, одна точка и для «выпил сам»,
 * и для «напоил союзника» ({@link ClassNetwork}). До 0.6.0 обе
 * ветки считали лечение и кулдаун своими копиями кода, и правка
 * в одной не доезжала до другой.
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
     * Подсказка в инвентаре. Класс-гейт у предмета невидимый: без
     * строчки в тултипе не-Клирик узнаёт о нём, только потеряв
     * бутылку. Второй строкой — что отвар можно не только выпить.
     */
    @Override
    public void appendHoverText(
            ItemStack стопка, TooltipContext контекст, List<Component> строки, TooltipFlag флаг) {
        строки.add(Component.translatable("tooltip.lmpc_classes.clerics_brew.gate")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        строки.add(Component.translatable("tooltip.lmpc_classes.clerics_brew.feed")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    /**
     * ПКМ по игроку — это «напоить», не «выпить самому». Без этого
     * ваниль считает клик по сущности промахом и тут же откатывается
     * на обычное использование предмета в руке: Клирик, целящийся
     * в союзника, выпивал бутылку сам. {@code client.BrewTargeting}
     * и {@link ClassNetwork} — единственный путь напоить кого-то
     * этим предметом.
     */
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack стопка, Player игрок, LivingEntity цель, InteractionHand рука) {
        return цель instanceof Player ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /**
     * Бутылку тратит только сервер и только если отвар сработал:
     * на кулдауне Клирик раньше терял её впустую, ничего не получив.
     * У не-Клирика бутылка всё так же пропадает — это не баг, а цена
     * попытки (спек, раздел 4).
     */
    @Override
    public ItemStack finishUsingItem(ItemStack стопка, Level мир, LivingEntity кто) {
        if (!(кто instanceof ServerPlayer игрок)) return стопка;
        if (!выпить(игрок)) return стопка;
        if (игрок.hasInfiniteMaterials()) return стопка;

        стопка.shrink(1);
        if (стопка.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        игрок.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
        return стопка;
    }

    /**
     * Эффект отвара на цель. Одна точка для обоих способов применения:
     * лечение и иммунитет считаются по тиру Клирика, кулдаун ставится
     * тому, кого лечат (спек, раздел 4 — счётчик про больного, а не
     * про лекаря), мастерство идёт лекарю.
     *
     * @return 0 — подействовало; иначе сколько тиков цели ещё ждать.
     */
    public static long применить(ServerPlayer клирик, ServerPlayer цель) {
        PlayerClassData дЦели = PlayerClassData.данные(цель);
        long сейчас = цель.level().getGameTime();
        if (сейчас < дЦели.отварГотовТик) return дЦели.отварГотовТик - сейчас;

        int тир = PlayerClassData.данные(клирик).тир();
        PlagueBridge.cure(цель, ClassesConfig.отварЛечение(тир));
        PlagueBridge.grantImmunity(цель, ClassesConfig.отварИммунитетТики());
        дЦели.отварГотовТик = сейчас + ClassesConfig.отварКулдаунТики(тир);
        PlayerClassData.синхронизировать(цель);

        // Вылечить другого — профильнее, чем вылечить себя: вдвое дороже по мастерству.
        int заЛечение = ClassesConfig.клирикМастерствоЗаЛечение();
        PlayerClassData.прибавитьМастерство(клирик, клирик == цель ? заЛечение : заЛечение * 2);

        цель.level().playSound(null, цель.blockPosition(),
            SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.2f);
        return 0L;
    }

    /** @return тратить ли бутылку. */
    private static boolean выпить(ServerPlayer игрок) {
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.CLERIC) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.brew.not_cleric"), true);
            игрок.level().playSound(null, игрок.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
            return true;
        }

        long осталось = применить(игрок, игрок);
        if (осталось > 0) {
            игрок.displayClientMessage(Component.translatable(
                "msg.lmpc_classes.brew.cooldown", ClassSwitch.минутОсталось(осталось)), true);
            return false;
        }
        return true;
    }
}
