package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Путёвки классов. По одной на класс. Правый клик — {@link ClassTokenItem}.
 */
public final class ClassItems {
    private ClassItems() {}

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(LmpcClasses.MODID);

    public static final DeferredItem<ClassTokenItem> TOKEN_CLERIC = ПРЕДМЕТЫ.registerItem(
        "class_token_cleric",
        свойства -> new ClassTokenItem(PlayerClassData.Класс.CLERIC, свойства));

    public static final DeferredItem<ClassTokenItem> TOKEN_SMITH = ПРЕДМЕТЫ.registerItem(
        "class_token_smith",
        свойства -> new ClassTokenItem(PlayerClassData.Класс.SMITH, свойства));

    public static final DeferredItem<ClassTokenItem> TOKEN_FARMER = ПРЕДМЕТЫ.registerItem(
        "class_token_farmer",
        свойства -> new ClassTokenItem(PlayerClassData.Класс.FARMER, свойства));

    public static final DeferredItem<ClassTokenItem> TOKEN_CHRONICLER = ПРЕДМЕТЫ.registerItem(
        "class_token_chronicler",
        свойства -> new ClassTokenItem(PlayerClassData.Класс.CHRONICLER, свойства));

    public static void register(IEventBus modEventBus) {
        ПРЕДМЕТЫ.register(modEventBus);
    }
}
