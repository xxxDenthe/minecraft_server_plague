package dev.denthe.classes;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.theillusivec4.curios.api.CuriosApi;

/** Предметы Клирика: кулон и улучшенный отвар. Спек, раздел 4. */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassItems {
    private ClassItems() {}

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(LmpcClasses.MODID);

    public static final DeferredItem<ClericsPendantItem> CLERICS_PENDANT = ПРЕДМЕТЫ.registerItem(
        "clerics_pendant", ClericsPendantItem::new);

    public static final DeferredItem<ClericsBrewItem> CLERICS_BREW = ПРЕДМЕТЫ.registerItem(
        "clerics_brew", свойства -> new ClericsBrewItem(свойства.stacksTo(1)));

    public static void register(IEventBus modEventBus) {
        ПРЕДМЕТЫ.register(modEventBus);
        modEventBus.addListener(ClassItems::настройка);
    }

    /**
     * Регистрация в Curios — только если он реально загружен. Toml
     * помечает зависимость необязательной, но сам класс ссылается на
     * Curios напрямую, поэтому без него эта регистрация не должна
     * даже пытаться выполниться.
     */
    private static void настройка(FMLCommonSetupEvent событие) {
        событие.enqueueWork(() ->
            CuriosApi.registerCurio(CLERICS_PENDANT.get(), CLERICS_PENDANT.get()));
    }

    @SubscribeEvent
    public static void вКреатив(BuildCreativeModeTabContentsEvent событие) {
        if (событие.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            событие.accept(CLERICS_BREW);
        }
        if (событие.getTabKey() == CreativeModeTabs.COMBAT) {
            событие.accept(CLERICS_PENDANT);
        }
    }
}
