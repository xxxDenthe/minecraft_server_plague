"""Генератор города для minecraft_server_plague.

ТЗ: docs/superpowers/specs/2026-09-04-gorod-koncept-design.md
Палитра: docs/superpowers/notes/2026-09-03-palitra-chumy.md (город — ТЁПЛАЯ
противоположность холодной серой чуме, холодные ванильные серые не используем).

Пишет plague_city.schem (Sponge, DataVersion 1.21.1). Вставка: //paste -a
или импорт в Axiom. Только ванильные блоки — мебель handcrafted/supplementaries
ставится сверху руками, чтобы схема грузилась даже без модов.

Ограничение по заказу: 150x150 (в ТЗ ориентир 300-400 — см. отчёт).
"""
import math
import random

import mcschematic

SIZE = 150
CX = CZ = SIZE // 2
GY = 2                                  # верхний блок земли

R_SQUARE = 13                           # площадь
R_ROAD_IN, R_ROAD_OUT = 43, 48          # кольцевая улица
R_PLOT_IN, R_PLOT_OUT = 50, 67          # пояс жилых участков
R_WALL_IN, R_WALL_OUT = 69, 71          # стена (толщина 3)
SPOKE = 3                               # полуширина улицы-спицы
WALL_H = 7

rng = random.Random(1421)
schem = mcschematic.MCSchematic()
occupied = set()                        # (x,z) — занято улицей/постройкой

PAVE = ["packed_mud"] * 5 + ["mud_bricks"] * 3 + ["granite"] * 2 + ["coarse_dirt"]
WALL_MIX = ["mud_bricks"] * 6 + ["packed_mud"] * 2 + ["granite"]


# --- примитивы -------------------------------------------------------------

def sb(x, y, z, block):
    if 0 <= x < SIZE and 0 <= z < SIZE:
        schem.setBlock((x, y, z), "minecraft:" + block)


def fill(x0, y0, z0, x1, y1, z1, block):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                sb(x, y, z, block)


def orad(x, z):
    """Октагональный радиус — восьмиугольник вместо круга.

    Кольцо собирается из прямых сегментов (ТЗ, раздел 1): здания стоят строго
    по осям, кривизна улицы — из стыков сегментов, а не из гнутой геометрии.
    """
    dx, dz = abs(x - CX), abs(z - CZ)
    return max(dx, dz, int((dx + dz) * 0.7071 + 0.5))


def take(x0, z0, w, d, margin=1):
    """Резервирует пятно под постройку. False — если пересеклись."""
    cells = [(x, z)
             for x in range(x0 - margin, x0 + w + margin)
             for z in range(z0 - margin, z0 + d + margin)]
    if any(c in occupied for c in cells):
        return False
    occupied.update(cells)
    return True


def pave(x, z):
    sb(x, GY, z, rng.choice(PAVE))
    occupied.add((x, z))


# --- земля, улицы ----------------------------------------------------------

def ground():
    for x in range(SIZE):
        for z in range(SIZE):
            if orad(x, z) <= R_WALL_OUT + 4:
                sb(x, 0, z, "dirt")
                sb(x, 1, z, "dirt")
                sb(x, GY, z, "grass_block")


def streets():
    for x in range(SIZE):
        for z in range(SIZE):
            r = orad(x, z)
            if r <= R_SQUARE or R_ROAD_IN <= r <= R_ROAD_OUT:
                pave(x, z)
    # спицы: восток, запад, юг (север закрывает Ратуша)
    for d in range(R_SQUARE, R_PLOT_OUT + 1):
        for o in range(-SPOKE, SPOKE + 1):
            pave(CX + d, CZ + o)
            pave(CX - d, CZ + o)
            pave(CX + o, CZ + d)
    # южная спица пробивает стену к воротам
    for d in range(R_PLOT_OUT, R_WALL_OUT + 5):
        for o in range(-SPOKE, SPOKE + 1):
            pave(CX + o, CZ + d)
    # парадный подход от площади к Ратуше
    for z in range(54, CZ - R_SQUARE + 1):
        for x in range(CX - 5, CX + 6):
            pave(x, z)


def lamp(x, z):
    sb(x, GY, z, "polished_granite")
    fill(x, GY + 1, z, x, GY + 3, z, "oak_fence")
    sb(x, GY + 4, z, "lantern")
    occupied.add((x, z))


