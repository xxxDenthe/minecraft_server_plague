"""Проверка и лечение блюпринтов Axiom (.bp) под наш пак 1.21.1.

Блюпринт с axiomassets.net может быть собран на версии игры новее нашей.
Axiom читает палитру строго (`getOrThrow`) и роняет клиент прямо в списке
чертежей, ещё до вставки — так упал `Raised House Study No.3.bp`
(`minecraft:dark_oak_shelf`, полки появились только в 1.21.9).

    python tools/bp_doctor.py check <файл.bp> [...]   какие блоки пак не знает
    python tools/bp_doctor.py fix   <файл.bp> [...]   заменить их на воздух

Список известных блоков собирается из самих джарников: `blockstates/*.json`
у ванили и у всех модов пака. Ничего не скачивает, работает офлайн.
"""

import glob
import gzip
import os
import re
import shutil
import struct
import sys
import zipfile

MAGIC = b"\x0a\xe5\xbb\x36"

PACK_MODS = os.path.expandvars(
    r"%APPDATA%\ModrinthApp\profiles\LMPCCHUMA\mods"
)
# Ванильные ассеты 1.21.1 — из дев-окружения любого нашего мода
VANILLA_GLOB = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "*", "build", "moddev", "artifacts", "*client-extra*.jar",
)

# ---------------------------------------------------------------- NBT

FIXED = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}


def _read(buf, pos, tag):
    """Возвращает (значение, новая позиция). Скаляры хранятся сырыми байтами."""
    if tag in FIXED:
        n = FIXED[tag]
        return ("raw", buf[pos:pos + n]), pos + n
    if tag == 8:
        n = struct.unpack_from(">H", buf, pos)[0]
        return ("str", buf[pos + 2:pos + 2 + n]), pos + 2 + n
    if tag in (7, 11, 12):
        width = {7: 1, 11: 4, 12: 8}[tag]
        n = struct.unpack_from(">i", buf, pos)[0]
        end = pos + 4 + n * width
        return ("arr", tag, buf[pos + 4:end]), end
    if tag == 9:
        elem = buf[pos]
        n = struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        items = []
        for _ in range(n):
            value, pos = _read(buf, pos, elem)
            items.append(value)
        return ("list", elem, items), pos
    if tag == 10:
        fields = []
        while True:
            t = buf[pos]
            pos += 1
            if t == 0:
                return ("comp", fields), pos
            n = struct.unpack_from(">H", buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n]
            pos += 2 + n
            value, pos = _read(buf, pos, t)
            fields.append([name, t, value])
    raise ValueError("неизвестный тег NBT: %d" % tag)


def _write(out, value):
    kind = value[0]
    if kind == "raw":
        out += value[1]
    elif kind == "str":
        out += struct.pack(">H", len(value[1])) + value[1]
    elif kind == "arr":
        width = {7: 1, 11: 4, 12: 8}[value[1]]
        out += struct.pack(">i", len(value[2]) // width) + value[2]
    elif kind == "list":
        out += bytes([value[1]]) + struct.pack(">i", len(value[2]))
        for item in value[2]:
            _write(out, item)
    elif kind == "comp":
        for name, t, item in value[1]:
            out += bytes([t]) + struct.pack(">H", len(name)) + name
            _write(out, item)
        out += b"\x00"
    return out


def nbt_load(buf):
    name_len = struct.unpack_from(">H", buf, 1)[0]
    value, _ = _read(buf, 3 + name_len, 10)
    return value


def nbt_dump(root):
    return bytes(_write(bytearray(b"\x0a\x00\x00"), root))


def get(comp, key):
    for name, _, value in comp[1]:
        if name == key:
            return value
    return None


# ---------------------------------------------------------------- .bp

def bp_load(path):
    """-> (сырой файл, смещение секции блоков, длина, распакованный NBT)."""
    data = open(path, "rb").read()
    if data[:4] != MAGIC:
        raise ValueError("не похоже на блюпринт Axiom: %s" % path)
    pos = 8 + struct.unpack_from(">i", data, 4)[0]      # шапка
    pos += 4 + struct.unpack_from(">i", data, pos)[0]   # превьюшка PNG
    size = struct.unpack_from(">i", data, pos)[0]
    body = gzip.decompress(data[pos + 4:pos + 4 + size])
    return data, pos, size, nbt_load(body)


def bp_save(path, data, pos, size, root):
    blob = gzip.compress(nbt_dump(root))
    new = data[:pos] + struct.pack(">i", len(blob)) + blob + data[pos + 4 + size:]
    open(path, "wb").write(new)


def palettes(node):
    """Все списки состояний блоков: элементы со строковым полем Name."""
    kind = node[0]
    if kind == "list":
        if node[1] == 10 and node[2] and get(node[2][0], b"Name"):
            yield node
        for item in node[2]:
            yield from palettes(item)
    elif kind == "comp":
        for _, _, value in node[1]:
            yield from palettes(value)


def block_ids(node):
    for palette in palettes(node):
        for state in palette[2]:
            yield state, get(state, b"Name")[1].decode()


# ------------------------------------------------------- список блоков

def known_blocks():
    jars = sorted(glob.glob(os.path.join(PACK_MODS, "*.jar")))
    jars += sorted(glob.glob(VANILLA_GLOB))
    if not jars:
        sys.exit("не нашёл джарников: %s" % PACK_MODS)
    ids, entry = set(), re.compile(r"assets/([^/]+)/blockstates/(.+)\.json$")
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    m = entry.match(name)
                    if m:
                        ids.add("%s:%s" % m.groups())
        except zipfile.BadZipFile:
            print("пропускаю битый джарник: %s" % os.path.basename(jar))
    return ids


# ---------------------------------------------------------------- CLI

def selftest():
    """NBT обязан пережить чтение и запись без потерь — на этом держится fix."""
    sample = ("comp", [[b"Palette", 9, ("list", 10, [
        ("comp", [[b"Name", 8, ("str", b"minecraft:stone")]]),
    ])]])
    assert nbt_load(nbt_dump(sample)) == sample
    assert [i for _, i in block_ids(sample)] == ["minecraft:stone"]
    print("самопроверка пройдена")


def main(argv):
    if len(argv) == 2 and argv[1] == "selftest":
        return selftest()
    if len(argv) < 3 or argv[1] not in ("check", "fix"):
        sys.exit(__doc__)
    command, known = argv[1], known_blocks()
    print("в паке известно блоков: %d" % len(known))
    bad_total = 0
    for path in argv[2:]:
        data, pos, size, root = bp_load(path)
        bad = {i for _, i in block_ids(root) if i not in known}
        bad_total += len(bad)
        print("\n%s: %s" % (os.path.basename(path),
                            ", ".join(sorted(bad)) if bad else "чисто"))
        if not bad or command == "check":
            continue
        for state, ident in block_ids(root):
            if ident in bad:
                state[1][:] = [[b"Name", 8, ("str", b"minecraft:air")]]
        # ponytail: тайлы (BlockEntities) не трогаем — игра сама пропускает
        # неизвестный id с предупреждением; чистить, если Axiom всё же ругнётся
        # оригинал уносим из папки блюпринтов: в ней он бы так же ронял Axiom
        backup = os.path.dirname(os.path.abspath(path)) + "_backup"
        os.makedirs(backup, exist_ok=True)
        shutil.copyfile(path, os.path.join(backup, os.path.basename(path)))
        bp_save(path, data, pos, size, root)
        print("  заменено на воздух, оригинал в %s" % backup)
    return 1 if bad_total and command == "check" else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
