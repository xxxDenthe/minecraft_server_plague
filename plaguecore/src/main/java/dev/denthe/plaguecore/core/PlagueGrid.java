package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

import java.util.Arrays;

/**
 * Четыре плоские сетки байт, по одной ячейке на чанк.
 *
 * Хранение:
 *   level      0..5  уровень заражения
 *   resistance 0..100 → 0.0..1.0  сопротивление от очистителей
 *   scar       0..7   сколько ночей ещё держится шрам
 *   terrain    0..255 → делённое на 100  множитель местности
 *   appliedSurface     0..5  до какого уровня чанк уже отрисован сверху
 *   appliedUnderground 0..5  то же под землёй
 *
 * Шесть массивов по 3 969 байт — около 24 КБ на весь мир. Влезает в кэш
 * процессора целиком, поэтому ночной проход стоит доли миллисекунды.
 *
 * Отрисованные уровни лежат здесь, а не в данных чанка, потому что очередь
 * материализации строится в том числе из незагруженных чанков, а про них
 * иначе ничего не узнать. Дизайн материализации, раздел 3.1.
 */
public final class PlagueGrid {

    private final int size;
    private final int originX;
    private final int originZ;

    private final byte[] level;
    private final byte[] resistance;
    private final byte[] scar;
    private final byte[] terrain;
    private final byte[] appliedSurface;
    private final byte[] appliedUnderground;

    public PlagueGrid(int size, int originChunkX, int originChunkZ) {
        if (size <= 0) throw new IllegalArgumentException("size должен быть положительным");
        this.size = size;
        this.originX = originChunkX;
        this.originZ = originChunkZ;
        int n = size * size;
        this.level = new byte[n];
        this.resistance = new byte[n];
        this.scar = new byte[n];
        this.terrain = new byte[n];
        this.appliedSurface = new byte[n];
        this.appliedUnderground = new byte[n];
        Arrays.fill(this.terrain, (byte) 100); // множитель 1.0 по умолчанию
    }

    /** Конструктор для кодека: массивы принимаются как есть, без копирования. */
    PlagueGrid(int size, int originChunkX, int originChunkZ,
               byte[] level, byte[] resistance, byte[] scar, byte[] terrain,
               byte[] appliedSurface, byte[] appliedUnderground) {
        this.size = size;
        this.originX = originChunkX;
        this.originZ = originChunkZ;
        this.level = level;
        this.resistance = resistance;
        this.scar = scar;
        this.terrain = terrain;
        this.appliedSurface = appliedSurface;
        this.appliedUnderground = appliedUnderground;
    }

    public int size() { return size; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int cellCount() { return size * size; }

    public boolean contains(int cx, int cz) {
        int dx = cx - originX;
        int dz = cz - originZ;
        return dx >= 0 && dx < size && dz >= 0 && dz < size;
    }

    /** Индекс ячейки или -1, если координата вне сетки. */
    public int index(int cx, int cz) {
        int dx = cx - originX;
        int dz = cz - originZ;
        if (dx < 0 || dx >= size || dz < 0 || dz >= size) return -1;
        return dz * size + dx;
    }

    public int chunkXOf(int index) { return originX + (index % size); }
    public int chunkZOf(int index) { return originZ + (index / size); }

    public int getLevel(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : level[i];
    }

    public void setLevel(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        level[i] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public int getLevelAt(int index) { return level[index]; }

    /**
     * Наибольший уровень в квадрате три на три вокруг чанка, считая его сам.
     *
     * Нужен материализации: доля поражённых столбцов сглажена между
     * центрами соседних чанков, поэтому картинка чанка зависит не только
     * от него. Это число и есть «до чего чанк надо дорисовать», и по нему
     * же видно, что он отстал.
     */
    public int maxLevelAround(int index) {
        int cx = chunkXOf(index);
        int cz = chunkZOf(index);
        int max = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                max = Math.max(max, getLevel(cx + dx, cz + dz));
            }
        }
        return max;
    }

    public void setLevelAt(int index, int value) {
        level[index] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public float getResistance(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0f : (resistance[i] & 0xFF) / 100f;
    }

    public void setResistance(int cx, int cz, float value) {
        int i = index(cx, cz);
        if (i < 0) return;
        resistance[i] = (byte) Math.round(clampF(value, 0f, 1f) * 100f);
    }

    public float getResistanceAt(int index) { return (resistance[index] & 0xFF) / 100f; }

    public int getScar(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : scar[i];
    }

    public void setScar(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        scar[i] = (byte) clamp(value, 0, 7);
    }

    public int getScarAt(int index) { return scar[index]; }

    public void setScarAt(int index, int value) { scar[index] = (byte) clamp(value, 0, 7); }

    public float getTerrain(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0f : (terrain[i] & 0xFF) / 100f;
    }

    public void setTerrain(int cx, int cz, float value) {
        int i = index(cx, cz);
        if (i < 0) return;
        terrain[i] = (byte) Math.round(clampF(value, 0f, 2.55f) * 100f);
    }

    public float getTerrainAt(int index) { return (terrain[index] & 0xFF) / 100f; }

    public int getAppliedSurface(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : appliedSurface[i];
    }

    public void setAppliedSurface(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        appliedSurface[i] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public int getAppliedSurfaceAt(int index) { return appliedSurface[index]; }

    public void setAppliedSurfaceAt(int index, int value) {
        appliedSurface[index] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public int getAppliedUnderground(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : appliedUnderground[i];
    }

    public void setAppliedUnderground(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        appliedUnderground[i] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public int getAppliedUndergroundAt(int index) { return appliedUnderground[index]; }

    public void setAppliedUndergroundAt(int index, int value) {
        appliedUnderground[index] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    /**
     * Отстала ли картинка мира от расчёта. Неравенство в обе стороны:
     * уровень вырос — надо догнивать, уровень упал при очистке — надо
     * отыгрывать назад.
     */
    public boolean surfaceOutOfDate(int cx, int cz) {
        int i = index(cx, cz);
        return i >= 0 && surfaceOutOfDateAt(i);
    }

    public boolean surfaceOutOfDateAt(int index) {
        return level[index] != appliedSurface[index];
    }

    public boolean undergroundOutOfDate(int cx, int cz) {
        int i = index(cx, cz);
        return i >= 0 && undergroundOutOfDateAt(i);
    }

    public boolean undergroundOutOfDateAt(int index) {
        return level[index] != appliedUnderground[index];
    }

    public int countInfected() {
        int n = 0;
        for (byte b : level) if (b > 0) n++;
        return n;
    }

    public float infectedFraction() {
        return (float) countInfected() / cellCount();
    }

    public byte[] levelsCopy() { return level.clone(); }

    byte[] rawLevels() { return level; }
    byte[] rawResistance() { return resistance; }
    byte[] rawScar() { return scar; }
    byte[] rawTerrain() { return terrain; }
    byte[] rawAppliedSurface() { return appliedSurface; }
    byte[] rawAppliedUnderground() { return appliedUnderground; }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static float clampF(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
