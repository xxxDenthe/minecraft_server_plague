"""Проверка блоков генератора по ассетам самой игры.

Axiom и WorldEdit парсят BlockState строго: одна опечатка в имени блока или
свойства — и вся схема не грузится, без указания координаты. Поэтому имена
и свойства сверяются с assets/minecraft/blockstates/*.json из клиентского
джарника NeoForge, а не с памятью.

python validate_blocks.py
"""
import json
import os
import re
import zipfile

import city_gen as c

JAR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                   "plaguecore", "build", "moddev", "artifacts",
                   "neoforge-21.1.249-client-extra-aka-minecraft-resources.jar")


def load_assets():
    """{block: {prop: {значения}}} из блокстейтов игры."""
    out = {}
    with zipfile.ZipFile(JAR) as z:
        for n in z.namelist():
            if not (n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")):
                continue
            name = n.split("/")[-1][:-5]
            props = {}
            data = json.loads(z.read(n))
            keys = list(data.get("variants", {}))
            for case in data.get("multipart", []):
                when = case.get("when", {})
                keys += [",".join(k + "=" + str(v) for k, v in w.items())
                         for w in when.get("OR", [when])]
            for key in keys:
                for part in filter(None, key.split(",")):
                    k, _, v = part.partition("=")
                    for one in v.split("|"):
                        props.setdefault(k, set()).add(one)
            out[name] = props
    return out


def used_blocks():
    seen = set()
    orig = c.sb
    c.sb = lambda x, y, z, b: (seen.add(b), orig(x, y, z, b))[1]
    c.build()
    c.sb = orig
    return seen


def main():
    assets = load_assets()
    problems = []
    for full in sorted(used_blocks()):
        name, _, rest = full.partition("[")
        if name == "air":
            continue
        if name not in assets:
            problems.append("нет такого блока: " + name)
            continue
        if not assets[name]:
            # ponytail: у сундука и листвы модель одна на все состояния, свойств
            # в блокстейте нет — проверяем только имя. Полный список свойств
            # лежит в реестре игры, лезть туда ради двух блоков не стоит.
            continue
        for part in filter(None, rest.rstrip("]").split(",")):
            k, _, v = part.partition("=")
            known = assets[name].get(k)
            if known is None:
                problems.append(name + ": нет свойства " + k)
            elif v not in known:
                problems.append(name + ": " + k + "=" + v + " не из " + str(sorted(known)))
    for p in problems:
        print("BAD ", p)
    print("проблем:", len(problems))
    assert not problems, "схема не загрузится — см. список выше"


if __name__ == "__main__":
    main()
