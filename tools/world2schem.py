#!/usr/bin/env python3
"""Вырезает готовые здания из мира в .schem по сетке из slots.json.

Игра сама собирает джигсо-структуру из кусков — мы её потом просто
поднимаем из региональных файлов целиком, со всей начинкой.

    python tools/world2schem.py "<...>/saves/Каталог" -o <куда>
    python tools/world2schem.py --selftest

Порядок работы описан в tools/structure_catalog/README.md. Мир должен
быть суперплоским пресетом «Пустота»: тогда любой непустой блок —
это здание, и искать границы не нужно.
"""
import argparse, gzip, json, struct, sys, zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from nbt2schem import read_nbt, write_nbt, make_schem, STR, IARR, COMP, INT, state_name

AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")

# слои обычного суперплоского мира: они не часть здания и в схематику не идут
GROUND = {-64: "minecraft:bedrock", -63: "minecraft:dirt",
          -62: "minecraft:dirt", -61: "minecraft:grass_block"}


def read_region(path):
    """Отдаёт {(cx, cz): компаунд чанка} для одного файла .mca."""
    raw = path.read_bytes()
    if len(raw) < 8192:
        return {}
    rx, rz = (int(p) for p in path.stem.split(".")[1:3])
    out = {}
    for i in range(1024):
        off, cnt = struct.unpack(">I", raw[i * 4:i * 4 + 4])[0] >> 8, raw[i * 4 + 3]
        if not off or not cnt:
            continue
        start = off * 4096
        length = struct.unpack(">I", raw[start:start + 4])[0]
        comp = raw[start + 4]
        blob = raw[start + 5:start + 4 + length]
        if comp == 1:
            blob = gzip.decompress(blob)
        elif comp == 2:
            blob = zlib.decompress(blob)
        elif comp != 3:
            continue                       # lz4 и самодельные схемы сжатия не трогаем
        out[(rx * 32 + i % 32, rz * 32 + i // 32)] = read_nbt(blob)
    return out


def section_blocks(sec):
    """Отдаёт (имена палитры, список из 4096 индексов) или None, если секция пуста."""
    bs = sec.get("block_states")
    if bs is None:
        return None
    names = [state_name(e) for e in bs[1]["palette"][1][1]]
    if len(names) == 1:
        return None if names[0] in AIR else (names, [0] * 4096)
    longs = bs[1].get("data")
    if longs is None:
        return None
    longs = longs[1]
    bits = max(4, (len(names) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    idx = []
    for word in longs:
        word &= 0xFFFFFFFFFFFFFFFF
        for k in range(per):
            idx.append((word >> (k * bits)) & mask)
            if len(idx) == 4096:
                return names, idx
    idx += [0] * (4096 - len(idx))
    return names, idx


def collect(chunks, x0, x1, z0, z1, drop_ground=True):
    """Непустые блоки в коробке -> {(x, y, z): имя состояния}, плюс блок-сущности."""
    blocks, tiles = {}, []
    for (cx, cz), ch in chunks.items():
        bx, bz = cx * 16, cz * 16
        if bx > x1 or bx + 15 < x0 or bz > z1 or bz + 15 < z0:
            continue
        for sec in ch.get("sections", (0, (0, [])))[1][1]:
            got = section_blocks(sec)
            if got is None:
                continue
            names, idx = got
            sy = sec["Y"][1] * 16
            for i, p in enumerate(idx):
                n = names[p]
                if n in AIR:
                    continue
                x = bx + (i & 15)
                z = bz + ((i >> 4) & 15)
                if not (x0 <= x <= x1 and z0 <= z <= z1):
                    continue
                y = sy + (i >> 8)
                if drop_ground and GROUND.get(y) == n.split("[")[0]:
                    continue
                blocks[(x, y, z)] = n
        for be in ch.get("block_entities", (0, (0, [])))[1][1]:
            x, y, z = be["x"][1], be["y"][1], be["z"][1]
            if x0 <= x <= x1 and z0 <= z <= z1:
                tiles.append((x, y, z, be))
    return blocks, tiles


def carve(blocks, tiles):
    """Блоки -> компаунд Sponge v2, обрезанный по фактическим границам."""
    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    mnx, mny, mnz = min(xs), min(ys), min(zs)
    w, h, l = max(xs) - mnx + 1, max(ys) - mny + 1, max(zs) - mnz + 1

    pal = {"minecraft:air": 0}
    grid = [0] * (w * h * l)
    for (x, y, z), n in blocks.items():
        i = pal.setdefault(n, len(pal))
        grid[((y - mny) * l + (z - mnz)) * w + (x - mnx)] = i

    out = []
    for x, y, z, be in tiles:
        if (x, y, z) not in blocks:
            continue
        d = dict(be)
        for k in ("x", "y", "z", "keepPacked"):
            d.pop(k, None)
        d["Id"] = d.pop("id", (STR, blocks[(x, y, z)].split("[")[0]))
        d["Pos"] = (IARR, [x - mnx, y - mny, z - mnz])
        out.append(d)
    return make_schem(w, h, l, pal, grid, out), (w, h, l)


def main(argv):
    ap = argparse.ArgumentParser(description="здания из мира -> .schem")
    ap.add_argument("world", nargs="?", help="папка мира (та, где лежит region/)")
    ap.add_argument("-o", "--out", default="schem_buildings")
    ap.add_argument("--slots", default=str(Path(__file__).resolve().parent
                                           / "structure_catalog" / "slots.json"))
    ap.add_argument("--keep-ground", action="store_true",
                    help="не отсеивать слои суперплоского мира")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args(argv)
    if a.selftest:
        return selftest()
    if not a.world:
        ap.error("нужна папка мира или --selftest")

    cfg = json.loads(Path(a.slots).read_text(encoding="utf-8"))
    half = cfg["half"]
    region = Path(a.world) / "region"
    if not region.is_dir():
        print("нет папки region в " + a.world, file=sys.stderr)
        return 1

    chunks = {}
    for f in sorted(region.glob("r.*.mca")):
        chunks.update(read_region(f))
    print("чанков в мире: %d" % len(chunks))

    out = Path(a.out)
    done = empty = 0
    for s in cfg["slots"]:
        x0, x1 = s["x"] - half + 1, s["x"] + half
        z0, z1 = s["z"] - half + 1, s["z"] + half
        blocks, tiles = collect(chunks, x0, x1, z0, z1, not a.keep_ground)
        if not blocks:
            empty += 1
            continue
        comp, size = carve(blocks, tiles)
        ns, name = s["id"].split(":")
        dst = out / ns / (name + ".schem")
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(write_nbt("Schematic", comp))
        done += 1
        print("  %-46s %dx%dx%d" % (s["id"], size[0], size[1], size[2]))
    print("готово: %d   пусто (не сгенерилось): %d   -> %s" % (done, empty, out))
    return 0


def selftest():
    """Собирает регион из одного чанка с двумя блоками и проверяет выемку."""
    import tempfile
    from nbt2schem import Writer, SHORT, LIST, BYTE, LARR

    # секция с палитрой air/stone/oak_log, 5 бит на блок не нужно — хватит 4
    pal = [{"Name": (STR, "minecraft:air")},
           {"Name": (STR, "minecraft:stone")},
           {"Name": (STR, "minecraft:oak_log"), "Properties": (COMP, {"axis": (STR, "y")})}]
    idx = [0] * 4096
    idx[0 * 256 + 1 * 16 + 2] = 1          # x=2 z=1 y=0 -> камень
    idx[3 * 256 + 4 * 16 + 5] = 2          # x=5 z=4 y=3 -> дуб
    bits, per = 4, 16
    longs = []
    for start in range(0, 4096, per):
        word = 0
        for k in range(per):
            word |= idx[start + k] << (k * bits)
        longs.append(word - (1 << 64) if word >= (1 << 63) else word)

    chunk = {
        "DataVersion": (INT, 3955),
        "xPos": (INT, 0), "zPos": (INT, 0),
        "sections": (LIST, (COMP, [{
            "Y": (BYTE, 0),
            "block_states": (COMP, {"palette": (LIST, (COMP, pal)), "data": (LARR, longs)}),
        }])),
        "block_entities": (LIST, (COMP, [
            {"id": (STR, "minecraft:chest"), "x": (INT, 2), "y": (INT, 0), "z": (INT, 1)}
        ])),
    }
    blob = zlib.compress(gzip.decompress(write_nbt("", chunk)))
    w = Writer()
    header = bytearray(8192)
    header[0:4] = struct.pack(">I", (2 << 8) | 1)
    body = struct.pack(">I", len(blob) + 1) + b"\x02" + blob
    body += b"\x00" * (4096 - len(body) % 4096)

    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "r.0.0.mca"
        p.write_bytes(bytes(header) + body)
        chunks = read_region(p)
    assert list(chunks) == [(0, 0)], list(chunks)

    blocks, tiles = collect(chunks, 0, 15, 0, 15)
    assert blocks == {(2, 0, 1): "minecraft:stone",
                      (5, 3, 4): "minecraft:oak_log[axis=y]"}, blocks
    assert len(tiles) == 1

    comp, size = carve(blocks, tiles)
    assert size == (4, 4, 4), size                    # от (2,0,1) до (5,3,4)
    p2 = dict((n, i) for n, (_, i) in comp["Palette"][1].items())
    assert p2["minecraft:air"] == 0 and "minecraft:oak_log[axis=y]" in p2, p2

    back = read_nbt(write_nbt("Schematic", comp))     # переживает запись и чтение
    assert back["Width"][1] == 4 and back["Height"][1] == 4 and back["Length"][1] == 4
    be = back["BlockEntities"][1][1]
    assert be[0]["Id"][1] == "minecraft:chest" and be[0]["Pos"][1] == [0, 0, 0], be

    # отсев грунта: коренная порода на -64 и трава на -61 — это мир, а не здание
    gpal = [{"Name": (STR, "minecraft:air")}, {"Name": (STR, "minecraft:bedrock")},
            {"Name": (STR, "minecraft:grass_block"), "Properties": (COMP, {"snowy": (STR, "false")})},
            {"Name": (STR, "minecraft:cobblestone")}]
    gidx = [0] * 4096
    gidx[0 * 256 + 0 * 16 + 0] = 1                    # y=-64 коренная
    gidx[3 * 256 + 0 * 16 + 0] = 2                    # y=-61 трава
    gidx[4 * 256 + 0 * 16 + 0] = 3                    # y=-60 булыжник — стена дома
    glongs = []
    for start in range(0, 4096, 16):
        word = 0
        for k in range(16):
            word |= gidx[start + k] << (k * 4)
        glongs.append(word - (1 << 64) if word >= (1 << 63) else word)
    gchunk = {"sections": (LIST, (COMP, [{
        "Y": (BYTE, -4),
        "block_states": (COMP, {"palette": (LIST, (COMP, gpal)), "data": (LARR, glongs)}),
    }]))}
    kept, _ = collect({(0, 0): gchunk}, 0, 15, 0, 15)
    assert kept == {(0, -60, 0): "minecraft:cobblestone"}, kept
    whole, _ = collect({(0, 0): gchunk}, 0, 15, 0, 15, drop_ground=False)
    assert len(whole) == 3, whole
    print("самопроверка прошла")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
