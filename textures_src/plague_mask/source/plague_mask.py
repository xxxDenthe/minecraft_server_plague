"""Plague Mask — peasant cloth face mask.

Two files:
  * plague_mask.png  64x64  worn layer, drawn by PlagueMaskLayer on the head
  * plague_mask_item.png  16x16  inventory icon

Folded strip of dirty linen, greyish beige, stained, frayed edges, two tie
cords going round the head. Deliberately NOT a plague doctor beak: nothing is
drawn on the top of the head and nothing sticks out from the face.
"""
import os
from pngio import write_png

PAL = {
    ".": (0, 0, 0, 0),
    "b": (176, 168, 148, 255),   # linen, greyish beige
    "o": (196, 188, 168, 255),   # lit edge
    "d": (141, 134, 116, 255),   # fold crease / shadow
    "n": (122, 114, 96, 255),    # stain
    "f": (156, 148, 130, 255),   # frayed, worn thin
    "c": (162, 151, 124, 255),   # tie cord
    "C": (111, 101, 82, 255),    # cord knot / dark
}

# ------------------------------------------------------------- worn layer
# 64x64, standard skin sheet. The head is the top-left 32x16 block:
#
#         x: 0---7   8--15   16-23   24-31
#   y: 0-7    unused  top     bottom  unused
#   y: 8-15   right   FACE    left    back
#
# So one horizontal run across x0..31 wraps the whole head. Everything
# outside that 32x16 block stays transparent.
#
# Vanilla eyes sit on row y12, mouth on y13. The cloth therefore starts at
# y13 (nose and mouth covered, eyes clear) and the upper tie runs at y11
# over the ears and round the back, never across the face.
HEAD_ROWS = {
    11: "cccccccc" + "........" + "cccccccccccccccc",
    13: "......" + "dd" + "dddddddd" + "dd" + ".........CC...",
    14: "cccccc" + "bb" + "bbnbbbbb" + "bb" + "cccccccccccccc",
    15: "......" + ".f" + "fbbfbnbf" + "f." + "..............",
}

EYE_ROW = 12

# ------------------------------------------------------------- item icon
ICON = [
    "................",
    "................",
    "C..............C",
    "c..............c",
    ".c............c.",
    ".c............c.",
    "..c..........c..",
    "..obbbbbbbbbbo..",
    "..bbbnbbbbbbbb..",
    "..dddddddddddd..",
    "..bbbbbbbnbbbb..",
    "..bbbbnbbbbbbb..",
    "..fbbfbbbbfbbf..",
    "...f...f....f...",
    "................",
    "................",
]

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets", "textures")
LAYER_OUT = os.path.join(ASSETS, "entity", "plague_mask.png")
ICON_OUT = os.path.join(ASSETS, "item", "plague_mask_item.png")

# The mod copy. PlagueMaskLayer loads exactly this path.
MOD_OUT = os.path.join(HERE, "..", "..", "..", "plaguecore", "src", "main",
                       "resources", "assets", "plaguecore", "textures",
                       "entity", "plague_mask.png")


def build_layer():
    img = [[list(PAL["."]) for _ in range(64)] for _ in range(64)]
    for y, row in HEAD_ROWS.items():
        for x, ch in enumerate(row):
            img[y][x] = list(PAL[ch])
    return img


def build_icon():
    return [[list(PAL[ch]) for ch in row] for row in ICON]


def check():
    assert all(len(r) == 32 for r in HEAD_ROWS.values()), "head row is not 32 wide"
    assert all(ch in PAL for r in HEAD_ROWS.values() for ch in r)
    assert len(ICON) == 16 and all(len(r) == 16 for r in ICON)
    assert all(ch in PAL for r in ICON for ch in r)

    img = build_layer()
    assert len(img) == 64 and all(len(r) == 64 for r in img), "layer is not 64x64"

    # Nothing may leave the head block. Everything else on the sheet is bare.
    for y in range(64):
        for x in range(64):
            if img[y][x][3] and not (x < 32 and 8 <= y < 16):
                raise AssertionError("pixel outside the head faces at %d,%d" % (x, y))

    # A face band, not a hood and not a beak: top and bottom of the head bare.
    assert all(8 <= y < 16 for y in HEAD_ROWS), "cloth on the top of the head"

    # The eye row stays completely bare, so the player can still see out.
    assert EYE_ROW not in HEAD_ROWS, "mask covers the eyes"

    # Upper tie: unbroken over both ears and round the back, absent across
    # the face, so it does not draw a line over the eyes.
    up = HEAD_ROWS[11]
    assert "." not in up[0:8] + up[16:32], "upper tie breaks"
    assert up[8:16] == "." * 8, "upper tie crosses the face"

    # Lower tie plus the cloth: an unbroken ring, so the mask does not
    # vanish when the player is seen from behind.
    assert "." not in HEAD_ROWS[14], "lower tie breaks, mask has a hole at the back"


if __name__ == "__main__":
    check()
    layer = build_layer()
    write_png(LAYER_OUT, 64, 64, layer)
    write_png(MOD_OUT, 64, 64, layer)
    write_png(ICON_OUT, 16, 16, build_icon())
    for p in (LAYER_OUT, MOD_OUT, ICON_OUT):
        print("wrote", os.path.normpath(p))