def lamps():
    """Фонарные столбы по краям улиц."""
    for d in range(R_SQUARE + 6, R_PLOT_OUT, 9):
        for x, z in ((CX + d, CZ + SPOKE + 1), (CX + d, CZ - SPOKE - 1),
                     (CX - d, CZ + SPOKE + 1), (CX - d, CZ - SPOKE - 1),
                     (CX + SPOKE + 1, CZ + d), (CX - SPOKE - 1, CZ + d)):
            if (x, z) not in occupied:
                lamp(x, z)
    for deg in range(0, 360, 30):
        x = CX + int((R_ROAD_IN - 1) * math.cos(math.radians(deg)))
        z = CZ + int((R_ROAD_IN - 1) * math.sin(math.radians(deg)))
        if (x, z) not in occupied:
            lamp(x, z)


# --- дом -------------------------------------------------------------------

def hip_roof(x0, z0, w, d, y0, stairs, solid):
    """Вальмовая крыша ступенями. Каждый уровень закрывается solid'ом —
    так крыша не течёт и не нужны угловые shape=outer_*."""
    i = 0
    while w - 2 * i > 0 and d - 2 * i > 0:
        y, rx, rz = y0 + i, x0 + i, z0 + i
        rw, rd = w - 2 * i, d - 2 * i
        for x in range(rx, rx + rw):
            sb(x, y, rz, stairs + "[facing=south,half=bottom]")
            sb(x, y, rz + rd - 1, stairs + "[facing=north,half=bottom]")
        for z in range(rz, rz + rd):
            sb(rx, y, z, stairs + "[facing=east,half=bottom]")
            sb(rx + rw - 1, y, z, stairs + "[facing=west,half=bottom]")
        for cx_, cz_ in ((rx, rz), (rx + rw - 1, rz),
                         (rx, rz + rd - 1), (rx + rw - 1, rz + rd - 1)):
            sb(cx_, y, cz_, solid)
        for x in range(rx + 1, rx + rw - 1):
            for z in range(rz + 1, rz + rd - 1):
                sb(x, y, z, solid)
        i += 1


def shutters(x, y, z, axis, sty):
    t = sty["trapdoor"]
    if axis == "z":
        sb(x - 1, y, z, t + "[facing=north,half=top,open=true]")
        sb(x + 1, y, z, t + "[facing=north,half=top,open=true]")
    else:
        sb(x, y, z - 1, t + "[facing=east,half=top,open=true]")
        sb(x, y, z + 1, t + "[facing=east,half=top,open=true]")


