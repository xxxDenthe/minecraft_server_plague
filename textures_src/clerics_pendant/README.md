# Clerics Pendant — кулон клирика

Кулон на шею (`lmpc_classes:clerics_pendant`): красный шнур и золотой
медальон с солнцем. Заменяет заглушку на ванильном `heart_of_the_sea`.
Та же палитра, что у алтаря — знак солнца тот же, что на задней плите.

Это **предмет, а не блок**. Поставить его нельзя, только носить и держать в руке.

---

## 1. Что где лежит

Текстура **уже стоит в моде**. Здесь лежит исходник, чтобы можно было переделать.

```
textures_src/clerics_pendant/
├── assets/
│   ├── models/item/clerics_pendant.json   ← копия того, что в моде
│   └── textures/item/clerics_pendant.png  ← иконка 16x16
├── source/
│   ├── clerics_pendant.py                 ← рисует иконку
│   └── pngio.py                           ← запись PNG
└── README.md
```

Куда это встало в моде:

| Файл | Путь в `lmpc_classes` |
|---|---|
| модель | `src/main/resources/assets/lmpc_classes/models/item/clerics_pendant.json` |
| иконка | `src/main/resources/assets/lmpc_classes/textures/item/clerics_pendant.png` |

---

## 2. Почему для предмета не нужен Blockbench

Обычный предмет в майнкрафте — это просто картинка 16×16. Игра сама делает из неё
тонкую пластинку в руке. Модель нужна одна и та же для всех таких предметов:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "lmpc_classes:item/clerics_pendant"
  }
}
```

Blockbench нужен только если предмет **объёмный** в руке — как трезубец или
подзорная труба. Тогда берётся формат «Java Item» и настраиваются `display`-позы.

---

## 3. Что уже готово в коде

Трогать ничего не надо, всё это уже есть в `lmpc_classes`:

| Что | Где |
|---|---|
| предмет | `ClericsPendantItem.java`, `ICurioItem` без переопределений |
| слот «ожерелье» | `data/curios/tags/item/necklace.json` |
| крафт | `data/lmpc_classes/recipe/clerics_pendant.json` — золотой слиток + нить + `plaguecore:spore_sac` |
| названия | `lang/ru_ru.json`, `lang/en_us.json` |
| эффект | `ClassesApi.protectionBonus` — снижает заражение Клирику в кулоне |

Curios у тебя стоит: `mods/curios-neoforge-9.5.1+1.21.1.jar`.

---

## 4. Если надо править картинку

Правь `source/clerics_pendant.py`, потом из папки `source/`:

```
python clerics_pendant.py
```

Скрипт кладёт рядом два файла:

- `clerics_pendant.png` — сама иконка, её копировать в `assets/textures/item/`
- `clerics_pendant_preview.png` — та же картинка, увеличенная в 16 раз, чтобы глазами посмотреть

Палитра внутри скрипта, вверху:

| Переменная | Что это |
|---|---|
| `G` | золото, 5 оттенков от тёмного к светлому |
| `R` | красный шнур, 4 оттенка |

Держи эти же цвета для остальных «человеческих» вещей — тогда набор будет цельным.
