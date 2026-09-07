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


def зарегистрированные() -> list[str]:
    """Имена предметов, подключённых к подсказкам Create."""
    поля = re.findall(r"подключить\(ClassItems\.(\w+)\)", ИСХОДНИК.read_text(encoding="utf-8"))
    исходник = ПРЕДМЕТЫ.read_text(encoding="utf-8")
    имена = []
    for поле in поля:
        # `registerItem("censer", ...)` / `registerSimpleItem("cleansing_agent")`
        совпадение = re.search(
            поле + r"\s*=[\s\S]{0,200}?register\w*\(\s*\n?\s*\"([a-z_]+)\"", исходник)
        if not совпадение:
            sys.exit(f"не нашёл имя предмета для ClassItems.{поле}")
        имена.append(совпадение.group(1))
    return имена


def main() -> int:
    языки = {ф.stem: json.loads(ф.read_text(encoding="utf-8")) for ф in ЯЗЫКИ.glob("*.json")}
    беды = []

    for предмет in зарегистрированные():
        корень = f"item.lmpc_classes.{предмет}.tooltip"
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
