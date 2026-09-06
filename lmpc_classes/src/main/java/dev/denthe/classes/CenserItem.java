package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Курильница — ручная очистка подземелья, спек ядра 10.3.
 *
 * <p><b>Почему предмет, а не машина.</b> Очиститель требует вращения,
 * то есть Create, и стоит он на поверхности. В шахте на глубине ста
 * блоков вала нет и не будет, а чума под землёй есть. Курильница —
 * ответ для того, кто в Create не полез совсем: её носят в руке,
 * ей распыляют реагент прямо там, где копают.
 *
 * <p><b>Три ограничения, которые делают её «дешёвой, ручной
 * и временной», как и хотел спек.</b>
 * <ul>
 *   <li>Под открытым небом не работает. Наверху для этого есть машина,
 *       и подменять её ручным предметом нельзя — иначе очиститель
 *       никому не нужен.</li>
 *   <li>Главная польза — сопротивление чанка, а оно само тает.
 *       Защита шахты держится, только пока в шахту ходят.</li>
 *   <li>Пауза между распылениями: без неё стопка реагента ушла бы
 *       в один чанк за минуту.</li>
 * </ul>
 *
 * <p>Класса курильница не спрашивает намеренно: это тот самый
 * «уровень, доступный без класса» из спека классов 2.1.
 */
public class CenserItem extends Item {

    /** Сколько тиков держать ПКМ. Полторы секунды: это жест, а не щелчок. */
    private static final int ДЛИТЕЛЬНОСТЬ = 30;

    public CenserItem(Properties свойства) {
        super(свойства);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack стопка) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack стопка, LivingEntity кто) {
        return ДЛИТЕЛЬНОСТЬ;
    }

    /**
     * Все отказы разбираются здесь, до начала распыления: игрок должен
     * узнать причину сразу, а не продержать ПКМ полторы секунды впустую.
     * Ровно та ошибка, которую в 0.6.0 чинили отвару Клирика.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        ItemStack стопка = игрок.getItemInHand(рука);

        if (мир.canSeeSky(игрок.blockPosition())) {
            if (!мир.isClientSide()) {
                игрок.displayClientMessage(
                    Component.translatable("msg.lmpc_classes.censer.open_sky"), true);
            }
            return InteractionResultHolder.fail(стопка);
        }
        if (!найтиРеагент(игрок).isEmpty() || игрок.getAbilities().instabuild) {
            игрок.startUsingItem(рука);
            return InteractionResultHolder.consume(стопка);
        }
        if (!мир.isClientSide()) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.censer.no_reagent"), true);
        }
        return InteractionResultHolder.fail(стопка);
    }

    /** Дымок всё время, пока держат ПКМ, — чтобы жест был виден со стороны. */
    @Override
    public void onUseTick(Level мир, LivingEntity кто, ItemStack стопка, int осталось) {
        if (!(мир instanceof ServerLevel уровень) || осталось % 4 != 0) return;
        уровень.sendParticles(ParticleTypes.CLOUD,
            кто.getX(), кто.getEyeY() - 0.2, кто.getZ(),
            2, 0.25, 0.1, 0.25, 0.01);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack стопка, Level мир, LivingEntity кто) {
        if (!(мир instanceof ServerLevel уровень) || !(кто instanceof Player игрок)) return стопка;

        BlockPos позиция = игрок.blockPosition();
        boolean снизился = PlagueBridge.очиститьЧанк(уровень,
            позиция.getX() >> 4, позиция.getZ() >> 4,
            ClassesConfig.курильницаСила(), ClassesConfig.курильницаСопротивление());

        if (!игрок.getAbilities().instabuild) {
            ItemStack реагент = найтиРеагент(игрок);
            if (!реагент.isEmpty()) реагент.shrink(1);
            стопка.hurtAndBreak(1, игрок, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        игрок.getCooldowns().addCooldown(this, ClassesConfig.курильницаПерезарядка());

        уровень.sendParticles(ParticleTypes.CLOUD,
            позиция.getX() + 0.5, позиция.getY() + 1.0, позиция.getZ() + 0.5,
            30, 1.2, 0.5, 1.2, 0.02);
        уровень.playSound(null, позиция, SoundEvents.FIRE_EXTINGUISH,
            SoundSource.PLAYERS, 0.6f, 1.4f);
        игрок.displayClientMessage(Component.translatable(снизился
            ? "msg.lmpc_classes.censer.cleansed"
            : "msg.lmpc_classes.censer.held"), true);
        return стопка;
    }

    /** Первый попавшийся реагент в инвентаре; пустая стопка — реагента нет. */
    private static ItemStack найтиРеагент(Player игрок) {
        for (ItemStack стопка : игрок.getInventory().items) {
            if (стопка.is(ClassItems.CLEANSING_AGENT.get())) return стопка;
        }
        return ItemStack.EMPTY;
    }
}
