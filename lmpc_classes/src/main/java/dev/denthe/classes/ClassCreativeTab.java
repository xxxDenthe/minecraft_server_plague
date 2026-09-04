package dev.denthe.classes;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Своя вкладка в творческом инвентаре — всё содержимое мода в одном
 * месте, как у `plaguecore` (`PlagueCreativeTab`). Список берётся из
 * реестров целиком: новый предмет попадёт сюда сам.
 */
public final class ClassCreativeTab {
    private ClassCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> ВКЛАДКИ =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LmpcClasses.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> КЛАССЫ =
        ВКЛАДКИ.register("classes", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lmpc_classes"))
            .icon(() -> new ItemStack(ClassItems.CLERICS_PENDANT.get()))
            .displayItems((параметры, вывод) -> {
                ClassBlocks.ПРЕДМЕТЫ.getEntries().forEach(предмет -> вывод.accept(предмет.get()));
                ClassItems.ПРЕДМЕТЫ.getEntries().forEach(предмет -> вывод.accept(предмет.get()));
            })
            .build());

    public static void register(IEventBus modEventBus) {
        ВКЛАДКИ.register(modEventBus);
    }
}
