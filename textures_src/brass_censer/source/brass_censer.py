"""Brass Censer — кадило.

Иконка предмета 16x16. Кадило висит на коротке цепи: продырявленный купол,
потемневшая латунь и тёмное железо, копоть на дне, тонкая струйка бледного
дыма уходит вверх и вбок. Ничего не светится.
"""
import os
from pngio import write_png

PAL = {
    ".": (0, 0, 0, 0),
    "B": (163, 140, 73, 255),   # tarnished brass, lit
    "b": (124, 105, 56, 255),   # brass, mid
    "r": (86, 72, 40, 255),     # brass, shadow
    "i": (70, 68, 63, 255),     # iron
    "I": (43, 41, 38, 255),     # dark iron
    "o": (31, 26, 21, 255),     # pierced hole
    "s": (36, 30, 24, 255),     # soot
    "S": (201, 196, 182, 255),  # pale smoke
    "w": (169, 164, 152, 255),  # smoke, fading out
}

ROWS = [
    ".......iI...w...",
    ".......Ii..S....",
    ".......iI..S....",
    ".......Ii.S.....",
    "......bBBb......",
    ".....bBoBBb.....",
    "....bBoBBoBb....",
    "...bsoBBBBosb...",
    "...IiIIIIIIiI...",
    "...bBBBBBBBBb...",
    "...bBBBBBBBBb...",
    "....rbBsBBbr....",
    ".....srssrs.....",
    "......iIIi......",
    "................",
    "................",
]

SMOKE = set("Sw")
CHAIN_X = (7, 8)
CHAIN_ROWS = range(0, 5)   # ring at the top down to the lid

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "assets", "textures", "item", "brass_censer.png")


def build():
    return [[list(PAL[ch]) for ch in row] for row in ROWS]


def check():
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
    assert all(ch in PAL for r in ROWS for ch in r)

    # The chain must not break, or the censer floats loose from its ring.
    for y in CHAIN_ROWS:
        assert any(ROWS[y][x] != "." for x in CHAIN_X), "chain breaks at row %d" % y

    # Every piercing must sit fully inside the dome. A hole touching the
    # outline reads as a bite chewed out of the lid, not as a hole.
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch != "o":
                continue
            for nx, ny in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
                assert 0 <= nx < 16 and 0 <= ny < 16 and ROWS[ny][nx] != ".", \
                    "piercing at %d,%d breaks the dome outline" % (x, y)

    # The wisp must float clear of the metal, otherwise it reads as a pale
    # smudge painted on the lid instead of smoke coming off it.
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch not in SMOKE:
                continue
            for nx, ny in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
                if 0 <= nx < 16 and 0 <= ny < 16:
                    n = ROWS[ny][nx]
                    assert n == "." or n in SMOKE, \
                        "smoke at %d,%d touches the metal" % (x, y)


if __name__ == "__main__":
    check()
    write_png(OUT, 16, 16, build())
    print("wrote", os.path.normpath(OUT))
