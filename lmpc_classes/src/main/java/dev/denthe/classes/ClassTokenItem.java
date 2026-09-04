package dev.denthe.classes;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Путёвка класса. Спек — 2026-09-04-klassy-design.md, раздел 2.
 *
 * Право-клик расходует предмет и ставит игроку класс, если не идёт
 * кулдаун смены. Крафт (донорский материал класса + cleansing_agent
 * из `plaguecore`) не заведён — реагент ещё не существует в игре.
 * Пока получить путёвку можно из творческого режима или команды
 * {@code /lmpcclasses class}.
 */
public class ClassTokenItem extends Item {
    private final PlayerClassData.Класс класс;

    public ClassTokenItem(PlayerClassData.Класс класс, Properties свойства) {
        super(свойства);
        this.класс = класс;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        ItemStack предмет = игрок.getItemInHand(рука);
        if (мир.isClientSide()) return InteractionResultHolder.success(предмет);

        long кулдаунТики = ClassesConfig.кулдаунСменыТики();
        PlayerClassData д = PlayerClassData.данные(игрок);

        if (!ClassSwitch.можноСменить(д.последняяСменаТик, мир.getGameTime(), кулдаунТики)) {
            long осталосьТиков = кулдаунТики - (мир.getGameTime() - д.последняяСменаТик);
            игрок.displayClientMessage(Component.literal(
                "Класс можно сменить через " + (осталосьТиков / 1200 + 1) + " мин."), true);
            return InteractionResultHolder.fail(предмет);
        }

        д.класс = класс;
        д.последняяСменаТик = мир.getGameTime();
        if (!игрок.getAbilities().instabuild) предмет.shrink(1);
        игрок.displayClientMessage(Component.literal("Класс: " + класс), true);
        return InteractionResultHolder.success(предмет);
    }
}
