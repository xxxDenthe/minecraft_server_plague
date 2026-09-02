package dev.denthe.plaguecore.core;

import java.nio.ByteBuffer;

/**
 * Сериализация сеток в плоский массив байт.
 *
 * Формат:
 *   [0]      версия формата
 *   [1..4]   размер стороны
 *   [5..8]   originX
 *   [9..12]  originZ
 *   далее    level, resistance, scar, terrain — по size*size байт каждый
 *
 * Отдельно от Minecraft, чтобы тестировать без запуска игры.
 * PlagueState просто кладёт результат в ByteArrayTag.
 */
public final class PlagueGridCodec {
    private PlagueGridCodec() {}

    public static final byte FORMAT_VERSION = 1;

    private static final int HEADER_BYTES = 1 + 4 + 4 + 4;

    public static byte[] encode(PlagueGrid grid) {
        int cells = grid.cellCount();
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + cells * 4);
        buf.put(FORMAT_VERSION);
        buf.putInt(grid.size());
        buf.putInt(grid.originX());
        buf.putInt(grid.originZ());
        buf.put(grid.rawLevels());
        buf.put(grid.rawResistance());
        buf.put(grid.rawScar());
        buf.put(grid.rawTerrain());
        return buf.array();
    }

    public static PlagueGrid decode(byte[] data) {
        if (data == null || data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("блоб слишком короткий: "
                + (data == null ? "null" : data.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte version = buf.get();
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("неизвестная версия формата сетки: " + version);
        }
        int size = buf.getInt();
        int originX = buf.getInt();
        int originZ = buf.getInt();

        if (size <= 0 || size > 4096) {
            throw new IllegalArgumentException("некорректный размер сетки: " + size);
        }
        int cells = size * size;
        if (data.length != HEADER_BYTES + cells * 4) {
            throw new IllegalArgumentException("длина блоба не соответствует размеру сетки: ожидалось "
                + (HEADER_BYTES + cells * 4) + ", получено " + data.length);
        }

        byte[] level = new byte[cells];
        byte[] resistance = new byte[cells];
        byte[] scar = new byte[cells];
        byte[] terrain = new byte[cells];
        buf.get(level);
        buf.get(resistance);
        buf.get(scar);
        buf.get(terrain);

        return new PlagueGrid(size, originX, originZ, level, resistance, scar, terrain);
    }
}
