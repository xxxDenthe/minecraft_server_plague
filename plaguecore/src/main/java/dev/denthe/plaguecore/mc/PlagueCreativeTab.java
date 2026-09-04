package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Своя вкладка в творческом инвентаре: всё содержимое мода в одном месте.
 *
 * Список не перечисляется вручную, а берётся из реестра предметов целиком.
 * Новый блок или яйцо попадёт во вкладку само, без правки этого файла;
 * порядок — тот же, в каком предметы зарегистрированы в PlagueBlocks.
 */
public final class PlagueCreativeTab {
    private PlagueCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> ВКЛАДКИ =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PlagueCore.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ЧУМА =
        ВКЛАДКИ.register("plague", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.plaguecore"))
            .icon(() -> new ItemStack(PlagueBlocks.SPORE_SAC_ITEM.get()))
            .displayItems((параметры, вывод) ->
                PlagueBlocks.ПРЕДМЕТЫ.getEntries()
                    .forEach(предмет -> вывод.accept(предмет.get())))
            .build());

    public static void register(IEventBus modEventBus) {
        ВКЛАДКИ.register(modEventBus);
    }
}
