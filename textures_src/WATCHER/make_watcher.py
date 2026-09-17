# -*- coding: utf-8 -*-
"""
Наблюдатель: геометрия и текстура из одного места.

Скрипт — единственный источник чисел. Из него выходят два файла:
  watcher.bbmodel  — чтобы открыть в Blockbench и подвигать руками
  watcher.png      — текстура, она же кладётся в ресурсы мода

Java-модель (client/WatcherModel.java) повторяет ТЕ ЖЕ числа. Если правишь
здесь — правь и там; расхождение видно сразу, силуэт разъедется.

Геометрия: ванильный игрок с тремя поправками, чтобы силуэт читался как
человеческий, но «что-то не так»:
  * ноги 13 вместо 12 — он на пиксель выше игрока
  * руки 13 вместо 12 и плечо поднято — кисти висят ниже бёдер
  * голова и тело подняты на пиксель следом за ногами
Наростов нет: он ещё человек.

Координаты здесь — как в Blockbench: Y вверх, отсчёт от пола.
"""
import base64
import json
import io
import os
import re
import random
import uuid

import PIL.Image

ЗДЕСЬ = os.path.dirname(os.path.abspath(__file__))
РЕСУРСЫ = os.path.join(
    ЗДЕСЬ, "..", "..", "plaguecore", "src", "main", "resources",
    "assets", "plaguecore", "textures", "entity", "watcher.png")

# ── геометрия ────────────────────────────────────────────────────────────
# (имя, uv_offset, from, to, origin)
ЧАСТИ = [
    ("head",      (0, 0),   (-4.0, 25.0, -4.0), (4.0, 33.0, 4.0),  (0.0, 25.0, 0.0)),
    ("body",      (16, 16), (-4.0, 13.0, -2.0), (4.0, 25.0, 2.0),  (0.0, 25.0, 0.0)),
    ("right_arm", (40, 16), (-8.0, 12.0, -2.0), (-4.0, 25.0, 2.0), (-5.0, 23.0, 0.0)),
    ("left_arm",  (32, 48), (4.0, 12.0, -2.0),  (8.0, 25.0, 2.0),  (5.0, 23.0, 0.0)),
    ("right_leg", (0, 16),  (-3.9, 0.0, -2.0),  (0.1, 13.0, 2.0),  (-1.9, 13.0, 0.0)),
    ("left_leg",  (16, 48), (-0.1, 0.0, -2.0),  (3.9, 13.0, 2.0),  (1.9, 13.0, 0.0)),
]


def bbmodel():
    элементы, дети = [], []
    for имя, uv, откуда, куда, опора in ЧАСТИ:
        ид = str(uuid.uuid4())
        элементы.append({
            "name": имя, "box_uv": True, "rescale": False, "locked": False,
            "render_order": "default", "allow_mirror_modeling": True,
            "from": list(откуда), "to": list(куда), "autouv": 0, "color": 0,
            "origin": list(опора), "uv_offset": list(uv),
            "faces": {}, "type": "cube", "uuid": ид,
        })
        дети.append(ид)
    return {
        "meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": True},
        "name": "watcher", "model_identifier": "", "visible_box": [1, 1, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [],
        "bedrock_animation_mode": "entity", "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64},
        "elements": элементы,
        "outliner": [{
            "name": "watcher", "origin": [0, 0, 0], "rotation": [0, 0, 0],
            "color": 0, "uuid": str(uuid.uuid4()), "export": True, "mirror_uv": False,
            "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
            "children": дети,
        }],
        "textures": [текстура_для_blockbench()],
    }


def текстура_для_blockbench():
    с_диска = os.path.join(ЗДЕСЬ, "watcher.png")
    данные = base64.b64encode(io.open(с_диска, "rb").read()).decode("ascii")
    return {
        "path": с_диска, "name": "watcher.png", "folder": "", "namespace": "",
        "id": "0", "width": 64, "height": 64, "uv_width": 64, "uv_height": 64,
        "particle": False, "use_as_default": True, "layers_enabled": False,
        "sync_to_project": "", "render_mode": "default", "render_sides": "auto",
        "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
        "frame_interpolate": False, "visible": True, "internal": True,
        "saved": False, "uuid": str(uuid.uuid4()),
        "relative_path": "./watcher.png",
        "source": "data:image/png;base64," + данные,
    }


