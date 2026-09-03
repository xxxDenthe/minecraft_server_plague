package dev.denthe.gmtools.client;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Набор иконок для меток. Индекс идёт в пакете, предметы — только на клиенте. */
public final class MarkerIcons {
    private MarkerIcons() {}

    private static final Item[] ICONS = {
        Items.WHITE_BANNER, Items.TORCH, Items.RED_BED, Items.SKELETON_SKULL,
        Items.DIAMOND, Items.EMERALD, Items.IRON_SWORD, Items.CHEST,
        Items.OAK_DOOR, Items.BELL, Items.TNT, Items.ENDER_EYE,
        Items.CAMPFIRE, Items.CRAFTING_TABLE, Items.BOOKSHELF, Items.LANTERN,
    };

    public static int count() {
        return ICONS.length;
    }

    public static ItemStack stack(int i) {
        return new ItemStack(ICONS[Math.floorMod(i, ICONS.length)]);
    }
}
