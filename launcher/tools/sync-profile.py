#!/usr/bin/env python3
"""Раскладывает пак из репозитория в локальный профиль Modrinth.

    python launcher/tools/sync-profile.py            # профиль LMPCCHUMA
    python launcher/tools/sync-profile.py --profile ДРУГОЙ
    python launcher/tools/sync-profile.py --force    # копировать при живой игре

Копирует mods/*.jar и всё из launcher/pack-config/ (кроме README).
То же самое, что получают игроки через раздачу, — чтобы своя машина
не расходилась с паком.

Игра должна быть закрыта. Моды с конфигом в памяти (Atmospherics,
Particular, NeoForge-споки) при выходе перезаписывают файл своим
состоянием, и свежескопированный конфиг молча откатывается — поэтому
скрипт сам проверяет, не запущен ли клиент.
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROFILES = Path.home() / "AppData/Roaming/ModrinthApp/profiles"


def game_is_running() -> bool:
    try:
        out = subprocess.run(["tasklist", "/FI", "IMAGENAME eq javaw.exe"],
                             capture_output=True, text=True, timeout=20).stdout
    except (OSError, subprocess.SubprocessError):
        return False  # не смогли проверить — не мешаем работать
    return "javaw.exe" in out


def sync(profile: Path) -> int:
    copied = 0
    for src in sorted((ROOT / "mods").glob("*.jar")):
        dst = profile / "mods" / src.name
        if dst.exists() and dst.stat().st_size == src.stat().st_size \
                and dst.read_bytes() == src.read_bytes():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        print(f"мод      {src.name}")
        copied += 1

    # Скрипт только докладывает и обновляет. Джарники, которых нет в
    # репозитории, НЕ трогаем: mods/MODLIST.md отстаёт от реального состава
    # пака (Sinytra Connector и forgified-fabric-api там до сих пор числятся
    # удалёнными, а в профиле нужны), так что «нет в репозитории» не значит
    # «лишний». Расхождение показываем, решение — за владельцем.
    ours = {p.name for p in (ROOT / "mods").glob("*.jar")}
    extra = [p.name for p in sorted((profile / "mods").glob("*.jar")) if p.name not in ours]
    if extra:
        print("в профиле есть джарники, которых нет в репозитории (не трогаю):")
        for name in extra:
            # Две версии одного мода рядом — игра не запустится, это надо
            # заметить сразу, а не в логе краша.
            stem = name.rsplit("-", 1)[0]
            clash = any(o != name and o.rsplit("-", 1)[0] == stem for o in ours)
            print(f"  {name}" + ("   <-- СТАРАЯ ВЕРСИЯ, удали её вручную" if clash else ""))

    config_root = ROOT / "launcher" / "pack-config"
    for src in sorted(config_root.rglob("*")):
        if not src.is_file() or src.name == "README.md":
            continue
        dst = profile / src.relative_to(config_root)
        if dst.exists() and dst.read_bytes() == src.read_bytes():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        print(f"конфиг   {src.relative_to(config_root)}")
        copied += 1

    return copied


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--profile", default="LMPCCHUMA")
    p.add_argument("--force", action="store_true")
    args = p.parse_args()

    target = PROFILES / args.profile
    if not target.is_dir():
        sys.exit(f"нет профиля {target}")

    if game_is_running() and not args.force:
        sys.exit("Minecraft запущен — закрой игру, иначе она перезапишет конфиги "
                 "своим состоянием при выходе. Скопировать всё равно: --force")

    n = sync(target)
    print(f"готово, обновлено файлов: {n}" if n else "всё уже совпадает")
