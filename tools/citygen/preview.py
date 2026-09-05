"""Вид сверху для city_gen.py — проверить планировку, не заходя в игру.

python preview.py  ->  preview.png (6 px на блок)
"""
import os

from PIL import Image

import city_gen as c

SCALE = 6
top = {}


def _rec(x, y, z, block):
    if 0 <= x < c.SIZE and 0 <= z < c.SIZE:
        name = block.split("[")[0]
        if name == "air":
            top.pop((x, z), None)
        elif (x, z) not in top or y >= top[(x, z)][0]:
            top[(x, z)] = (y, name)


_orig = c.sb


def sb(x, y, z, block):
    _orig(x, y, z, block)
    _rec(x, y, z, block)


c.sb = sb
for mod in (c,):
    mod.sb = sb

COLORS = {
    "grass_block": (108, 145, 74), "short_grass": (110, 150, 78),
    "poppy": (170, 70, 60), "dandelion": (190, 180, 70), "azure_bluet": (200, 200, 210),
    "dirt": (134, 96, 67), "coarse_dirt": (120, 88, 62),
    "packed_mud": (150, 112, 79), "mud_bricks": (150, 118, 88),
    "granite": (154, 106, 88), "polished_granite": (160, 115, 96),
    "granite_wall": (154, 106, 88), "polished_granite_slab": (160, 115, 96),
    "mud_brick_slab": (150, 118, 88), "mud_brick_wall": (150, 118, 88),
    "mud_brick_stairs": (150, 118, 88), "granite_slab": (154, 106, 88),
    "bricks": (150, 78, 62), "brick_stairs": (150, 78, 62), "brick_slab": (150, 78, 62),
    "spruce_planks": (114, 84, 48), "spruce_stairs": (114, 84, 48),
    "spruce_slab": (114, 84, 48), "spruce_fence": (114, 84, 48),
    "oak_planks": (162, 130, 78), "oak_stairs": (162, 130, 78),
    "oak_fence": (140, 112, 68), "oak_slab": (162, 130, 78),
    "oak_fence_gate": (140, 112, 68), "oak_pressure_plate": (162, 130, 78),
    "dark_oak_planks": (66, 43, 20), "dark_oak_stairs": (66, 43, 20),
    "dark_oak_slab": (66, 43, 20),
    "birch_planks": (192, 175, 121),
    "stripped_oak_log": (177, 144, 86), "stripped_spruce_log": (116, 91, 55),
    "stripped_dark_oak_log": (85, 60, 30), "stripped_birch_log": (196, 179, 123),
    "oak_log": (109, 85, 51), "oak_leaves": (60, 100, 45),
    "glass_pane": (200, 230, 235), "lantern": (255, 210, 120),
    "campfire": (255, 160, 60), "water": (60, 110, 200),
    "farmland": (95, 65, 40), "wheat": (190, 175, 95), "potatoes": (110, 150, 60),
    "carrots": (200, 120, 50), "beetroots": (150, 60, 60), "hay_block": (200, 175, 60),
    "bookshelf": (150, 110, 70), "dead_bush": (120, 100, 70),
    "bell": (220, 180, 70), "chest": (150, 110, 60), "barrel": (130, 100, 60),
}
DEFAULT = (128, 128, 128)


def main():
    c.build()
    img = Image.new("RGB", (c.SIZE * SCALE, c.SIZE * SCALE), (20, 20, 24))
    px = img.load()
    for (x, z), (y, name) in top.items():
        r, g, b = COLORS.get(name, DEFAULT)
        # затенение по высоте: выше — светлее
        k = 0.72 + min(max((y - c.GY) / 14.0, 0.0), 1.0) * 0.55
        col = (min(int(r * k), 255), min(int(g * k), 255), min(int(b * k), 255))
        for dx in range(SCALE):
            for dz in range(SCALE):
                px[x * SCALE + dx, z * SCALE + dz] = col
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview.png")
    img.save(out)
    print("ok ->", out)


if __name__ == "__main__":
    main()
