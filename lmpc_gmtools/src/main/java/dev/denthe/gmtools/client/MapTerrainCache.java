package dev.denthe.gmtools.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Кэш цветов «как на карте-предмете» для прогруженных чанков. Клиент
 * знает рельеф только вокруг себя (радиус прогрузки), поэтому фон карты
 * покрывает лишь эту зону — как и просил владелец.
 *
 * ponytail: наивная выборка верхнего блока по колонке, кэш по чанку с
 * TTL 5 с и лимитом пересчётов на кадр. Начнёт лагать — перевести на
 * DynamicTexture-атлас.
 */
public final class MapTerrainCache {

    private static final long TTL_MS = 5000;
    private static final int RECOMPUTE_PER_FRAME = 6;

    private final Map<Long, int[]> colors = new HashMap<>();
    private final Map<Long, Long> stamped = new HashMap<>();
    private int budget;

    /** Раз за кадр перед отрисовкой рельефа. */
    public void beginFrame() {
        budget = RECOMPUTE_PER_FRAME;
    }

    /**
     * 256 цветов ARGB по колонкам чанка (индекс x*16+z; 0 — нет цвета),
     * либо null, если чанк не загружен и в кэше ничего нет.
     */
    public int[] chunk(ClientLevel level, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long now = System.currentTimeMillis();
        int[] cached = colors.get(key);
        Long ts = stamped.get(key);
        boolean fresh = cached != null && ts != null && now - ts < TTL_MS;
        if (fresh || budget <= 0) return cached;

        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return cached;
        budget--;

        int[] out = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                m.set(wx, y - 1, wz);
                MapColor mc = chunk.getBlockState(m).getMapColor(level, m);
                out[x * 16 + z] = mc == MapColor.NONE ? 0 : (0xFF000000 | mc.col);
            }
        }
        colors.put(key, out);
        stamped.put(key, now);
        return out;
    }
}
