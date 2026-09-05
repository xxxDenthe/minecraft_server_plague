package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.PlagueGridCodec;
import dev.denthe.plaguecore.core.PhaseTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

/**
 * Состояние эпидемии в мире. Источник истины — сетки внутри PlagueGrid.
 *
 * Хранится через ванильный SavedData, то есть переживает перезапуск
 * сервера и лежит рядом с остальными данными мира.
 */
public class PlagueState extends SavedData {

    private static final String DATA_NAME = "plaguecore_state";

    private static final String KEY_GRID = "Grid";
    private static final String KEY_NIGHT = "Night";
    private static final String KEY_PAUSED = "Paused";
    private static final String KEY_TERRAIN_READY = "TerrainReady";
    private static final String KEY_EPICENTERS = "Epicenters";
    private static final String KEY_WORDS = "RevealedWords";

    private PlagueGrid grid;
    private int night;
    private boolean paused;
    private boolean terrainInitialized;
    private final List<Long> epicenters = new ArrayList<>();

    /**
     * Раскрытые корни тайнописи. Одно множество на весь сервер:
     * это память команды, а не рюкзак игрока — так же, как Хроника.
     */
    private final Set<String> раскрытыеСлова = new LinkedHashSet<>();

    /** Флаг «ночь этих суток уже обработана», в NBT не пишется. */
    private long lastProcessedDay = -1;

    public PlagueState() {
        int size = PlagueConstants.GRID_SIZE_CHUNKS;
        this.grid = new PlagueGrid(size, -(size / 2), -(size / 2));
        this.night = 0;
        this.paused = false;
        this.terrainInitialized = false;
    }

    public static PlagueState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PlagueState::new, PlagueState::load),
            DATA_NAME
        );
    }

    private static PlagueState load(CompoundTag tag, HolderLookup.Provider lookup) {
        PlagueState st = new PlagueState();
        if (tag.contains(KEY_GRID)) {
            st.grid = PlagueGridCodec.decode(tag.getByteArray(KEY_GRID));
        }
        st.night = tag.getInt(KEY_NIGHT);
        st.paused = tag.getBoolean(KEY_PAUSED);
        st.terrainInitialized = tag.getBoolean(KEY_TERRAIN_READY);
        st.epicenters.clear();
        for (long p : tag.getLongArray(KEY_EPICENTERS)) st.epicenters.add(p);
        st.раскрытыеСлова.clear();
        ListTag слова = tag.getList(KEY_WORDS, Tag.TAG_STRING);
        for (int i = 0; i < слова.size(); i++) st.раскрытыеСлова.add(слова.getString(i));
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putByteArray(KEY_GRID, PlagueGridCodec.encode(grid));
        tag.putInt(KEY_NIGHT, night);
        tag.putBoolean(KEY_PAUSED, paused);
        tag.putBoolean(KEY_TERRAIN_READY, terrainInitialized);
        long[] eps = new long[epicenters.size()];
        for (int i = 0; i < eps.length; i++) eps[i] = epicenters.get(i);
        tag.putLongArray(KEY_EPICENTERS, eps);
        ListTag слова = new ListTag();
        for (String к : раскрытыеСлова) слова.add(StringTag.valueOf(к));
        tag.put(KEY_WORDS, слова);
        return tag;
    }

    /** Раскрытые корни тайнописи, только для чтения. */
    public Set<String> раскрытыеСлова() { return Set.copyOf(раскрытыеСлова); }

    public boolean раскрыт(String корень) { return раскрытыеСлова.contains(корень); }

    /** Раскрыть корень. true — если раньше был закрыт. */
    public boolean раскрыть(String корень) {
        if (!раскрытыеСлова.add(корень)) return false;
        setDirty();
        return true;
    }

    /** Спрятать корень обратно. Нужно только мастеру игры, для отладки. */
    public boolean спрятать(String корень) {
        if (!раскрытыеСлова.remove(корень)) return false;
        setDirty();
        return true;
    }

    public PlagueGrid grid() { return grid; }

    public int night() { return night; }

    public void setNight(int value) { this.night = Math.max(0, value); setDirty(); }

    public void advanceNight() { this.night++; setDirty(); }

    public int phase() { return PhaseTable.phaseForNight(night); }

    public boolean isPaused() { return paused; }

    public void setPaused(boolean value) { this.paused = value; setDirty(); }

    public boolean isTerrainInitialized() { return terrainInitialized; }

    public void markTerrainInitialized() { this.terrainInitialized = true; setDirty(); }

    public List<Long> epicenters() { return List.copyOf(epicenters); }

    public long[] epicentersArray() {
        long[] out = new long[epicenters.size()];
        for (int i = 0; i < out.length; i++) out[i] = epicenters.get(i);
        return out;
    }

    public void addEpicenter(long packed) {
        if (!epicenters.contains(packed)) {
            epicenters.add(packed);
            setDirty();
        }
    }

    public void removeEpicenter(long packed) {
        if (epicenters.remove(packed)) setDirty();
    }

    /** Чанк, вокруг которого построена сетка. Совпадает с центром карты в GUI. */
    public int центрЧанкX() { return grid.originX() + grid.size() / 2; }

    public int центрЧанкZ() { return grid.originZ() + grid.size() / 2; }

    /**
     * Перенести сетку на новый центр мира.
     *
     * Сетка строится заново и пустой: старые уровни считались под другой
     * рельеф, а множители местности в новом месте другие — переносить их
     * значило бы врать движку распространения всю сессию. Очаги стираются,
     * местность помечается неразмеченной; после переноса нужен
     * {@code TerrainInitializer} и {@code /plague generate}.
     */
    public void переместитьЦентр(int центрЧанкX, int центрЧанкZ) {
        int size = PlagueConstants.GRID_SIZE_CHUNKS;
        this.grid = new PlagueGrid(size, центрЧанкX - size / 2, центрЧанкZ - size / 2);
        this.epicenters.clear();
        this.terrainInitialized = false;
        setDirty();
    }

    public long lastProcessedDay() { return lastProcessedDay; }

    public void setLastProcessedDay(long day) { this.lastProcessedDay = day; }
}
