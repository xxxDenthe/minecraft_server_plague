package dev.denthe.classes;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * Бутон чумы: одновременно сырьё для реагента и семя грядки.
 *
 * Отдельного предмета-семечка нет намеренно. По спеку (раздел 6) бутон
 * добывается диким кустом в Гнили, а Фермер сажает «то же самое»
 * у себя на грядке — заводить второй предмет ради этого значило бы
 * объяснять игроку разницу, которой в лоре нет.
 *
 * **Сажать может только Фермер.** Это и есть его эксклюзив: гейт стоит
 * на посадке, а не на крафте — тем же приёмом, что у отвара Клирика
 * («гейт на использовании, не на крафте»). Бутон в чужих руках
 * остаётся полноценным сырьём, просто не всходит.
 */
public class PlagueBloomSeedItem extends ItemNameBlockItem {

    public PlagueBloomSeedItem(Block грядка, Properties свойства) {
        super(грядка, свойства);
    }

    @Override
    public InteractionResult useOn(UseOnContext контекст) {
        Player игрок = контекст.getPlayer();
        if (игрок != null && PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.FARMER) {
            // Сообщение в надписи над хотбаром, а не в чат: отказ мелкий
            // и повторяемый, засорять им переписку партии незачем.
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.bloom.not_farmer"), true);
            return InteractionResult.FAIL;
        }
        return super.useOn(контекст);
    }
}
