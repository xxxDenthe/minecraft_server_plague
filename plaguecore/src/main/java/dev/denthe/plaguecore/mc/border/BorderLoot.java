package dev.denthe.plaguecore.mc.border;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.BorderMath;
import dev.denthe.plaguecore.mc.PlagueApi;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Лут по глубине: чем ближе сундук к Гнили, тем больше в нём лежит.
 *
 * Смысл — дать повод лезть дальше. Пограничье иначе проходится по краю:
 * дома у города обшарены, а идти вглубь незачем, потому что там ровно
 * то же самое, только опаснее.
 *
 * <p><b>Исходная таблица не подменяется, а дополняется.</b> Модовые
 * структуры сами решают, что в них лежит; мы доливаем сверху столько
 * бросков, сколько велит уровень чанка. Так ничья работа не пропадает
 * и ни один сундук не превращается в наш собственный.
 *
 * <p><b>В городе не доливается ничего.</b> Уровень 0 даёт ноль бросков,
 * и это не настройка, а свойство: сундук в стенах города не должен
 * богатеть от того, что зараза подошла к воротам.
 *
 * <p>Условия в json пустые намеренно. Отбор «это сундук, а не моб»
 * делается здесь по идентификатору таблицы: {@code LootTableIdCondition}
 * умеет сравнивать только с одним точным идентификатором, а сундучных
 * таблиц в паке сотни — по одному условию на каждую не напасёшься.
 */
public class BorderLoot extends LootModifier {

    public static final MapCodec<BorderLoot> CODEC =
        RecordCodecBuilder.mapCodec(инст -> codecStart(инст).apply(инст, BorderLoot::new));

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> КОДЕКИ =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, PlagueCore.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<BorderLoot>>
        BORDERLAND = КОДЕКИ.register("borderland", () -> CODEC);

    /** Самый глубокий тир: уровни выше просто читают его же таблицу. */
    private static final int ПОСЛЕДНИЙ_ТИР = 3;

    public BorderLoot(LootItemCondition[] условия) {
        super(условия);
    }

    public static void register(IEventBus шина) {
        КОДЕКИ.register(шина);
    }

    @SuppressWarnings("deprecation") // getRandomItemsRaw — как в neoforge:add_table
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> лут, LootContext контекст) {
        if (!сундук(контекст)) return лут;

        ServerLevel мир = контекст.getLevel();
        Vec3 точка = контекст.getParamOrNull(LootContextParams.ORIGIN);
        if (точка == null) return лут;

        int уровень = PlagueApi.getChunkLevelAt(мир, BlockPos.containing(точка));
        final int бросков = BorderMath.поУровню(PlagueConstants.BORDER_LOOT_ROLLS, уровень);
        if (бросков <= 0) return лут;

        ResourceKey<LootTable> тир = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID,
                "borderland/tier" + Math.min(уровень, ПОСЛЕДНИЙ_ТИР)));

        контекст.getResolver().get(Registries.LOOT_TABLE, тир).ifPresent(таблица -> {
            for (int i = 0; i < бросков; i++) {
                таблица.value().getRandomItemsRaw(контекст,
                    LootTable.createStackSplitter(мир, лут::add));
            }
        });
        return лут;
    }

    /**
     * Сундук ли это. Ванильные таблицы лежат в {@code chests/}, модовые
     * структуры почти без исключений повторяют то же имя папки. Мобы,
     * блоки и рыбалка сюда не попадают — им доливать нечего.
     */
    private static boolean сундук(LootContext контекст) {
        return контекст.getQueriedLootTableId().getPath().contains("chests/");
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
