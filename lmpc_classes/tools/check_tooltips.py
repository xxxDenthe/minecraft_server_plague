"""Сторож подсказок Create: ключи в языковых файлах не должны разъехаться.

Подсказку рисует Create по ключам вида
`item.lmpc_classes.<предмет>.tooltip.summary` (см. `CreateTooltips.java`).
Опечатка в ключе или забытый перевод ничего не ломают — подсказка просто
молча исчезает или показывает сырой ключ, и заметить это можно только
наведясь на предмет в игре. Проверка ловит три беды:

* предмет зарегистрирован в `CreateTooltips`, а `summary` для него нет;
* `conditionN` есть, а парного `behaviourN` нет (или наоборот);
* строка есть в русском файле и отсутствует в английском.

Запуск: python tools/check_tooltips.py
"""
import json
import pathlib
import re
import sys

КОРЕНЬ = pathlib.Path(__file__).resolve().parent.parent
ЯЗЫКИ = КОРЕНЬ / "src/main/resources/assets/lmpc_classes/lang"
ИСХОДНИК = КОРЕНЬ / "src/main/java/dev/denthe/classes/client/CreateTooltips.java"
ПРЕДМЕТЫ = КОРЕНЬ / "src/main/java/dev/denthe/classes/ClassItems.java"
БЛОКИ = КОРЕНЬ / "src/main/java/dev/denthe/classes/ClassBlocks.java"


def зарегистрированные() -> list[str]:
    """Корни ключей подсказок: `item.lmpc_classes.<имя>.tooltip` и `block....`.

    Create строит ключ из `getDescriptionId()`, поэтому у блочных предметов
    (оба очистителя) он начинается с `block.`, а не с `item.`.
    """
    поля = re.findall(r"подключить\((ClassItems|ClassBlocks)\.(\w+)\)",
                      ИСХОДНИК.read_text(encoding="utf-8"))
    исходники = {"ClassItems": ПРЕДМЕТЫ.read_text(encoding="utf-8"),
                 "ClassBlocks": БЛОКИ.read_text(encoding="utf-8")}
    корни = []
    for откуда, поле in поля:
        блочный = откуда == "ClassBlocks"
        # У блочного предмета имени при нём нет, оно стоит при самом блоке:
        # `ANDESITE_PURIFIER_ITEM = ПРЕДМЕТЫ.registerSimpleBlockItem(ANDESITE_PURIFIER)`.
        искать = поле[:-len("_ITEM")] if блочный and поле.endswith("_ITEM") else поле
        # `registerItem("censer", ...)` / `registerSimpleItem("cleansing_agent")`
        # / `registerBlock("andesite_purifier", ...)`
        совпадение = re.search(
            искать + r'\s*=[\s\S]{0,200}?register\w*\(\s*\n?\s*"([a-z_]+)"', исходники[откуда])
        if not совпадение:
            sys.exit(f"не нашёл имя для {откуда}.{поле}")
        корни.append(f"{'block' if блочный else 'item'}.lmpc_classes.{совпадение.group(1)}.tooltip")
    return корни


def main() -> int:
    языки = {ф.stem: json.loads(ф.read_text(encoding="utf-8")) for ф in ЯЗЫКИ.glob("*.json")}
    беды = []

    for корень in зарегистрированные():
        for язык, строки in языки.items():
            if f"{корень}.summary" not in строки:
                беды.append(f"{язык}: нет {корень}.summary")
            for n in range(1, 10):
                условие = f"{корень}.condition{n}" in строки
                действие = f"{корень}.behaviour{n}" in строки
                if условие != действие:
                    беды.append(f"{язык}: {корень} — condition{n} и behaviour{n} не в паре")

    # Русский и английский должны знать одни и те же ключи подсказок.
    наборы = {я: {к for к in с if ".tooltip." in к} for я, с in языки.items()}
    for язык, ключи in наборы.items():
        for другой, чужие in наборы.items():
            if язык != другой:
                беды += [f"{другой}: нет ключа {к} (есть в {язык})" for к in sorted(ключи - чужие)]

    for беда in sorted(set(беды)):
        print(беда)
    print("подсказки в порядке" if not беды else f"бед: {len(set(беды))}")
    return 1 if беды else 0


if __name__ == "__main__":
    sys.exit(main())
