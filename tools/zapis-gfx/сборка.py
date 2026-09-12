# -*- coding: utf-8 -*-
"""Перерисовать графику записей и разложить её по ресурсам мода."""
import os
import shutil

import font
import gui
import items

КОРЕНЬ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
АССЕТЫ = os.path.join(КОРЕНЬ, "plaguecore/src/main/resources/assets")


def положить(образ, *части):
    путь = os.path.join(АССЕТЫ, *части)
    os.makedirs(os.path.dirname(путь), exist_ok=True)
    образ.save(путь)
    print(os.path.relpath(путь, КОРЕНЬ))


def главное():
    лист, _ = font.лист()
    положить(лист, "plaguecore", "textures", "font", "tainopis.png")

    положить(gui.лист(), "minecraft", "textures", "gui", "book.png")
    for вперёд, ярче, имя in ((True, False, "page_forward"),
                              (True, True, "page_forward_highlighted"),
                              (False, False, "page_backward"),
                              (False, True, "page_backward_highlighted")):
        положить(gui.стрелка(вперёд, ярче),
                 "minecraft", "textures", "gui", "sprites", "widget", имя + ".png")

    имена = {"zapis_papers": "archive_record_papers",
             "zapis_chapel": "archive_record_chapel",
             "zapis_ledger": "archive_record_ledger"}
    for ключ, рисовать in items.ВСЕ.items():
        положить(рисовать(), "plaguecore", "textures", "item", имена[ключ] + ".png")


if __name__ == "__main__":
    главное()
