#!/usr/bin/env python3
"""Пересобирает launcher/pack-build/ из игрового профиля Modrinth.

    python launcher/tools/build-pack-from-profile.py
    python launcher/tools/build-pack-from-profile.py --profile ДРУГОЙ

Обратное к sync-profile.py: тот раскладывает репозиторий в профиль,
этот собирает пак из профиля. Профиль владельца — единственное место,
где состав пака есть целиком: mods/ репозитория отстаёт (см. MODLIST),
а pack-build/ не версионируется и легко протухает.

Что не едет игрокам:
  - личное и временное: закладки JEI по мирам, кэши, логи, .bak;
  - кэш скинов CustomSkinLoader — нужен только сам CustomSkinLoader.json;
  - имена вне ASCII: bsdtar читает список файлов в кодировке консоли
    Windows и на кириллице молча теряет строку (см. archive.js).

Поверх профиля накатывается launcher/pack-config/ — эталонные конфиги.
Это обязательно: моды с конфигом в памяти при выходе из игры
переписывают файл своим состоянием, и в профиле лежит уже не эталон.
"""

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "launcher" / "pack-build"
PROFILES = Path.home() / "AppData/Roaming/ModrinthApp/profiles"

DIRS = ["mods", "config", "kubejs", "resourcepacks", "defaultconfigs",
        "datapacks", "shaderpacks", "moonlight-global-datapacks"]
# Пути от корня инстанса, которые не раздаём целиком.
SKIP_DIRS = ["config/jei/world", "config/obscuria/cache", "config/axiom"]
SKIP_EXT = (".bak", ".disabled", ".log", ".tmp", ".old")
SKIP_NAMES = {"MODLIST.md", "desktop.ini", "Thumbs.db"}
# Инструмент строителя: стоит у владельца и на сервере, игрокам не едет.
SKIP_MODS = ("axiom-",)


def wanted(rel: str, name: str) -> bool:
    if name in SKIP_NAMES or name.lower().endswith(SKIP_EXT):
        return False
    if any(rel == d or rel.startswith(d + "/") for d in SKIP_DIRS):
        return False
    if rel.startswith("mods/"):
        if not name.endswith(".jar"):
            return False
        if name.lower().startswith(SKIP_MODS):
            return False
    return True


def collect(profile: Path) -> dict[str, Path]:
    """rel -> откуда взять. Профиль, затем pack-config поверх него."""
    files: dict[str, Path] = {}
    for d in DIRS:
        for src in sorted((profile / d).rglob("*")):
            if not src.is_file():
                continue
            rel = src.relative_to(profile).as_posix()
            if wanted(rel, src.name):
                files[rel] = src

    skin = profile / "CustomSkinLoader" / "CustomSkinLoader.json"
    if skin.is_file():
        files["CustomSkinLoader/CustomSkinLoader.json"] = skin

    config_root = ROOT / "launcher" / "pack-config"
    for src in sorted(config_root.rglob("*")):
        if src.is_file() and src.name != "README.md":
            files[src.relative_to(config_root).as_posix()] = src
    return files


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--profile", default="LMPCCHUMA")
    args = p.parse_args()

    profile = PROFILES / args.profile
    if not profile.is_dir():
        return f"нет профиля {profile}"

    files = collect(profile)

    bad = sorted(r for r in files if not r.isascii())
    if bad:
        print("имена вне ASCII — выкладка на них падает, убери их из профиля:")
        for r in bad:
            print(f"  {r}")
        return 1

    if OUT.exists():
        shutil.rmtree(OUT)
    for rel, src in files.items():
        dst = OUT / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    # Проверка: то, что собрали, и лежит на диске, побайтово тем же размером.
    for rel, src in files.items():
        dst = OUT / rel
        assert dst.is_file() and dst.stat().st_size == src.stat().st_size, rel

    for d in sorted({r.split("/")[0] for r in files}):
        n = sum(1 for r in files if r.split("/")[0] == d)
        size = sum((OUT / r).stat().st_size for r in files if r.split("/")[0] == d)
        print(f"  {d:<28} {n:>4} файлов  {size / 1048576:>7.1f} МБ")
    print(f"{OUT}\nвсего {len(files)} файлов")
    return 0


if __name__ == "__main__":
    sys.exit(main())
