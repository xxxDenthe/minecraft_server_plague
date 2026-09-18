package dev.denthe.classes.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.denthe.classes.ClassBlocks;
import dev.denthe.classes.ClassItems;
import dev.denthe.classes.LmpcClasses;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Подсказки предметов в стиле Create: строка «Удерживайте [Shift] для
 * сводки», а под ней лор, назначение и разбор «когда — что делает».
 *
 * <b>Своего кода здесь нет намеренно.</b> Всё рисует сам Create:
 * {@link ItemDescription.Modifier} читает языковой файл по ключам
 * {@code item.<мод>.<предмет>.tooltip.summary},
 * {@code .condition1}/{@code .behaviour1} и так далее, режет строки
 * по ширине и красит их палитрой {@code STANDARD_CREATE} — тем же
 * оранжевым с жёлтой подсветкой, что у самого Create и его дополнений.
 * Слово в тексте, обёрнутое подчёркиваниями (_вот так_), становится
 * подсвеченным.
 *
 * Строку «Удерживайте [Shift]» переводить не надо: она приходит
 * из языкового файла Create ({@code create.tooltip.holdForDescription}).
 *
 * Класс клиентский: подсказки существуют только на клиенте, а сам
 * реестр {@link TooltipModifier#REGISTRY} опрашивается из
 * {@code ClientEvents} Create для любого предмета, чей бы мод его
 * ни зарегистрировал.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID, value = Dist.CLIENT)
public final class CreateTooltips {
    private CreateTooltips() {}

    @SubscribeEvent
    public static void клиентГотов(FMLClientSetupEvent событие) {
        событие.enqueueWork(() -> {
            подключить(ClassItems.CLERICS_PENDANT);
            подключить(ClassItems.CLASS_CODEX);
            подключить(ClassItems.PLAGUE_BLOOM);
            подключить(ClassItems.CLEANSING_AGENT);
            подключить(ClassItems.CENSER);
            подключить(ClassItems.CLERICS_BREW);
            // Блочные предметы подключаются той же строкой: Create
            // строит ключ из getDescriptionId(), поэтому у очистителей
            // он начинается с block., а не с item.
            подключить(ClassBlocks.ANDESITE_PURIFIER_ITEM);
            подключить(ClassBlocks.BRASS_PURIFIER_ITEM);

            // Отвар и повязка принадлежат plaguecore, но подсказку им
            // рисует тот же Create, а реестр модификаторов общий на всю
            // игру. Компилироваться против plaguecore ради двух
            // предметов ни к чему: берём их из реестра по имени, а
            // тексты лежат в его же языковом файле.
            подключить(ResourceLocation.fromNamespaceAndPath("plaguecore", "plague_brew"));
            подключить(ResourceLocation.fromNamespaceAndPath("plaguecore", "plague_mask"));
        });
    }

    private static void подключить(DeferredItem<? extends Item> держатель) {
        зарегистрировать(держатель.get());
    }

    /** Без мода-хозяина реестр вернёт воздух — тогда просто молчим. */
    private static void подключить(ResourceLocation имя) {
        Item предмет = BuiltInRegistries.ITEM.get(имя);
        if (предмет != Items.AIR) зарегистрировать(предмет);
    }

    private static void зарегистрировать(Item предмет) {
        TooltipModifier.REGISTRY.register(предмет,
            new ItemDescription.Modifier(предмет, FontHelper.Palette.STANDARD_CREATE));
    }
}
