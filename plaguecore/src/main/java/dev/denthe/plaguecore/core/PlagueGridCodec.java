package dev.denthe.plaguecore.core;

import java.nio.ByteBuffer;

/**
 * Сериализация сеток в плоский массив байт.
 *
 * Формат версии 2:
 *   [0]      версия формата
 *   [1..4]   размер стороны
 *   [5..8]   originX
 *   [9..12]  originZ
 *   далее    level, resistance, scar, terrain,
 *            appliedSurface, appliedUnderground — по size*size байт каждый
 *
 * Версия 1 отличается только отсутствием двух последних массивов. Она
 * читается по-прежнему; отрисованные уровни в ней считаются нулевыми,
 * то есть мир один раз перерисуется целиком.
 *
 * Отдельно от Minecraft, чтобы тестировать без запуска игры.
 * PlagueState просто кладёт результат в ByteArrayTag.
 */
public final class PlagueGridCodec {
    private PlagueGridCodec() {}

    public static final byte FORMAT_VERSION = 2;

    /** Сколько массивов по одной ячейке на чанк лежит в блобе каждой версии. */
    private static final int МАССИВОВ_V1 = 4;
    private static final int МАССИВОВ_V2 = 6;

    private static final int HEADER_BYTES = 1 + 4 + 4 + 4;

    public static byte[] encode(PlagueGrid grid) {
        int cells = grid.cellCount();
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + cells * МАССИВОВ_V2);
        buf.put(FORMAT_VERSION);
        buf.putInt(grid.size());
        buf.putInt(grid.originX());
        buf.putInt(grid.originZ());
        buf.put(grid.rawLevels());
        buf.put(grid.rawResistance());
        buf.put(grid.rawScar());
        buf.put(grid.rawTerrain());
        buf.put(grid.rawAppliedSurface());
        buf.put(grid.rawAppliedUnderground());
        return buf.array();
    }

    public static PlagueGrid decode(byte[] data) {
        if (data == null || data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("блоб слишком короткий: "
                + (data == null ? "null" : data.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte version = buf.get();
        int массивов = switch (version) {
            case 1 -> МАССИВОВ_V1;
            case 2 -> МАССИВОВ_V2;
            default -> throw new IllegalArgumentException(
                "неизвестная версия формата сетки: " + version);
        };
        int size = buf.getInt();
        int originX = buf.getInt();
        int originZ = buf.getInt();

        if (size <= 0 || size > 4096) {
            throw new IllegalArgumentException("некорректный размер сетки: " + size);
        }
        int cells = size * size;
        int ожидалось = HEADER_BYTES + cells * массивов;
        if (data.length != ожидалось) {
            throw new IllegalArgumentException("длина блоба не соответствует размеру сетки: ожидалось "
                + ожидалось + ", получено " + data.length);
        }

        byte[] level = new byte[cells];
        byte[] resistance = new byte[cells];
        byte[] scar = new byte[cells];
        byte[] terrain = new byte[cells];
        byte[] appliedSurface = new byte[cells];
        byte[] appliedUnderground = new byte[cells];
        buf.get(level);
        buf.get(resistance);
        buf.get(scar);
        buf.get(terrain);
        if (массивов == МАССИВОВ_V2) {
            buf.get(appliedSurface);
            buf.get(appliedUnderground);
        }

        return new PlagueGrid(size, originX, originZ,
            level, resistance, scar, terrain, appliedSurface, appliedUnderground);
    }
}
