"""Class Codex — Гримуар призвания.

Иконка предмета 16x16. Закрытый том, стоящий корешком влево: кожаная
обложка, справа торец страниц, поперёк — ремень с латунной пряжкой.
Цвета взяты не с потолка, а из его же экрана
(`textures/gui/codex_background.png`): пергамент, коричневая кожа
и тёплый загар оттуда, чтобы предмет и открытая книга читались как
одна вещь. Фиолетового нет: гримуар — вещь класса, а не зараза.
"""
import os
from pngio import write_png

PAL = {
    ".": (0, 0, 0, 0),
    "o": (24, 14, 12, 255),     # outline, warm near-black
    "d": (58, 38, 27, 255),     # leather, deepest: spine and bottom shadow
    "s": (70, 44, 19, 255),     # strap
    "l": (112, 70, 45, 255),    # leather, mid
    "L": (127, 88, 57, 255),    # leather, lit
    "q": (214, 197, 167, 255),  # page edge, shadow
    "p": (239, 228, 198, 255),  # page edge, mid
    "P": (253, 249, 237, 255),  # page edge, bright
    "b": (146, 124, 66, 255),   # brass buckle, shadow
    "B": (198, 172, 98, 255),   # brass buckle, lit
}

ROWS = [
    "................",
    "................",
    "..ooooooooooo...",
    ".odLLLLLLLLqPo..",
    ".odLlllllllpPo..",
    ".odLlllllllqpo..",
    ".odLlllllllpPo..",
    ".odssssssssBBo..",
    ".odssssssssbbo..",
    ".odLlllllllqpo..",
    ".odLlllllllpPo..",
    ".odLlllllllqPo..",
    ".odddddddddqpo..",
    "..ooooooooooo...",
    "................",
    "................",
]

SPINE_X = 2
COVER_X = range(3, 11)   # кожа обложки
PAGE_X = (11, 12)        # торец страниц
STRAP_ROWS = (7, 8)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "assets", "textures", "item", "class_codex.png")


def build():
    return [[list(PAL[ch]) for ch in row] for row in ROWS]


def check():
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
    assert all(ch in PAL for r in ROWS for ch in r)

    # Контур замкнут: любой пиксель, граничащий с пустотой, обязан быть
    # обводкой. Иначе кожа или страницы вытекают наружу без края, и на
    # тёмном фоне инвентаря том теряет силуэт.
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                пусто = not (0 <= nx < 16 and 0 <= ny < 16) or ROWS[ny][nx] == "."
                assert not пусто or ch == "o", \
                    "край без обводки в %d,%d (%s)" % (x, y, ch)

    # Ремень пересекает обложку целиком. Ремень с дыркой посередине
    # читается как царапина на коже, а не как то, чем том стянут.
    for y in STRAP_ROWS:
        for x in COVER_X:
            assert ROWS[y][x] == "s", "ремень рвётся в %d,%d" % (x, y)
        assert ROWS[y][PAGE_X[1]] in "bB", "пряжка не легла на торец, строка %d" % y

    # Страницы не заезжают на корешок: торец бывает только справа.
    # Светлый пиксель у корешка мгновенно превращает том в раскрытую книгу.
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch in "qpP":
                assert x in PAGE_X, "страница вне торца в %d,%d" % (x, y)

    # Корешок сплошной по всей высоте тела — на нём держится «закрытость».
    тело = [y for y, row in enumerate(ROWS) if row[SPINE_X] != "."]
    assert тело == list(range(2, 14)), "корешок рваный: %r" % (тело,)


if __name__ == "__main__":
    check()
    write_png(OUT, 16, 16, build())
    print("wrote", os.path.normpath(OUT))
