package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Однократное заполнение сетки множителей местности. Спек, раздел 6.4.
 *
 * Множитель берётся из биома, а не из блоков: биом можно спросить у
 * генератора без загрузки чанка, а блоки — нельзя. Для наших целей
 * этого достаточно: нас интересует «живая местность или мёртвая»,
 * а не конкретный блок.
 */
public final class TerrainInitializer {
    private TerrainInitializer() {}

    private static final float ЖИВАЯ = 1.4f;   // лес, джунгли, равнины
    private static final float ОБЫЧНАЯ = 1.0f; // земля, песок
    private static final float КАМЕНЬ = 0.6f;  // горы, скалы
    private static final float ВОДА = 0.4f;    // океаны, реки
    private static final float МЁРТВАЯ = 0.1f; // лава, незер-подобное

    public static int initialize(ServerLevel level, PlagueState state) {
        PlagueGrid grid = state.grid();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();

        // getNoiseBiome работает в «квартах» — блок, делённый на 4
        final int quartY = level.getSeaLevel() >> 2;

        int заполнено = 0;
        for (int cz = grid.originZ(); cz < grid.originZ() + grid.size(); cz++) {
            for (int cx = grid.originX(); cx < grid.originX() + grid.size(); cx++) {
                int quartX = (cx << 4) >> 2;
                int quartZ = (cz << 4) >> 2;
                Holder<Biome> biome = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
                grid.setTerrain(cx, cz, множительДля(biome));
                заполнено++;
            }
        }
        state.markTerrainInitialized();
        state.setDirty();
        return заполнено;
    }

    private static float множительДля(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)
            || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return ВОДА;
        }
        if (biome.is(BiomeTags.IS_NETHER)) {
            return МЁРТВАЯ;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_END)) {
            return КАМЕНЬ;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)
            || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return ЖИВАЯ;
        }
        return ОБЫЧНАЯ;
    }
}
