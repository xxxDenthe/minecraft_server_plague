package dev.denthe.shade;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Мастер-значения цветокора на сервере: карта id→строка. Только те
 * параметры, которые мастер игры явно поменял; остальное у клиента
 * остаётся его локальным. Персист через ванильный SavedData
 * (data/lmpc_shade.dat в мире), переживает перезапуск.
 */
public final class ShadeServerState extends SavedData {
    private static final String NAME = "lmpc_shade";

    private final Map<String, String> values = new LinkedHashMap<>();

    public static ShadeServerState get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(ShadeServerState::new, ShadeServerState::load), NAME);
    }

    public Map<String, String> snapshot() {
        return new LinkedHashMap<>(values);
    }

    public void put(String id, String value) {
        values.put(id, value);
        setDirty();
    }

    public void clear() {
        values.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag v = new CompoundTag();
        values.forEach(v::putString);
        tag.put("values", v);
        return tag;
    }

    static ShadeServerState load(CompoundTag tag, HolderLookup.Provider registries) {
        ShadeServerState s = new ShadeServerState();
        CompoundTag v = tag.getCompound("values");
        for (String k : v.getAllKeys()) s.values.put(k, v.getString(k));
        return s;
    }
}
