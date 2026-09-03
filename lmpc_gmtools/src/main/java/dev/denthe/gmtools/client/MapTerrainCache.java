package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Фон карты «как на карте-предмете» для прогруженных чанков.
 *
 * Оптимизация: цвета колонок кэшируются по чанку (TTL 5 с), а из них
 * один раз собирается плоский список прямоугольников — соседние клетки
 * одного цвета в строке сливаются в один прямоугольник. Пока вид не
 * двигается и чанки не пересчитывались, отрисовка — это просто повтор
 * готового списка, без обхода клеток.
 *
 * ponytail: слияние только по горизонтали внутри чанка; при тормозах
 * — вертикальное слияние или отрисовка в DynamicTexture.
 */
public final class MapTerrainCache {

    private static final long TTL_MS = 5000;
    private static final int RECOMPUTE_PER_BUILD = 8;

    // ── кэш цветов по чанкам ──────────────────────────────────────────────
    private final Map<Long, int[]> colors = new HashMap<>();
    private final Map<Long, Long> stamped = new HashMap<>();
    private long version;   // растёт при пересчёте любого чанка

    // ── кэш собранных прямоугольников ─────────────────────────────────────
    private int[] rects = new int[4096];   // x,y,w,h,argb — повторяется
    private int rectCount;                  // элементов в rects (кратно 5)
    private double rCX = Double.NaN, rCZ, rScale;
    private long rVersion = -1;
    private long rebuiltAt;
    private int buildBudget;                // сколько чанков ещё можно пересчитать за эту сборку

    public void draw(GuiGraphics g, ClientLevel level, int mapX, int mapY, int mapW, int mapH,
                     double cx, double cz, double scale) {
        if (level == null) return;

        boolean same = rVersion == version && cx == rCX && cz == rCZ && scale == rScale
            && System.currentTimeMillis() - rebuiltAt < 2000;
        if (!same) {
            rebuild(level, mapX, mapY, mapW, mapH, cx, cz, scale);
            rCX = cx; rCZ = cz; rScale = scale; rVersion = version;
            rebuiltAt = System.currentTimeMillis();
        }
        for (int i = 0; i < rectCount; i += 5) {
            g.fill(rects[i], rects[i + 1], rects[i] + rects[i + 2], rects[i + 1] + rects[i + 3], rects[i + 4]);
        }
    }

    private void rebuild(ClientLevel level, int mapX, int mapY, int mapW, int mapH,
                         double cx, double cz, double scale) {
        rectCount = 0;
        buildBudget = RECOMPUTE_PER_BUILD;

        double midX = mapX + mapW / 2.0, midY = mapY + mapH / 2.0;
        double halfW = (mapW / 2.0) / scale, halfH = (mapH / 2.0) / scale;
        int minCX = net.minecraft.util.Mth.floor(cx - halfW) >> 4;
        int maxCX = net.minecraft.util.Mth.floor(cx + halfW) >> 4;
        int minCZ = net.minecraft.util.Mth.floor(cz - halfH) >> 4;
        int maxCZ = net.minecraft.util.Mth.floor(cz + halfH) >> 4;
        if (maxCX - minCX > 96 || maxCZ - minCZ > 96) return;   // слишком отдалено

        int step = scale >= 0.45 ? 1 : (scale >= 0.18 ? 2 : 4);

        for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
            for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                int[] cols = chunkColors(level, chunkX, chunkZ);
                if (cols == null) continue;

                int baseX = chunkX << 4, baseZ = chunkZ << 4;
                for (int z = 0; z < 16; z += step) {
                    int runStart = -1, runColor = 0;
                    for (int x = 0; x <= 16; x += step) {
                        int col = (x < 16) ? cols[x * 16 + z] : 0;
                        if (col == runColor && col != 0) continue;
                        if (runStart >= 0 && runColor != 0) {
                            int sx = (int) Math.round(midX + (baseX + runStart - cx) * scale);
                            int ex = (int) Math.round(midX + (baseX + x - cx) * scale);
                            int sy = (int) Math.round(midY + (baseZ + z - cz) * scale);
                            int ey = (int) Math.round(midY + (baseZ + z + step - cz) * scale);
                            if (ex > mapX && sx < mapX + mapW && ey > mapY && sy < mapY + mapH) {
                                addRect(sx, sy, Math.max(1, ex - sx), Math.max(1, ey - sy), runColor);
                            }
                        }
                        runStart = x;
                        runColor = col;
                    }
                }
            }
        }
    }

    /** Цвета колонок чанка (свежие или из кэша), либо null, если данных нет. */
    private int[] chunkColors(ClientLevel level, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long now = System.currentTimeMillis();
        int[] cached = colors.get(key);
        Long ts = stamped.get(key);
        if (cached != null && ts != null && now - ts < TTL_MS) return cached;
        if (buildBudget <= 0) return cached;

        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return cached;
        buildBudget--;

        int[] out = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                m.set((cx << 4) + x, y - 1, (cz << 4) + z);
                MapColor mc = chunk.getBlockState(m).getMapColor(level, m);
                out[x * 16 + z] = mc == MapColor.NONE ? 0 : (0xFF000000 | mc.col);
            }
        }
        colors.put(key, out);
        stamped.put(key, now);
        version++;
        return out;
    }

    private void addRect(int x, int y, int w, int h, int argb) {
        if (rectCount + 5 > rects.length) {
            int[] bigger = new int[rects.length * 2];
            System.arraycopy(rects, 0, bigger, 0, rectCount);
            rects = bigger;
        }
        rects[rectCount++] = x;
        rects[rectCount++] = y;
        rects[rectCount++] = w;
        rects[rectCount++] = h;
        rects[rectCount++] = argb;
    }
}
