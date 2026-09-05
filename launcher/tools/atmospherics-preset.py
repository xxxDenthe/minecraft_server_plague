#!/usr/bin/env python3
"""Пересчитывает штатный пресет Atmospherics в чумной серый.

Atmospherics владеет небом, солнцем, звёздами, облаками, туманом и дымкой;
lmpc_shade поверх этого только грейдит кадр. Чтобы оба смотрели в одну
палитру, берём фирменный пресет мода (source_biome_fog.json прямо из
джарника — 65 биомов, руками столько не покрасишь) и обесцвечиваем все
цвета к палитре чумы.

    python launcher/tools/atmospherics-preset.py
    python launcher/tools/atmospherics-preset.py --selftest

Результат: launcher/pack-config/config/ambientfog/biome_fog.json —
раздаётся игрокам вместе с модами.
"""

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]  # tools -> launcher -> корень репозитория
# Джарники не версионируются и обновляются — ищем по маске, а не по версии.
JARS = sorted((ROOT / "mods").glob("atmospherics-*.jar"))
SOURCE = "assets/atmospherics/presets/source_biome_fog.json"
# Папка конфига у мода называется ambientfog, а не atmospherics — старое имя проекта.
OUT = ROOT / "launcher" / "pack-config" / "config" / "ambientfog" / "biome_fog.json"

# Палитра чумы (та же, что дефолты lmpc_shade): туман #4A4842, тон #2C2B28.
TINT = (0x2C, 0x2B, 0x28)
SATURATION = 0.14  # сколько цветности оставить: 0 — чистое ЧБ, 1 — как было

# Спад светов: чем ярче исходный цвет, тем сильнее его гасим. Без этого
# облака и полуденное небо остаются белыми — обесцвечивание их не трогает,
# белый и так серый.
HIGHLIGHT_ROLLOFF = 0.35

# Яркость по группам ключей: ночь глушим сильнее, грозу — средне.
MUL_NIGHT = 0.70
MUL_THUNDER = 0.80
MUL_DAY = 0.92

# Ключи, где int — не цвет, несмотря на имя.
NOT_A_COLOR = {"colorPreset"}


def desaturate(argb: int, saturation: float = SATURATION, mul: float = 1.0) -> int:
    """0xAARRGGBB → тот же цвет, уведённый в серый цвета TINT. Альфа цела."""
    alpha = argb & ~0xFFFFFF  # хранит и знак: цвета в пресете бывают отрицательными
    r, g, b = (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF

    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    tint_avg = sum(TINT) / 3.0
    mul *= 1.0 - HIGHLIGHT_ROLLOFF * luma / 255.0

    out = []
    for channel, tint_channel in zip((r, g, b), TINT):
        v = luma + (channel - luma) * saturation  # обесцветить
        v *= tint_channel / tint_avg              # сдвинуть баланс в тон чумы
        v *= mul                                  # приглушить
        out.append(max(0, min(255, round(v))))

    return alpha | (out[0] << 16) | (out[1] << 8) | out[2]


def mul_for(key: str) -> float:
    k = key.lower()
    if "night" in k:
        return MUL_NIGHT
    if "thunder" in k:
        return MUL_THUNDER
    return MUL_DAY


def recolor(node, key: str = ""):
    """Рекурсивно красит всё, что похоже на цвет (int под ключом *color*)."""
    if isinstance(node, dict):
        return {k: recolor(v, k) for k, v in node.items()}
    if isinstance(node, list):
        return [recolor(v, key) for v in node]
    if isinstance(node, bool) or not isinstance(node, int):
        return node
    if "color" not in key.lower() or key in NOT_A_COLOR:
        return node
    return desaturate(node, mul=mul_for(key))


# Ручные правки поверх перекраски: то, что цветом не выражается.
OVERRIDES = {
    "fogDensity": 0.45,          # гуще ванильного: мир тонет в дымке
    "disableTwilightRing": True,  # без оранжевого кольца на закате
    "airHazeIntensity": 1.15,
    "sky": {"nightDarkening": 1.0, "tintStrength": 1.0, "fogBlendStrength": 0.55},
    "clouds": {
        "fogColorMixStrength": 0.8,   # облака одного цвета с туманом
        "nightDarkening": 0.45,
        "horizonFogBlend": 0.6,
        "autoHorizonFogBlend": True,
        "storeModeCloudLayerHeight": 110.0,  # ниже ванили — небо давит
    },
    "comets": {"enabled": False},  # кометы — эффектно и не про наш лор
}


def apply_overrides(cfg: dict) -> dict:
    for key, value in OVERRIDES.items():
        if isinstance(value, dict):
            cfg[key].update(value)
        else:
            cfg[key] = value
    return cfg


def build() -> dict:
    if not JARS:
        sys.exit("не нашёл mods/atmospherics-*.jar — положи джарник мода на место")
    with zipfile.ZipFile(JARS[-1]) as jar:
        source = json.loads(jar.read(SOURCE).decode("utf-8"))
    return apply_overrides(recolor(source))


def selftest():
    # Насыщенный оранжевый закат должен стать почти серым и темнее.
    orange = desaturate(0xFFA000)
    r, g, b = (orange >> 16) & 0xFF, (orange >> 8) & 0xFF, orange & 0xFF
    # разброс каналов был 255 — должен ужаться минимум вчетверо
    assert max(r, g, b) - min(r, g, b) < 255 / 4, f"осталась цветность: {orange:06X}"
    assert r >= b, "тон должен быть тёплым, а не лиловым"

    # Белое облако не должно остаться белым.
    assert ((desaturate(0xFFFFFF) >> 16) & 0xFF) < 190, "света не погасли"

    # Альфа и знак не теряются.
    assert desaturate(-9402664) < 0, "отрицательный ARGB перестал быть отрицательным"

    # Чёрное остаётся чёрным, ключи-не-цвета не трогаем.
    assert desaturate(0) == 0
    assert recolor({"colorPreset": 3}) == {"colorPreset": 3}
    assert recolor({"cloudNightColorDarkening": 0.9})["cloudNightColorDarkening"] == 0.9
    assert recolor({"fogColor": 0xFFA000})["fogColor"] != 0xFFA000

    cfg = build()
    assert cfg["version"] == 43, "версия пресета уехала — мод запустит миграцию"
    assert len(cfg["biomes"]) == 65
    assert cfg["comets"]["enabled"] is False
    print("selftest ok")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--selftest", action="store_true")
    args = p.parse_args()

    if args.selftest:
        selftest()
        sys.exit(0)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(build(), indent=2), encoding="utf-8")
    print(f"записано: {OUT.relative_to(ROOT)}")