def windows(x0, z0, w, d, h, sty, storeys):
    rows = [GY + 2] if storeys == 1 else [GY + 2, GY + h // 2 + 2]
    tall = h >= 6
    for y in rows:
        if y + 1 >= GY + h:
            continue
        for x in range(x0 + 2, x0 + w - 2):
            if (x - x0) % 4 == 2:
                for z in (z0, z0 + d - 1):
                    sb(x, y, z, "glass_pane")
                    if tall:
                        sb(x, y + 1, z, "glass_pane")
                    shutters(x, y, z, "z", sty)
        for z in range(z0 + 2, z0 + d - 2):
            if (z - z0) % 4 == 2:
                for x in (x0, x0 + w - 1):
                    sb(x, y, z, "glass_pane")
                    if tall:
                        sb(x, y + 1, z, "glass_pane")
                    shutters(x, y, z, "x", sty)


def doorway(x0, z0, w, d, sty, side):
    face = {"s": "north", "n": "south", "e": "west", "w": "east"}[side]
    if side in "ns":
        x, z = x0 + w // 2, (z0 + d - 1 if side == "s" else z0)
    else:
        x, z = (x0 + w - 1 if side == "e" else x0), z0 + d // 2
    sb(x, GY + 1, z, sty["door"] + "[facing=" + face + ",half=lower,hinge=left]")
    sb(x, GY + 2, z, sty["door"] + "[facing=" + face + ",half=upper,hinge=left]")
    sb(x, GY + 3, z, sty["beam"])
    if side in "ns":
        dx, dz = 0, (1 if side == "s" else -1)
    else:
        dx, dz = (1 if side == "e" else -1), 0
    sb(x + dx, GY, z + dz, "polished_granite")
    sb(x + dx, GY + 3, z + dz, sty["slab"] + "[type=top]")
    for s in (-1, 1):
        ox, oz = (s, 0) if side in "ns" else (0, s)
        sb(x + ox, GY + 3, z + oz, "lantern")


def house(x0, z0, w, d, h, sty, door="s", storeys=1):
    """Каркасный дом: цоколь, фахверк, окна со ставнями, вальмовая крыша."""
    take(x0, z0, w, d)
    fill(x0, GY, z0, x0 + w - 1, GY, z0 + d - 1, sty["base"])
    fill(x0 + 1, GY, z0 + 1, x0 + w - 2, GY, z0 + d - 2, sty["floor"])
    top = GY + h

    for y in range(GY + 1, top):
        for x in range(x0, x0 + w):
            for z in (z0, z0 + d - 1):
                sb(x, y, z, sty["wall"])
        for z in range(z0, z0 + d):
            for x in (x0, x0 + w - 1):
                sb(x, y, z, sty["wall"])
    # фахверк: столбы по стенам и на углах
    for x in range(x0, x0 + w, 4):
        for z in (z0, z0 + d - 1):
            fill(x, GY + 1, z, x, top - 1, z, sty["beam"])
    for z in range(z0, z0 + d, 4):
        for x in (x0, x0 + w - 1):
            fill(x, GY + 1, z, x, top - 1, z, sty["beam"])
    for x in (x0, x0 + w - 1):
        for z in (z0, z0 + d - 1):
            fill(x, GY + 1, z, x, top - 1, z, sty["beam"])
    # верхний пояс
    for x in range(x0, x0 + w):
        for z in (z0, z0 + d - 1):
            sb(x, top, z, sty["beam"])
    for z in range(z0, z0 + d):
        for x in (x0, x0 + w - 1):
            sb(x, top, z, sty["beam"])
    if storeys > 1:
        my = GY + h // 2
        for x in range(x0, x0 + w):
            for z in (z0, z0 + d - 1):
                sb(x, my, z, sty["beam"])
        for z in range(z0, z0 + d):
            for x in (x0, x0 + w - 1):
                sb(x, my, z, sty["beam"])
        fill(x0 + 1, my, z0 + 1, x0 + w - 2, my, z0 + d - 2, sty["floor"])

    windows(x0, z0, w, d, h, sty, storeys)
    doorway(x0, z0, w, d, sty, door)
    hip_roof(x0 - 1, z0 - 1, w + 2, d + 2, top + 1, sty["stairs"], sty["roof"])
    for dx, dz in ((2, 2), (w - 3, 2), (2, d - 3), (w - 3, d - 3)):
        sb(x0 + dx, top - 1, z0 + dz, "lantern[hanging=true]")


# --- стили -----------------------------------------------------------------

SPRUCE = dict(base="polished_granite", floor="spruce_planks", wall="spruce_planks",
              beam="stripped_spruce_log[axis=y]", stairs="spruce_stairs",
              roof="spruce_planks", door="spruce_door", trapdoor="spruce_trapdoor",
              slab="spruce_slab")
OAK = dict(base="polished_granite", floor="oak_planks", wall="oak_planks",
           beam="stripped_oak_log[axis=y]", stairs="dark_oak_stairs",
           roof="dark_oak_planks", door="oak_door", trapdoor="oak_trapdoor",
           slab="oak_slab")
CIVIC = dict(base="polished_granite", floor="polished_granite", wall="mud_bricks",
             beam="stripped_dark_oak_log[axis=y]", stairs="brick_stairs",
             roof="bricks", door="dark_oak_door", trapdoor="dark_oak_trapdoor",
             slab="brick_slab")
FORGE = dict(base="polished_granite", floor="granite", wall="granite",
             beam="stripped_dark_oak_log[axis=y]", stairs="dark_oak_stairs",
             roof="dark_oak_planks", door="dark_oak_door", trapdoor="dark_oak_trapdoor",
             slab="granite_slab")
CLERIC = dict(base="polished_granite", floor="birch_planks", wall="mud_bricks",
              beam="stripped_birch_log[axis=y]", stairs="spruce_stairs",
              roof="spruce_planks", door="birch_door", trapdoor="birch_trapdoor",
              slab="mud_brick_slab")


# --- площадь ---------------------------------------------------------------

def square():
    # колодец
    wx, wz = CX - 6, CZ
    for x in range(wx - 1, wx + 2):
        for z in range(wz - 1, wz + 2):
            sb(x, GY + 1, z, "polished_granite")
    sb(wx, GY, wz, "water")
    sb(wx, GY + 1, wz, "water")
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        fill(wx + dx, GY + 2, wz + dz, wx + dx, GY + 3, wz + dz, "stripped_oak_log[axis=y]")
    hip_roof(wx - 2, wz - 2, 5, 5, GY + 4, "dark_oak_stairs", "dark_oak_planks")

    # колокол под навесом — точка сбора и тревога при набеге (ТЗ раздел 2)
    bx, bz = CX + 6, CZ
    for x in range(bx - 2, bx + 3):
        for z in range(bz - 2, bz + 3):
            sb(x, GY, z, "polished_granite")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        fill(bx + dx, GY + 1, bz + dz, bx + dx, GY + 4, bz + dz, "stripped_oak_log[axis=y]")
    hip_roof(bx - 2, bz - 2, 5, 5, GY + 5, "dark_oak_stairs", "dark_oak_planks")
    sb(bx, GY + 4, bz, "bell[attachment=ceiling,facing=north]")

    # жаровни по углам площади
    for dx, dz in ((-9, -9), (9, -9), (-9, 9), (9, 9)):
        sb(CX + dx, GY + 1, CZ + dz, "polished_granite")
        sb(CX + dx, GY + 2, CZ + dz, "campfire[lit=true]")


# --- ключевые постройки ----------------------------------------------------

def town_hall():
    """Ратуша: доска призвания, библиотека лора, зал совета, архив Летописца."""
    x0, z0, w, d, h = 65, 40, 21, 15, 9
    house(x0, z0, w, d, h, CIVIC, door="s", storeys=2)
    for x in range(x0 + 7, x0 + 14):
        sb(x, GY, z0 + d, "polished_granite")
        sb(x, GY, z0 + d + 1, "polished_granite")
    # библиотека лора + стены под архив снимков Летописца
    for x in range(x0 + 2, x0 + 7):
        fill(x, GY + 1, z0 + 1, x, GY + 3, z0 + 1, "bookshelf")
    sb(x0 + 3, GY + 1, z0 + 3, "lectern[facing=south]")
    # доска призвания — постамент в центре
    fill(x0 + 9, GY + 1, z0 + 6, x0 + 11, GY + 1, z0 + 8, "polished_granite")
    sb(x0 + 10, GY + 2, z0 + 7, "lectern[facing=south]")
    # зал совета
    for x in range(x0 + 15, x0 + 19):
        sb(x, GY + 1, z0 + 3, "oak_stairs[facing=south,half=bottom]")
        sb(x, GY + 1, z0 + 6, "oak_stairs[facing=north,half=bottom]")
        sb(x, GY + 1, z0 + 4, "dark_oak_slab[type=top]")
        sb(x, GY + 1, z0 + 5, "dark_oak_slab[type=top]")
    sb(x0 + 1, GY + 1, z0 + d - 2, "chest[facing=south]")
    sb(x0 + 2, GY + 1, z0 + d - 2, "chest[facing=south]")


def tavern():
    """Таверна: общее место игроков, погреб под блюда FarmersDelight."""
    x0, z0, w, d, h = 96, 52, 17, 13, 8
    house(x0, z0, w, d, h, OAK, door="w", storeys=2)
    for x in range(x0 + 1, x0 + 4):
        sb(x, GY + 1, z0 + 1, "furnace[facing=south]")
    sb(x0 + 4, GY + 1, z0 + 1, "smoker[facing=south]")
    sb(x0 + 1, GY + 1, z0 + 3, "cauldron")
    sb(x0 + 2, GY + 1, z0 + 3, "campfire[lit=true]")
    for tx, tz in ((x0 + 7, z0 + 3), (x0 + 7, z0 + 8), (x0 + 12, z0 + 3), (x0 + 12, z0 + 8)):
        sb(tx, GY + 1, tz, "oak_fence")
        sb(tx, GY + 2, tz, "oak_pressure_plate")
        sb(tx - 1, GY + 1, tz, "oak_stairs[facing=west,half=bottom]")
        sb(tx + 1, GY + 1, tz, "oak_stairs[facing=east,half=bottom]")
        sb(tx, GY + 1, tz - 1, "oak_stairs[facing=north,half=bottom]")
        sb(tx, GY + 1, tz + 1, "oak_stairs[facing=south,half=bottom]")
    for z in range(z0 + 2, z0 + 6):
        sb(x0 + w - 3, GY + 1, z, "dark_oak_slab[type=top]")
        sb(x0 + w - 2, GY + 1, z, "barrel[facing=up]")
    # погреб
    fill(x0 + 1, GY - 4, z0 + 1, x0 + 7, GY - 4, z0 + 7, "polished_granite")
    fill(x0 + 2, GY - 3, z0 + 2, x0 + 6, GY - 1, z0 + 6, "air")
    for z in range(z0 + 2, z0 + 7):
        sb(x0 + 2, GY - 3, z, "barrel[facing=up]")
    for i in range(4):
        sb(x0 + 6, GY - i, z0 + 6 - i, "oak_stairs[facing=north,half=bottom]")


def smithy():
    """Мастерская Кузнеца: сборка деталей Очистителя (шаг 2 цепочки реагента)."""
    x0, z0, w, d, h = 96, 88, 15, 13, 6
    house(x0, z0, w, d, h, FORGE, door="w")
    sb(x0 + 2, GY + 1, z0 + 2, "anvil[facing=north]")
    sb(x0 + 4, GY + 1, z0 + 2, "smithing_table")
    sb(x0 + 6, GY + 1, z0 + 2, "crafting_table")
    sb(x0 + 2, GY + 1, z0 + 4, "blast_furnace[facing=south]")
    sb(x0 + 3, GY + 1, z0 + 4, "blast_furnace[facing=south]")
    sb(x0 + 8, GY + 1, z0 + 3, "chest[facing=south]")
    sb(x0 + 9, GY + 1, z0 + 3, "chest[facing=south]")
    # горн с трубой наружу
    fill(x0 + w - 4, GY + 1, z0 + d - 4, x0 + w - 3, GY + 5, z0 + d - 3, "bricks")
    sb(x0 + w - 4, GY + 1, z0 + d - 4, "campfire[lit=true]")
    fill(x0 + w - 4, GY + h + 1, z0 + d - 4, x0 + w - 3, GY + h + 4, z0 + d - 3, "bricks")
    for x in range(x0 - 4, x0):
        for z in range(z0 + 2, z0 + 8):
            sb(x, GY, z, "packed_mud")
            occupied.add((x, z))


def cleric_house():
    """Дом Клирика: алхимия и варка (шаг 1 цепочки реагента)."""
    x0, z0, w, d, h = 40, 52, 13, 11, 6
    house(x0, z0, w, d, h, CLERIC, door="e")
    sb(x0 + 2, GY + 1, z0 + 2, "brewing_stand")
    sb(x0 + 3, GY + 1, z0 + 2, "brewing_stand")
    sb(x0 + 2, GY + 1, z0 + 4, "cauldron")
    sb(x0 + 4, GY + 1, z0 + 4, "crafting_table")
    for z in range(z0 + 6, z0 + 9):
        sb(x0 + 1, GY + 1, z, "bookshelf")
        sb(x0 + 1, GY + 2, z, "bookshelf")
    sb(x0 + 3, GY + 1, z0 + 7, "lectern[facing=east]")
    sb(x0 + w - 3, GY + 1, z0 + 2, "chest[facing=south]")
    # травяной садик у входа
    for x in range(x0 + w + 1, x0 + w + 5):
        for z in range(z0 + 2, z0 + 9):
            if (x + z) % 3:
                sb(x, GY, z, "farmland[moisture=7]")
                sb(x, GY + 1, z, rng.choice(["wheat[age=7]", "beetroots[age=3]", "carrots[age=7]"]))
            occupied.add((x, z))


def farm():
    """Огороженная ферма: безопасная грядка plague_bloom внутри стен (ТЗ)."""
    x0, z0, w, d = 36, 86, 25, 19
    take(x0, z0, w, d)
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            if x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1):
                sb(x, GY + 1, z, "oak_fence")
            else:
                sb(x, GY, z, "farmland[moisture=7]")
                sb(x, GY + 1, z, rng.choice(["wheat[age=7]"] * 3 + ["potatoes[age=7]", "carrots[age=7]"]))
    for cx_, cz_ in ((x0, z0), (x0 + w - 1, z0), (x0, z0 + d - 1), (x0 + w - 1, z0 + d - 1)):
        fill(cx_, GY + 1, cz_, cx_, GY + 2, cz_, "stripped_oak_log[axis=y]")
    sb(x0 + w // 2, GY + 1, z0, "oak_fence_gate[facing=north]")
    # водяные каналы
    for z in range(z0 + 3, z0 + d - 1, 5):
        for x in range(x0 + 2, x0 + w - 2):
            sb(x, GY, z, "water")
            sb(x, GY + 1, z, "air")
    # навес с сеном
    for x in range(x0 + 2, x0 + 6):
        for z in range(z0 + 1, z0 + 3):
            sb(x, GY, z, "spruce_planks")
            sb(x, GY + 1, z, "air")
    fill(x0 + 2, GY + 1, z0 + 1, x0 + 3, GY + 2, z0 + 2, "hay_block[axis=y]")
    sb(x0 + 5, GY + 1, z0 + 1, "composter")


def cemetery():
    """Кладбище/мемориал: вес механике «-0.5 сердца навсегда» (ТЗ)."""
    x0, z0, w, d = 11, 84, 15, 17
    take(x0, z0, w, d)
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            if x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1):
                sb(x, GY + 1, z, "mud_brick_wall")
    sb(x0 + w - 1, GY + 1, z0 + d // 2, "air")   # калитка к западной спице
    for x in range(x0 + 2, x0 + w - 2, 3):
        for z in range(z0 + 5, z0 + d - 2, 3):
            sb(x, GY + 1, z, "granite_wall")
            sb(x, GY + 2, z, "polished_granite_slab[type=bottom]")
            if rng.random() < 0.4:
                sb(x + 1, GY + 1, z, "dead_bush")
    mx, mz = x0 + w // 2, z0 + 2
    fill(mx - 1, GY + 1, mz - 1, mx + 1, GY + 1, mz + 1, "polished_granite")
    fill(mx, GY + 2, mz, mx, GY + 4, mz, "granite_wall")
    sb(mx, GY + 5, mz, "lantern")
    sb(mx - 1, GY + 2, mz, "lantern")
    sb(mx + 1, GY + 2, mz, "lantern")


def cottages(limit=20):
    """Готовая застройка внутреннего пояса — чтобы город с первого дня читался
    обжитым (ТЗ, раздел 1), а не как площадь с шестью зданиями на лужайке."""
    made = 0
    cand = []
    for x0 in range(2, SIZE - 12):
        for z0 in range(2, SIZE - 12):
            w, d = (11, 9) if (x0 + z0) % 2 else (9, 7)
            corners = [(x0, z0), (x0 + w - 1, z0), (x0, z0 + d - 1), (x0 + w - 1, z0 + d - 1)]
            if not all(18 <= orad(x, z) <= 40 for x, z in corners):
                continue
            cand.append((math.atan2(z0 + d // 2 - CZ, x0 + w // 2 - CX), x0, z0, w, d))
    cand.sort()
    for _, x0, z0, w, d in cand:
        if made >= limit:
            break
        if any((x, z) in occupied
               for x in range(x0 - 3, x0 + w + 3) for z in range(z0 - 3, z0 + d + 3)):
            continue
        dx, dz = CX - (x0 + w // 2), CZ - (z0 + d // 2)
        door = ("e" if dx > 0 else "w") if abs(dx) >= abs(dz) else ("s" if dz > 0 else "n")
        house(x0, z0, w, d, 5 + made % 2, SPRUCE if made % 2 else OAK, door=door)
        # дорожка от двери к ближайшей улице
        step = {"n": (0, -1), "s": (0, 1), "w": (-1, 0), "e": (1, 0)}[door]
        px = x0 + w // 2 if door in "ns" else (x0 + w if door == "e" else x0 - 1)
        pz = z0 + d // 2 if door in "we" else (z0 + d if door == "s" else z0 - 1)
        for i in range(6):
            sb(px + step[0] * i, GY, pz + step[1] * i, "packed_mud")
            occupied.add((px + step[0] * i, pz + step[1] * i))
        made += 1
    return made


# --- жилые участки ---------------------------------------------------------

def plot(x0, z0, side):
    gate = {"n": (x0 + 6, z0), "s": (x0 + 6, z0 + 11),
            "w": (x0, z0 + 6), "e": (x0 + 11, z0 + 6)}[side]
    for x in range(x0, x0 + 12):
        for z in range(z0, z0 + 12):
            if x in (x0, x0 + 11) or z in (z0, z0 + 11):
                sb(x, GY + 1, z, "oak_fence")
    for cx_, cz_ in ((x0, z0), (x0 + 11, z0), (x0, z0 + 11), (x0 + 11, z0 + 11)):
        fill(cx_, GY + 1, cz_, cx_, GY + 2, cz_, "stripped_spruce_log[axis=y]")
        sb(cx_, GY + 3, cz_, "lantern")
    sb(gate[0], GY + 1, gate[1], "air")
    sb(gate[0], GY, gate[1], "packed_mud")
    step = {"n": (0, -1), "s": (0, 1), "w": (-1, 0), "e": (1, 0)}[side]
    for i in range(1, 4):
        sb(gate[0] + step[0] * i, GY, gate[1] + step[1] * i, "packed_mud")
    # разметка пятна под дом — участок читается как готовый под застройку
    for x in range(x0 + 2, x0 + 10):
        for z in range(z0 + 2, z0 + 10):
            if x in (x0 + 2, x0 + 9) or z in (z0 + 2, z0 + 9):
                sb(x, GY, z, "coarse_dirt")


def plots():
    """Участки 12x12: размеченные и огороженные, застройка — за игроками (ТЗ).

    Кандидаты обходятся по кругу (сортировка по углу), а не сеткой: пояс —
    восьмиугольный, жёсткая сетка в него почти не попадает.
    """
    cand = []
    for x0 in range(1, SIZE - 12):
        for z0 in range(1, SIZE - 12):
            corners = [(x0, z0), (x0 + 11, z0), (x0, z0 + 11), (x0 + 11, z0 + 11)]
            # внутрь стены целиком, наружу от кольцевой; пересечения с улицами
            # и стеной ловит take() — они уже в occupied
            if max(orad(x, z) for x, z in corners) > R_PLOT_OUT:
                continue
            if orad(x0 + 6, z0 + 6) < R_ROAD_OUT + 5:
                continue
            cand.append((math.atan2(z0 + 6 - CZ, x0 + 6 - CX), x0, z0))
    cand.sort()
    made = 0
    for _, x0, z0 in cand:
        if not take(x0, z0, 12, 12, margin=2):
            continue
        dx, dz = CX - (x0 + 6), CZ - (z0 + 6)
        side = ("e" if dx > 0 else "w") if abs(dx) >= abs(dz) else ("s" if dz > 0 else "n")
        plot(x0, z0, side)
        made += 1
    return made


# --- стена, ворота, башни --------------------------------------------------

def tower(x, z):
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for y in range(GY + 1, GY + 12):
                sb(x + dx, y, z + dz, rng.choice(WALL_MIX))
            occupied.add((x + dx, z + dz))
    fill(x - 1, GY + 1, z - 1, x + 1, GY + 10, z + 1, "air")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            edge = abs(dx) == 2 or abs(dz) == 2
            sb(x + dx, GY + 11, z + dz, "mud_bricks" if edge else "mud_brick_slab[type=bottom]")
            if edge and (dx + dz) % 2 == 0:
                sb(x + dx, GY + 12, z + dz, "mud_bricks")
    for dx, dz in ((0, -2), (0, 2), (-2, 0), (2, 0)):
        sb(x + dx, GY + 7, z + dz, "air")
        sb(x + dx, GY + 8, z + dz, "air")
    sb(x, GY + 12, z, "lantern")


def gatehouse(gate_x):
    z_mid = CZ + (R_WALL_IN + R_WALL_OUT) // 2
    lo, hi = min(gate_x), max(gate_x)
    for x in gate_x:
        for z in range(CZ + R_WALL_IN - 1, CZ + R_WALL_OUT + 2):
            sb(x, GY, z, "mud_bricks")
            occupied.add((x, z))
    for z in range(CZ + R_WALL_IN, CZ + R_WALL_OUT + 1):
        for x in (lo - 1, hi + 1):
            fill(x, GY + 1, z, x, GY + 8, z, "mud_bricks")
        for x in gate_x:
            sb(x, GY + 6, z, "mud_bricks")
            sb(x, GY + 7, z, "mud_bricks")
        sb(lo, GY + 5, z, "mud_brick_stairs[facing=east,half=top]")
        sb(hi, GY + 5, z, "mud_brick_stairs[facing=west,half=top]")
    for x in (lo - 1, hi + 1):
        sb(x, GY + 4, z_mid, "lantern")
    for x in range(lo - 1, hi + 2):
        for z in range(CZ + R_WALL_IN, CZ + R_WALL_OUT + 1):
            sb(x, GY + 8, z, "mud_bricks")
            if (x + z) % 2 == 0 and z in (CZ + R_WALL_IN, CZ + R_WALL_OUT):
                sb(x, GY + 9, z, "mud_bricks")
    tower(lo - 4, z_mid)
    tower(hi + 4, z_mid)


def walls():
    gate_x = range(CX - SPOKE - 1, CX + SPOKE + 2)
    for x in range(SIZE):
        for z in range(SIZE):
            r = orad(x, z)
            if not (R_WALL_IN <= r <= R_WALL_OUT):
                continue
            if x in gate_x and z > CZ:
                continue                              # проём ворот
            for y in range(GY + 1, GY + WALL_H):
                sb(x, y, z, rng.choice(WALL_MIX))
            if r == R_WALL_OUT:
                sb(x, GY + WALL_H, z, "mud_bricks")
                if (x + z) % 2 == 0:
                    sb(x, GY + WALL_H + 1, z, "mud_bricks")
            else:
                sb(x, GY + WALL_H, z, "mud_brick_slab[type=bottom]")
            occupied.add((x, z))
    gatehouse(gate_x)
    for tx, tz in ((CX, CZ - R_WALL_IN - 1), (CX + R_WALL_IN + 1, CZ), (CX - R_WALL_IN - 1, CZ),
                   (CX + 50, CZ + 50), (CX - 50, CZ + 50),
                   (CX + 50, CZ - 50), (CX - 50, CZ - 50)):
        tower(tx, tz)


# --- зелень ----------------------------------------------------------------

def tree(x, z):
    h = rng.randint(4, 6)
    fill(x, GY + 1, z, x, GY + h, z, "oak_log[axis=y]")
    for dy in range(h - 2, h + 2):
        r = 2 if dy < h else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + 1 and not (dx == 0 and dz == 0 and dy <= h):
                    sb(x + dx, GY + dy, z + dz, "oak_leaves[persistent=true]")
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            occupied.add((x + dx, z + dz))


def greenery():
    planted = 0
    for _ in range(4000):
        if planted >= 55:
            break
        x, z = rng.randrange(6, SIZE - 6), rng.randrange(6, SIZE - 6)
        if orad(x, z) >= R_WALL_IN - 2:
            continue
        if any((x + dx, z + dz) in occupied for dx in range(-2, 3) for dz in range(-2, 3)):
            continue
        tree(x, z)
        planted += 1
    for _ in range(600):
        x, z = rng.randrange(6, SIZE - 6), rng.randrange(6, SIZE - 6)
        if orad(x, z) < R_WALL_IN and (x, z) not in occupied:
            sb(x, GY + 1, z, rng.choice(
                ["short_grass", "short_grass", "poppy", "dandelion", "azure_bluet"]))
    return planted


# --- сборка ----------------------------------------------------------------

def build():
    ground()
    streets()
    square()
    town_hall()
    tavern()
    smithy()
    cleric_house()
    farm()
    cemetery()
    n_cot = cottages()
    n_plots = plots()
    walls()
    lamps()
    n_trees = greenery()
    return n_plots, n_cot, n_trees


def check():
    """Один прогон-проверка: город на месте, ворота открыты, стена цела."""
    assert schem.getBlockDataAt((CX, GY, CZ)) != "minecraft:air", "площадь пустая"
    assert schem.getBlockDataAt((CX, GY + 3, CZ + R_WALL_IN + 1)) == "minecraft:air", "ворота замурованы"
    # точка на северной стене между башнями (в самих башнях внутри — воздух)
    assert schem.getBlockDataAt((CX + 20, GY + 3, CZ - 70)) != "minecraft:air", "стена дырявая"
    assert schem.getBlockDataAt((65, GY + 1, 42)) != "minecraft:air", "у Ратуши нет стены"
    assert schem.getBlockDataAt((75, GY + 5, 47)) == "minecraft:air", "Ратуша залита монолитом"


if __name__ == "__main__":
    import os
    p, cot, t = build()
    check()
    out = os.path.dirname(os.path.abspath(__file__))
    schem.save(out, "plague_city", mcschematic.Version.JE_1_21_1)
    print("ok: %d plots, %d cottages, %d trees -> %s/plague_city.schem" % (p, cot, t, out))
