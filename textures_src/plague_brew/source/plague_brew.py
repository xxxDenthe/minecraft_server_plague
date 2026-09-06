"""Plague Brew icon 16x16.

Silhouette is copied pixel-for-pixel from vanilla minecraft:item/glass_bottle
(see glass_bottle_vanilla.png). Only the colours change: dark crude glass,
cloudy pale murky liquid, cloth rag + twine instead of the cork. No glow.
"""
import os
from pngio import read_png, write_png

PAL = {
    ".": (0, 0, 0, 0),
    "D": (26, 33, 26, 255),      # darkest glass edge
    "g": (45, 57, 42, 255),      # dark glass
    "h": (78, 94, 70, 255),      # dull glass highlight (no shine)
    "S": (37, 47, 35, 255),      # hollow neck, seen through glass
    "l": (176, 173, 152, 255),   # pale murky liquid
    "L": (196, 193, 171, 255),   # liquid surface
    "m": (150, 148, 127, 255),   # cloudy clumps
    "n": (116, 114, 95, 255),    # sediment
    "c": (176, 161, 133, 255),   # cloth rag
    "C": (201, 187, 158, 255),   # cloth highlight
    "s": (128, 115, 88, 255),    # cloth shadow
    "t": (104, 80, 50, 255),     # twine
    "T": (70, 53, 32, 255),      # twine dark
}

ROWS = [
    "................",
    "................",
    ".......cCc......",
    "......scCcs.....",
    "......tTtTt.....",
    ".......gSg......",
    ".......gSg......",
    "......hSSSg.....",
    ".....DhLLllD....",
    "....DghlmllmD...",
    "....DghmllmlD...",
    "....DgllmlhgD...",
    "....DglmlmhgD...",
    ".....DgnnhgD....",
    "......DDDDD.....",
    "................",
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "assets", "textures", "item", "plague_brew.png")
VANILLA = os.path.join(HERE, "glass_bottle_vanilla.png")


def build():
    return [[list(PAL[ch]) for ch in row] for row in ROWS]


def check():
    """Silhouette must stay vanilla: every glass pixel vanilla draws is still
    drawn here, and nothing spills past vanilla's outline. We only ADD pixels
    inside the bottle, because ours is full and vanilla's is empty."""
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
    _, _, van = read_png(VANILLA)
    for y, row in enumerate(ROWS):
        solid = [x for x in range(16) if van[y][x][3] != 0]
        for x in solid:
            assert row[x] != ".", "hole where vanilla has glass at %d,%d" % (x, y)
        lo, hi = (min(solid), max(solid)) if solid else (16, -1)
        for x, ch in enumerate(row):
            if ch != ".":
                assert lo <= x <= hi, "pixel outside vanilla outline at %d,%d" % (x, y)


if __name__ == "__main__":
    check()
    write_png(OUT, 16, 16, build())
    print("wrote", os.path.normpath(OUT))
