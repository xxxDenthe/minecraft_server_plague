package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Предметы мода. Клирик (спек, раздел 4) — кулон и улучшенный отвар.
 * Реагент (раздел 8) — сырой бутон и обработанный агент; пока целиком
 * здесь, а не в `plaguecore`: старый спек предполагал, что предмет
 * объявляет ядро, но это писалось до разделения на моды.
 */
public final class ClassItems {
    private ClassItems() {}

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(LmpcClasses.MODID);

    public static final DeferredItem<ClericsPendantItem> CLERICS_PENDANT = ПРЕДМЕТЫ.registerItem(
        "clerics_pendant", ClericsPendantItem::new);

    public static final DeferredItem<ClericsBrewItem> CLERICS_BREW = ПРЕДМЕТЫ.registerItem(
        "clerics_brew", свойства -> new ClericsBrewItem(свойства.stacksTo(1)));

    /**
     * Гримуар — личный дневник призвания. Выдаётся автоматически при
     * первом выборе класса ({@link ClassCommands}), крафта нет.
     */
    public static final DeferredItem<ClassCodexItem> CLASS_CODEX = ПРЕДМЕТЫ.registerItem(
        "class_codex", свойства -> new ClassCodexItem(свойства.stacksTo(1)));

    /**
     * Сырой бутон чумы. По спеку (раздел 10.4 старого документа)
     * должен добываться диким кустом в Гнили или расти на грядке
     * Фермера — ни того, ни другого ещё нет, поэтому пока обычный
     * крафтимый предмет. Заменится настоящим сбором, когда подъедут
     * Фермер и Мир.
     */
    public static final DeferredItem<net.minecraft.world.item.Item> PLAGUE_BLOOM =
        ПРЕДМЕТЫ.registerSimpleItem("plague_bloom");

    /**
     * Обработанный реагент чумы. Крафт открыт всем — гейта по классу
     * на использовании нет, как у отвара, потому что варить его
     * в одиночку ещё нечем: ни один потребитель (Очиститель, топливо
     * курильницы) пока не спрашивает, кто его сварил. Когда появится
     * настоящее «эксклюзивное» потребление — гейтить там же, где
     * `clerics_brew`, а не здесь.
     */
    public static final DeferredItem<net.minecraft.world.item.Item> CLEANSING_AGENT =
        ПРЕДМЕТЫ.registerSimpleItem("cleansing_agent");

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
}
