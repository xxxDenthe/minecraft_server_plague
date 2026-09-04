package dev.denthe.classes;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Алтарь призвания — единственный блок мода. */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassBlocks {
    private ClassBlocks() {}

    public static final DeferredRegister.Blocks БЛОКИ =
        DeferredRegister.createBlocks(LmpcClasses.MODID);

    public static final DeferredRegister.Items ПРЕДМЕТЫ =
        DeferredRegister.createItems(LmpcClasses.MODID);

    public static final DeferredBlock<ClassAltarBlock> CLASS_ALTAR = БЛОКИ.registerBlock(
        "class_altar",
        ClassAltarBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS)
            .mapColor(MapColor.STONE));

    public static final DeferredItem<BlockItem> CLASS_ALTAR_ITEM =
        ПРЕДМЕТЫ.registerSimpleBlockItem(CLASS_ALTAR);

    public static void register(IEventBus modEventBus) {
        БЛОКИ.register(modEventBus);
        ПРЕДМЕТЫ.register(modEventBus);
    }

    @SubscribeEvent
    public static void вКреатив(BuildCreativeModeTabContentsEvent событие) {
        if (событие.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            событие.accept(CLASS_ALTAR_ITEM);
        }
    }
}
