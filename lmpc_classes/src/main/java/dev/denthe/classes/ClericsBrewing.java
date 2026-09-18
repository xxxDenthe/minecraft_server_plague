package dev.denthe.classes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Отвар Клирика варится в стойке, а не собирается на верстаке.
 *
 * Внизу — обычный Отвар от чумы, сверху — Чумоцвет. Цветок растёт
 * на грядке, так что аптека Клирика получается возобновляемой,
 * а не упирается в дроп с мобов.
 *
 * <b>Почему тут есть свой код, хотя рецепт зарегистрирован.</b>
 * Ванильная стойка переваривает все три бутылки за один реагент.
 * По решению владельца одного цветка хватает ровно на
 * {@link #ПОРЦИЙ_ЗА_ЦВЕТОК} порции, поэтому саму варку мы делаем
 * руками в {@link PotionBrewEvent.Pre} и отменяем ванильную. Рецепт
 * в реестре всё равно нужен: без него стойка не считает связку
 * «отвар + цветок» варибельной и просто не запустится, да и JEI
 * показывает игроку именно его.
 *
 * Отменённое событие не тратит реагент за нас — цветок убавляем сами.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClericsBrewing {
    private ClericsBrewing() {}

    /** Сколько бутылок стойка успевает довести одним цветком. */
    private static final int ПОРЦИЙ_ЗА_ЦВЕТОК = 2;

    /** Слот реагента в стойке; 0..2 — бутылки, 4 — топливо. */
    private static final int СЛОТ_РЕАГЕНТА = 3;

    private static final ResourceLocation ЧУМНОЙ_ОТВАР =
        ResourceLocation.fromNamespaceAndPath("plaguecore", "plague_brew");

    @SubscribeEvent
    public static void рецепты(RegisterBrewingRecipesEvent событие) {
        Item основа = BuiltInRegistries.ITEM.get(ЧУМНОЙ_ОТВАР);
        if (основа == Items.AIR) return;
        событие.getBuilder().addRecipe(
            Ingredient.of(основа),
            Ingredient.of(ClassItems.PLAGUE_BLOOM.get()),
            new ItemStack(ClassItems.CLERICS_BREW.get()));
    }

    @SubscribeEvent
    public static void сварить(PotionBrewEvent.Pre событие) {
        if (!событие.getItem(СЛОТ_РЕАГЕНТА).is(ClassItems.PLAGUE_BLOOM.get())) return;

        Item основа = BuiltInRegistries.ITEM.get(ЧУМНОЙ_ОТВАР);
        int сварено = 0;
        for (int слот = 0; слот < СЛОТ_РЕАГЕНТА && сварено < ПОРЦИЙ_ЗА_ЦВЕТОК; слот++) {
            if (событие.getItem(слот).is(основа)) {
                событие.setItem(слот, new ItemStack(ClassItems.CLERICS_BREW.get()));
                сварено++;
            }
        }
        // Ни одной подходящей бутылки — пусть стойка разбирается сама:
        // в ней может стоять чужой рецепт на том же цветке.
        if (сварено == 0) return;

        ItemStack остаток = событие.getItem(СЛОТ_РЕАГЕНТА).copy();
        остаток.shrink(1);
        событие.setItem(СЛОТ_РЕАГЕНТА, остаток);
        событие.setCanceled(true);
    }
}
