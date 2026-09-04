package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
 * Отвар: лекарство от лёгкой чумы, доступное каждому.
 *
 * Сила падает при частом питье и восстанавливается за пять минут паузы.
 * Смысл в том, чтобы отвар был расходником на вылазку, а не кнопкой
 * «выздороветь»: три глотка подряд вытаскивают с потолка второй стадии,
 * а десять подряд не дают почти ничего.
 *
 * На стадиях 3 и 4 не работает вовсе. Иначе стопка отваров заменила бы
 * Клирика, и весь класс превратился бы в украшение.
 */
public class BrewItem extends Item {

    public BrewItem(Properties свойства) {
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
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        long сейчас = мир.getGameTime();

        // Лихорадку отваром не сбить. Бутылка всё равно пропадает —
        // так игрок узнаёт правило один раз и запоминает.
        if (д.стадия > PlagueConstants.PLAYER_BREW_MAX_STAGE) {
            мир.playSound(null, игрок.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
            return;
        }

        int номер = InfectionMath.счётчикГлотков(д.глотков, д.тикПоследнегоГлотка, сейчас);
        float снимет = InfectionMath.силаОтвара(номер);

        int была = д.стадия;
        д.заражённость = Math.max(0f, д.заражённость - снимет);
        д.стадия = InfectionMath.стадия(д.заражённость);
        д.глотков = номер + 1;
        д.тикПоследнегоГлотка = сейчас;

        if (д.стадия != была) PlayerInfection.пересчитатьЗдоровье(игрок);
    }
}
