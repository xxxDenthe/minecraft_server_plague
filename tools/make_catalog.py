#!/usr/bin/env python3
"""Строит датапак-каталог: расставляет все модовые структуры по сетке
с известными координатами и пишет slots.json для tools/world2schem.py.

    python tools/make_catalog.py mods -o tools/structure_catalog

Сетка: партия из 16 структур 4x4 с шагом 256 блоков, партии разнесены
по X на 2048. У каждой партии две функции: go_N телепортирует в центр
партии, place_N ставит все 16. Координаты абсолютные — иначе скрипт
выемки не знал бы, где что искать.
"""
import argparse, json, re, zipfile
from pathlib import Path

SPACING = 256          # шаг между строениями внутри партии
BATCH_STRIDE = 2048    # расстояние между центрами партий по X
PLACE_Y = 64           # высота постановки; у джигсо-структур игра её переопределяет
OFFS = [-384, -128, 128, 384]

STRUCT_RE = re.compile(r"^data/([^/]+)/worldgen/structure/([^/]+)\.json$")


def find_structures(mods_dir):
    found = []
    for jar in sorted(Path(mods_dir).glob("*.jar")):
        try:
            with zipfile.ZipFile(jar) as z:
                names = z.namelist()
        except zipfile.BadZipFile:
            continue
        for n in names:
            m = STRUCT_RE.match(n)
            if m:
                found.append((m.group(1) + ":" + m.group(2), jar.name))
    return sorted(set(found))


def main():
    ap = argparse.ArgumentParser(description="датапак-каталог строений")
    ap.add_argument("mods", help="папка с джарниками модов")
    ap.add_argument("-o", "--out", default="tools/structure_catalog")
    a = ap.parse_args()

    structs = find_structures(a.mods)
    out = Path(a.out)
    fn = out / "data" / "lmpc_struct" / "function"
    for old in fn.glob("*.mcfunction"):
        old.unlink()
    fn.mkdir(parents=True, exist_ok=True)
    (out / "pack.mcmeta").write_text(json.dumps({
        "pack": {"pack_format": 48,
                 "description": "Каталог строений: ставит модовые структуры по известной сетке"}
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    slots, batches = [], []
    for i, (sid, jar) in enumerate(structs):
        b, k = divmod(i, 16)
        r, c = divmod(k, 4)
        slot = {"id": sid, "jar": jar, "batch": b + 1,
                "x": b * BATCH_STRIDE + OFFS[r], "z": OFFS[c]}
        slots.append(slot)
        while len(batches) <= b:
            batches.append([])
        batches[b].append(slot)

    for b, items in enumerate(batches, 1):
        cx = (b - 1) * BATCH_STRIDE
        (fn / ("go_%d.mcfunction" % b)).write_text(
            "gamemode spectator @s\ntp @s %d 100 0\n" % cx, encoding="utf-8")
        lines = ["place structure %s %d %d %d" % (s["id"], s["x"], PLACE_Y, s["z"])
                 for s in items]
        (fn / ("place_%d.mcfunction" % b)).write_text("\n".join(lines) + "\n", encoding="utf-8")

    (out / "slots.json").write_text(json.dumps(
        {"spacing": SPACING, "half": SPACING // 2, "slots": slots},
        ensure_ascii=False, indent=1), encoding="utf-8")

    readme = ["# Каталог строений", "",
              "Датапак ставит %d модовых строений по сетке с известными координатами."
              % len(slots),
              "Дальше `tools/world2schem.py` вырезает их из мира в готовые `.schem`.", "",
              "## Порядок", "",
              "1. Новый мир: тип **Суперплоский**, пресет по умолчанию (classic flat),",
              "   режим творческий, читы включены. Дальность прорисовки — **24 чанка",
              "   или больше**, иначе дальние слоты окажутся в непрогруженных чанках",
              "   и `/place` откажется работать.",
              "   Слои плоского мира скрипт выемки отбрасывает сам.",
              "2. Скопировать эту папку в `saves/<мир>/datapacks/structure_catalog/`, затем `/reload`.",
              "3. Для каждой партии от 1 до %d выполнить две команды подряд:" % len(batches), "",
              "   ```",
              "   /function lmpc_struct:go_1",
              "   /function lmpc_struct:place_1",
              "   ```", "",
              "   Между `go` и `place` подождать пару секунд, пока прогрузятся чанки.",
              "4. Выйти из мира (важно: иначе куски не сохранятся на диск).",
              "5. `python tools/world2schem.py \"<путь к saves/мир>\" -o <куда>`", "",
              "## Партии", ""]
    for b, items in enumerate(batches, 1):
        readme.append("### %d — `/function lmpc_struct:go_%d` + `place_%d`" % (b, b, b))
        readme.append("```")
        readme += [s["id"] for s in items]
        readme.append("```")
        readme.append("")
    (out / "README.md").write_text("\n".join(readme), encoding="utf-8")

    print("структур: %d   партий: %d   -> %s" % (len(slots), len(batches), out))


if __name__ == "__main__":
    main()
