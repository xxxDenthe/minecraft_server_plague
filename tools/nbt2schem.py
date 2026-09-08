#!/usr/bin/env python3
"""Конвертер ванильных структур .nbt -> Sponge schematic v2 (.schem).

Читает .nbt из джарника мода, папки или одного файла и кладёт .schem,
которые понимают Axiom (File -> Import Schematic) и WorldEdit (//schem load).

    python tools/nbt2schem.py mods/dungeons-and-taverns-v4.4.4.jar -o out
    python tools/nbt2schem.py mods/t_and_t-fabric-neoforge-1.13.11.jar -o out --min-blocks 200
    python tools/nbt2schem.py --selftest

Блоки structure_void превращаются в воздух: иначе при вставке в мир
появились бы невидимые блоки. Сущности (мобы, картины) не переносятся —
в ванильных структурах их почти не бывает, а формат v2 требует для них
отдельную возню.
"""
import argparse, gzip, struct, sys, zipfile
from pathlib import Path

# ---------- NBT: значение = (тип, полезная нагрузка) ----------
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)


class Reader:
    def __init__(self, buf):
        self.b, self.i = buf, 0

    def take(self, n):
        v = self.b[self.i:self.i + n]
        if len(v) != n:
            raise EOFError("файл обрывается")
        self.i += n
        return v

    def num(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.num("H")).decode("utf-8", "replace")

    def payload(self, t):
        if t == BYTE:
            return self.num("b")
        if t == SHORT:
            return self.num("h")
        if t == INT:
            return self.num("i")
        if t == LONG:
            return self.num("q")
        if t == FLOAT:
            return self.num("f")
        if t == DOUBLE:
            return self.num("d")
        if t == BARR:
            return self.take(self.num("i"))
        if t == STR:
            return self.string()
        if t == LIST:
            et, n = self.num("b"), self.num("i")
            return (et, [self.payload(et) for _ in range(n)])
        if t == COMP:
            out = {}
            while True:
                et = self.num("b")
                if et == END:
                    return out
                name = self.string()          # имя строго до значения: иначе порядок чтения плывёт
                out[name] = (et, self.payload(et))
        if t == IARR:
            n = self.num("i")
            return [self.num("i") for _ in range(n)]
        if t == LARR:
            n = self.num("i")
            return [self.num("q") for _ in range(n)]
        raise ValueError("неизвестный тег " + str(t))


class Writer:
    def __init__(self):
        self.o = bytearray()

    def num(self, fmt, v):
        self.o += struct.pack(">" + fmt, v)

    def string(self, s):
        e = s.encode("utf-8")
        self.num("H", len(e))
        self.o += e

    def payload(self, t, v):
        if t == BYTE:
            self.num("b", v)
        elif t == SHORT:
            self.num("h", v)
        elif t == INT:
            self.num("i", v)
        elif t == LONG:
            self.num("q", v)
        elif t == FLOAT:
            self.num("f", v)
        elif t == DOUBLE:
            self.num("d", v)
        elif t == BARR:
            self.num("i", len(v))
            self.o += bytes(v)
        elif t == STR:
            self.string(v)
        elif t == LIST:
            et, items = v
            self.num("b", et if items else END)
            self.num("i", len(items))
            for it in items:
                self.payload(et, it)
        elif t == COMP:
            for name, (et, ev) in v.items():
                self.num("b", et)
                self.string(name)
                self.payload(et, ev)
            self.num("b", END)
        elif t == IARR:
            self.num("i", len(v))
            for x in v:
                self.num("i", x)
        elif t == LARR:
            self.num("i", len(v))
            for x in v:
                self.num("q", x)
        else:
            raise ValueError("неизвестный тег " + str(t))


def read_nbt(raw):
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    t = r.num("b")
    if t != COMP:
        raise ValueError("корень не compound")
    r.string()
    return r.payload(COMP)


def write_nbt(name, comp):
    w = Writer()
    w.num("b", COMP)
    w.string(name)
    w.payload(COMP, comp)
    return gzip.compress(bytes(w.o))


def varint(out, v):
    while True:
        b = v & 0x7F
        v >>= 7
        out.append(b | (0x80 if v else 0))
        if not v:
            return


# ---------- собственно конвертация ----------
def state_name(entry):
    name = entry["Name"][1]
    props = entry.get("Properties")
    if not props or not props[1]:
        return name
    inner = ",".join(k + "=" + v[1] for k, v in sorted(props[1].items()))
    return name + "[" + inner + "]"


def convert(root):
    """Ванильная структура -> компаунд Sponge v2 и число непустых блоков."""
    w, h, l = root["size"][1][1]
    if w <= 0 or h <= 0 or l <= 0:
        return None, 0
    if "palette" in root:
        entries = root["palette"][1][1]
    elif "palettes" in root:
        entries = root["palettes"][1][1][0][1]
    else:
        raise ValueError("нет палитры")
    names = [state_name(e) for e in entries]

    pal = {}                       # имя состояния -> индекс в схематике

    def idx(n):
        return pal.setdefault(n, len(pal))

    air = idx("minecraft:air")     # индекс 0, им же заполняем дыры
    grid = [air] * (w * h * l)
    tiles, solid = [], 0
    for blk in root["blocks"][1][1]:
        x, y, z = blk["pos"][1][1]
        if not (0 <= x < w and 0 <= y < h and 0 <= z < l):
            continue
        n = names[blk["state"][1]]
        if n.startswith("minecraft:structure_void"):
            n = "minecraft:air"
        i = idx(n)
        grid[(y * l + z) * w + x] = i
        if i != air:
            solid += 1
        if "nbt" in blk:
            be = dict(blk["nbt"][1])
            for k in ("x", "y", "z"):
                be.pop(k, None)
            be["Id"] = be.pop("id", (STR, n.split("[")[0]))
            be["Pos"] = (IARR, [x, y, z])
            tiles.append(be)

    return make_schem(w, h, l, pal, grid, tiles, root.get("DataVersion", (INT, 3955))[1]), solid