# ── текстура ─────────────────────────────────────────────────────────────
# Почти силуэт: на расстоянии — тень человека, вблизи различимо лицо.
# Второй слой (шляпа, куртка, рукава) в модели выключен, поэтому вся
# текстура непрозрачная: прозрачность тут только создала бы дыры там,
# где удлинённые руки и ноги залезают на его UV.
ТКАНЬ = (22, 23, 26)
КОЖА = (46, 43, 40)
ГЛАЗ = (7, 8, 10)
ШУМ = 4


def текстура():
    сл = random.Random(20260917)
    холст = PIL.Image.new("RGBA", (64, 64), ТКАНЬ + (255,))
    пиксели = холст.load()

    def залить(x0, y0, w, h, цвет):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                д = сл.randint(-ШУМ, ШУМ)
                пиксели[x, y] = tuple(max(0, min(255, к + д)) for к in цвет) + (255,)

    залить(0, 0, 64, 64, ТКАНЬ)

    # Голова: box_uv 8×8×8 в (0,0) раскладывается на четыре боковые грани
    # в строках 8..16 — правая, передняя, левая, задняя, по восемь пикселей.
    залить(0, 8, 32, 8, КОЖА)   # все четыре стороны головы
    залить(8, 0, 8, 8, КОЖА)    # макушка
    залить(16, 0, 8, 8, КОЖА)   # низ (шея)

    # Лицо — передняя грань, x 8..16, y 8..16. Глаза посажены глубоко
    # и без бликов: блик читается как «живой», а он уже нет.
    for x in (10, 13):
        залить(x, 11, 2, 2, ГЛАЗ)
    залить(10, 14, 5, 1, tuple(к - 14 for к in КОЖА))  # рот, еле видно

    return холст


# ── сверка с Java ────────────────────────────────────────────────────────
# Числа живут в двух местах, и разойтись они могут молча: в Blockbench
# силуэт один, в игре другой, и заметишь ты это только на сервере ночью.
# Поэтому — пересчёт блокбенчевых координат в java-координаты и сравнение
# с тем, что реально написано в WatcherModel.java.
JAVA = os.path.join(
    ЗДЕСЬ, "..", "..", "plaguecore", "src", "main", "java",
    "dev", "denthe", "plaguecore", "client", "WatcherModel.java")

ПОЛ = 24.0  # мировой Y опоры корня: в java-моделях отсчёт идёт от него


def в_java():
    """{имя: (pivot_x, pivot_y, pivot_z, x0, y0, z0, w, h, d)} по нашим числам."""
    итог = {}
    for имя, _uv, откуда, куда, опора in ЧАСТИ:
        ox, oy, oz = опора
        итог[имя] = (
            ox, ПОЛ - oy, oz,
            откуда[0] - ox, oy - куда[1], откуда[2] - oz,
            куда[0] - откуда[0], куда[1] - откуда[1], куда[2] - откуда[2],
        )
    return итог


def из_java():
    исходник = io.open(os.path.normpath(JAVA), encoding="utf-8").read()
    ч = r"(-?\d+(?:\.\d+)?)F"
    шаблон = (r'addOrReplaceChild\("(\w+)".*?'
              r'addBox\(' + r",\s*".join([ч] * 6) + r'\).*?'
              r'PartPose\.offset\(' + r",\s*".join([ч] * 3) + r'\)')
    итог = {}
    for м in re.finditer(шаблон, исходник, re.S):
        имя = м.group(1)
        x0, y0, z0, w, h, d = (float(м.group(i)) for i in range(2, 8))
        px, py, pz = (float(м.group(i)) for i in range(8, 11))
        итог[имя] = (px, py, pz, x0, y0, z0, w, h, d)
    return итог


def сверить():
    ждём, есть = в_java(), из_java()
    assert set(ждём) == set(есть), "части разошлись: %s / %s" % (
        sorted(ждём), sorted(есть))
    for имя in ждём:
        assert ждём[имя] == есть[имя], "%s: скрипт %s, java %s" % (
            имя, ждём[имя], есть[имя])
    print("сверка с WatcherModel.java: %d частей сходятся" % len(ждём))


if __name__ == "__main__":
    т = текстура()
    т.save(os.path.join(ЗДЕСЬ, "watcher.png"))
    т.save(os.path.normpath(РЕСУРСЫ))
    with open(os.path.join(ЗДЕСЬ, "watcher.bbmodel"), "w", encoding="utf-8") as ф:
        json.dump(bbmodel(), ф, ensure_ascii=False)
    сверить()
    print("готово:", os.path.normpath(РЕСУРСЫ))
