package dev.denthe.gmtools.net;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Снимок инвентаря игрока — оператору, вызвавшему `/gmtools inv`. */
public final class GmInventory {
    private GmInventory() {}

    static int view(CommandSourceStack src, ServerPlayer target) {
        if (!(src.getEntity() instanceof ServerPlayer op)) {
            src.sendFailure(Component.literal("Только от лица игрока"));
            return 0;
        }
        Inventory inv = target.getInventory();
        List<ItemStack> slots = new ArrayList<>(41);
        for (int i = 0; i < 36; i++) slots.add(inv.items.get(i).copy());
        for (int i = 0; i < 4; i++) slots.add(inv.armor.get(i).copy());
        slots.add(inv.offhand.get(0).copy());

        PacketDistributor.sendToPlayer(op,
            new GmNetwork.Inventory(target.getGameProfile().getName(), slots));
        return 1;
    }
}