def make_schem(w, h, l, pal, grid, tiles, data_version=3955):
    """Компаунд Sponge v2. pal: имя состояния -> индекс, grid: индексы в порядке y,z,x."""
    data = bytearray()
    for v in grid:
        varint(data, v)
    return {
        "Version": (INT, 2),
        "DataVersion": (INT, data_version),
        "Width": (SHORT, w), "Height": (SHORT, h), "Length": (SHORT, l),
        "Offset": (IARR, [0, 0, 0]),
        "PaletteMax": (INT, len(pal)),
        "Palette": (COMP, dict((n, (INT, i)) for n, i in pal.items())),
        "BlockData": (BARR, bytes(data)),
        "BlockEntities": (LIST, (COMP, tiles)),
        "Metadata": (COMP, {"WEOffsetX": (INT, 0), "WEOffsetY": (INT, 0), "WEOffsetZ": (INT, 0)}),
    }


def sources(target):
    """Отдаёт пары (относительный путь без расширения, байты .nbt)."""
    p = Path(target)
    if p.suffix == ".jar":
        with zipfile.ZipFile(p) as z:
            for n in z.namelist():
                if n.endswith(".nbt") and "/structure/" in n:
                    head, tail = n.split("/structure/", 1)
                    yield head.split("/")[-1] + "/" + tail[:-4], z.read(n)
    elif p.is_dir():
        for f in sorted(p.rglob("*.nbt")):
            yield str(f.relative_to(p).with_suffix("")).replace("\\", "/"), f.read_bytes()
    else:
        yield p.stem, p.read_bytes()


def main(argv):
    ap = argparse.ArgumentParser(description="ванильные .nbt -> Sponge .schem")
    ap.add_argument("target", nargs="?", help="джарник мода, папка или один .nbt")
    ap.add_argument("-o", "--out", default="schem_out", help="куда класть (по умолчанию schem_out)")
    ap.add_argument("--min-blocks", type=int, default=0, help="пропускать структуры мельче N блоков")
    ap.add_argument("--selftest", action="store_true", help="самопроверка без файлов")
    a = ap.parse_args(argv)
    if a.selftest:
        return selftest()
    if not a.target:
        ap.error("нужен путь или --selftest")

    out = Path(a.out)
    done = skipped = failed = 0
    for rel, raw in sources(a.target):
        try:
            comp, solid = convert(read_nbt(raw))
        except Exception as e:
            failed += 1
            print("  ! " + rel + ": " + str(e), file=sys.stderr)
            continue
        if comp is None or solid < a.min_blocks:
            skipped += 1
            continue
        dst = out / (rel + ".schem")
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(write_nbt("Schematic", comp))
        done += 1
    print("готово: %d   пропущено мелких: %d   ошибок: %d   -> %s" % (done, skipped, failed, out))
    return 0 if not failed else 1


def selftest():
    """Собирает структуру 2x1x2 из камня и дуба, гоняет через конвертер и проверяет."""
    src = {
        "DataVersion": (INT, 3955),
        "size": (LIST, (INT, [2, 1, 2])),
        "palette": (LIST, (COMP, [
            {"Name": (STR, "minecraft:stone")},
            {"Name": (STR, "minecraft:oak_log"), "Properties": (COMP, {"axis": (STR, "y")})},
            {"Name": (STR, "minecraft:structure_void")},
        ])),
        "blocks": (LIST, (COMP, [
            {"pos": (LIST, (INT, [0, 0, 0])), "state": (INT, 0)},
            {"pos": (LIST, (INT, [1, 0, 1])), "state": (INT, 1)},
            {"pos": (LIST, (INT, [1, 0, 0])), "state": (INT, 2)},
            {"pos": (LIST, (INT, [0, 0, 1])), "state": (INT, 0),
             "nbt": (COMP, {"id": (STR, "minecraft:chest"), "x": (INT, 0)})},
        ])),
    }
    comp, solid = convert(src)
    assert solid == 3, solid                                   # камень, дуб, камень-сундук
    assert comp["Width"][1] == 2 and comp["Length"][1] == 2 and comp["Height"][1] == 1

    pal = dict((n, i) for n, (_, i) in comp["Palette"][1].items())
    assert pal["minecraft:air"] == 0, pal                       # воздух всегда нулевой
    assert "minecraft:oak_log[axis=y]" in pal, pal              # свойства попали в имя
    assert "minecraft:structure_void" not in pal, pal           # пустота стала воздухом

    order = list(comp["BlockData"][1])                          # порядок y,z,x; индексы < 128
    assert order == [pal["minecraft:stone"], pal["minecraft:air"],
                     pal["minecraft:stone"], pal["minecraft:oak_log[axis=y]"]], order

    be = comp["BlockEntities"][1][1]
    assert len(be) == 1 and be[0]["Id"][1] == "minecraft:chest" and be[0]["Pos"][1] == [0, 0, 1]
    assert "x" not in be[0]                                     # мировые координаты выкинуты

    back = read_nbt(write_nbt("Schematic", comp))               # переживает запись и чтение
    assert back["Palette"][1] == comp["Palette"][1]
    assert back["BlockData"][1] == comp["BlockData"][1]
    print("самопроверка прошла")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
