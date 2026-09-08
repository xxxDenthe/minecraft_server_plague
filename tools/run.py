#!/usr/bin/env python3
"""Меню для тех, кому терминал не родной: ставит датапак в мир
и достаёт из мира готовые здания. Запускается из ЗАПУСТИ_МЕНЯ.bat.
"""
import os
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def game_folders():
    """Похожие на игровые папки: те, где есть saves."""
    app = Path(os.environ.get("APPDATA", ""))
    seen, out = set(), []
    for p in [app / ".minecraft", app / "LMPC" / "instance",
              app / "PlagueLauncher" / "instance", Path.home() / "curseforge"]:
        if (p / "saves").is_dir() and p not in seen:
            seen.add(p)
            out.append(p)
    return out


def choose(title, options, fmt=str):
    if not options:
        return None
    print("\n" + title)
    for i, o in enumerate(options, 1):
        print("  %d) %s" % (i, fmt(o)))
    while True:
        ans = input("Номер (или Enter чтобы отменить): ").strip()
        if not ans:
            return None
        if ans.isdigit() and 1 <= int(ans) <= len(options):
            return options[int(ans) - 1]
        print("Не понял. Введи число от 1 до %d." % len(options))


def normalize_game(raw):
    """Прощаем то, что реально перетаскивают: саму папку игры, saves,
    отдельный мир или его datapacks. Возвращает папку игры или None."""
    raw = raw.strip().strip('"').strip()
    if not raw:
        return None
    p = Path(raw)
    if p.name.lower() == "datapacks" and (p.parent / "level.dat").is_file():
        p = p.parent
    elif p.name.lower() in ("mods", "config", "resourcepacks", "shaderpacks"):
        p = p.parent                                 # перетащили соседнюю папку
    if (p / "level.dat").is_file():                  # кинули сам мир
        p = p.parent.parent
    elif p.name.lower() == "saves" and p.is_dir():   # кинули saves
        p = p.parent
    if (p / "saves").is_dir():
        return p
    if p.is_dir() and ((p / "mods").is_dir() or (p / "options.txt").is_file()):
        (p / "saves").mkdir(exist_ok=True)           # игра ещё не создавала миров
        return p
    return None


def pick_game():
    found = game_folders()
    if found:
        print("\nПохоже на папки игры (если нужной тут нет — не страшно):")
        for f in found:
            print("   %s" % f)
    print("\nНужна папка игры — та, внутри которой лежат mods, config и saves.")
    print("Проще всего: открой её в проводнике и перетащи сюда мышкой.")
    while True:
        raw = input("\nПуть к папке игры (Enter чтобы выйти): ")
        if not raw.strip():
            return None
        g = normalize_game(raw)
        if g:
            print("Беру: %s" % g)
            return g
        print("Не похоже на папку игры: там нет ни saves, ни mods.")
        print("Пример правильного пути: C:\\Users\\Имя\\AppData\\Roaming\\.minecraft")


def pick_world(game):
    worlds = sorted([w for w in (game / "saves").iterdir() if (w / "level.dat").is_file()])
    if not worlds:
        print("В %s нет ни одного мира." % (game / "saves"))
        return None
    def label(w):
        mark = "  <- датапак уже стоит" if (w / "datapacks" / "structure_catalog").is_dir() else ""
        return w.name + mark
    return choose("Какой мир?", worlds, label)


def install(game):
    world = pick_world(game)
    if not world:
        return
    dst = world / "datapacks" / "structure_catalog"
    if dst.exists():
        shutil.rmtree(dst)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(HERE / "structure_catalog", dst)
    print("\nГотово. Датапак лежит в:\n  %s" % dst)
    print("\nТеперь зайди в мир и напиши /reload,")
    print("потом по очереди десять пар команд:")
    print("  /function lmpc_struct:go_1")
    print("  /function lmpc_struct:place_1")
    print("...и так до go_10 / place_10. Потом выйди из мира и вернись сюда.")


def extract(game):
    world = pick_world(game)
    if not world:
        return
    out = game / "config" / "worldedit" / "schematics" / "buildings"
    print("\nЧитаю мир, это может занять пару минут...\n")
    import world2schem
    code = world2schem.main([str(world), "-o", str(out)])
    if code == 0:
        print("\nЗдания лежат в:\n  %s" % out)
        print("\nВ игре: Axiom -> File -> Import Schematic -> выбрать здание -> Create Blueprint.")


def main():
    print("=" * 60)
    print("  Здания из модов -> схематики для Axiom")
    print("=" * 60)
    game = pick_game()
    if not game:
        return
    while True:
        act = choose("Что делать?  (папка игры: %s)" % game, [
            ("install", "Положить датапак в мир (шаг 2)"),
            ("extract", "Достать здания из мира (шаг 5)"),
            ("chdir", "Указать другую папку игры"),
        ], lambda t: t[1])
        if act is None:
            return
        if act[0] == "install":
            install(game)
        elif act[0] == "extract":
            extract(game)
        else:
            game = pick_game() or game


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except Exception as e:
        print("\nОшибка: %s: %s" % (type(e).__name__, e))
    if not os.environ.get("LMPC_FROM_BAT"):   # из RUN.bat окно держит сам pause
        try:
            input("\nНажми Enter чтобы закрыть.")
        except EOFError:
            pass
