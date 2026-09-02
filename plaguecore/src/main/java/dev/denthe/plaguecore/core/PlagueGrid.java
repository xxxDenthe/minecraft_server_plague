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
 *
 * Четыре массива по 3 969 байт — около 16 КБ на весь мир. Влезает в кэш
 * процессора целиком, поэтому ночной проход стоит доли миллисекунды.
 */
public final class PlagueGrid {

    private final int size;
    private final int originX;
    private final int originZ;

    private final byte[] level;
    private final byte[] resistance;
    private final byte[] scar;
    private final byte[] terrain;

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
        Arrays.fill(this.terrain, (byte) 100); // множитель 1.0 по умолчанию
    }

    /** Конструктор для кодека: массивы принимаются как есть, без копирования. */
    PlagueGrid(int size, int originChunkX, int originChunkZ,
               byte[] level, byte[] resistance, byte[] scar, byte[] terrain) {
        this.size = size;
        this.originX = originChunkX;
        this.originZ = originChunkZ;
        this.level = level;
        this.resistance = resistance;
        this.scar = scar;
        this.terrain = terrain;
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

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static float clampF(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
