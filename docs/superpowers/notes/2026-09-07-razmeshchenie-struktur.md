# Размещение готовых строений из модов вручную

**Дата:** 2026-09-07. **Вопрос:** ставить дома из Dungeons and Taverns
и прочих модов в реальном времени, удобно.

## Что выяснено

- Axiom 6.0.5 **не читает** ванильные `.nbt`-структуры. Его импорт
  (`File → Import Schematic`) понимает только Sponge `.schem`,
  MCEdit `.schematic` и Litematica `.litematic`.
- WorldEdit 7.3.8 тоже без ванильного `.nbt`: форматы только
  `MCEDIT_SCHEMATIC` и `SPONGE_V1/V2/V3`.
- Строения D&T нарезаны на 3312 кусочков-джигсо. Один `.nbt` — это
  одна комната, а не дом. Собирать их вручную бессмысленно.

Вывод: конвертировать `.nbt` не надо. Ванильная команда `/place`
собирает целое строение сама.

## Как ставить (рабочий способ)

```
/place structure <id> [x y z]
```

Ставит целое строение со всеми кусочками, сундуками и мобами в точке
(по умолчанию — под тобой). Tab дополняет список. Нужен креатив/оп.
Отмены нет — только Axiom-выделением стереть.

Отдельный кусочек, если нужен именно он:

```
/place template <id> [x y z] [поворот] [зеркало] [целостность]
```

## Чтобы возить кистью с предпросмотром

`/place structure` один раз в тестовом мире → выделить в Axiom →
`Create Blueprint` → дальше ставить блупринт-кистью: превью, поворот,
отмена. Это единственный путь получить Axiom-блупринт из модовой
структуры, и он одноразовый на каждое строение.

## Список целых структур в паке (148)

### Galosphere-1.21.1-1.5.5-NeoForge.jar
```
galosphere:forgotten_ruins
galosphere:pink_salt_shrine
```

### aquamirae-neoforge-1.21.1-7.2.4.jar
```
aquamirae:pirate_outpost
aquamirae:pirate_shelter
aquamirae:pirate_ship
aquamirae:shipwreck
```

### dungeons-and-taverns-v4.4.4.jar
```
minecraft:village_taiga
nova_structures:badlands_miner_outpost
nova_structures:bunker
nova_structures:conduit_ruin
nova_structures:creeping_crypt
nova_structures:deepslate_camp
nova_structures:desert_ruins
nova_structures:end_castle
nova_structures:end_lighthouse
nova_structures:end_ship
nova_structures:firewatch_tower_birch
nova_structures:firewatch_tower_cherry
nova_structures:firewatch_tower_dark_oak
nova_structures:firewatch_tower_forest
nova_structures:firewatch_tower_jungle
nova_structures:firewatch_tower_mangrove
nova_structures:firewatch_tower_savanna
nova_structures:firewatch_tower_swamp
nova_structures:firewatch_tower_taiga
nova_structures:hamlet
nova_structures:illager_camp
nova_structures:illager_hideout
nova_structures:illager_manor
nova_structures:jungle_ruins
nova_structures:lone_citadel
nova_structures:mangrove_witch_hut
nova_structures:nether_keep
nova_structures:nether_port
nova_structures:nether_skeleton_tower_crimson
nova_structures:nether_skeleton_tower_soul
nova_structures:nether_skeleton_tower_warped
nova_structures:nether_skeleton_tower_waste
nova_structures:piglin_camp
nova_structures:piglin_donjon
nova_structures:piglin_outstation
nova_structures:remnant_bee_keeper
nova_structures:remnant_big_remnant
nova_structures:remnant_big_remnant_2
nova_structures:remnant_big_remnant_3
nova_structures:remnant_birch_graveyard
nova_structures:remnant_bridge_remnant
nova_structures:remnant_bunny_base
nova_structures:remnant_classic_village
nova_structures:remnant_desert_remnant
nova_structures:remnant_forest_smith
nova_structures:remnant_frog_ranch
nova_structures:remnant_graveyard
nova_structures:remnant_medium_remnant
nova_structures:remnant_medium_remnant_2
nova_structures:remnant_miner_hut
nova_structures:remnant_mud_brick_constructor
nova_structures:remnant_ominous_shop
nova_structures:remnant_ruin_farmer
nova_structures:remnant_ruin_smith
nova_structures:remnant_sawmill
nova_structures:remnant_school_remnant
nova_structures:remnant_taiga_castle
nova_structures:remnant_woodland_hud
nova_structures:remnant_zombie_horse_ranch
nova_structures:ruin_town
nova_structures:shrine_combat_tier_1
nova_structures:shrine_combat_tier_2
nova_structures:shrine_combat_tier_3
nova_structures:shrine_combat_tier_4
nova_structures:shrine_combat_tier_5
nova_structures:shrine_tower
nova_structures:skeleton_camp_crimson
nova_structures:skeleton_camp_soul
nova_structures:skeleton_camp_warped
nova_structures:skeleton_camp_waste
nova_structures:stray_fort
nova_structures:tavern_acacia
nova_structures:tavern_birch
nova_structures:tavern_cherry
nova_structures:tavern_dark_oak
nova_structures:tavern_desert
nova_structures:tavern_jungle
nova_structures:tavern_mangrove
nova_structures:tavern_oak
nova_structures:tavern_snowy
nova_structures:tavern_spruce
nova_structures:tavern_swamp
nova_structures:toxic_lair
nova_structures:trident_trial_monument
nova_structures:undead_crypt
nova_structures:underground_house
nova_structures:village_birch
nova_structures:village_jungle
nova_structures:village_swamp
nova_structures:well_birch
nova_structures:well_dark_oak
nova_structures:well_jungle
nova_structures:well_oak
nova_structures:well_savana
nova_structures:well_spruce
nova_structures:wild_ruin
nova_structures:witch_villa
```

### supplementaries-1.21.1-3.9.6-neoforge.jar
```
supplementaries:galleon
supplementaries:road_sign
```

### t_and_t-fabric-neoforge-1.13.11.jar
```
towns_and_towers:mimic_desert
towns_and_towers:pillager_outpost_badlands
towns_and_towers:pillager_outpost_beach
towns_and_towers:pillager_outpost_birch_forest
towns_and_towers:pillager_outpost_desert
towns_and_towers:pillager_outpost_flower_forest
towns_and_towers:pillager_outpost_forest
towns_and_towers:pillager_outpost_grove
towns_and_towers:pillager_outpost_jungle
towns_and_towers:pillager_outpost_meadow
towns_and_towers:pillager_outpost_mushroom_fields
towns_and_towers:pillager_outpost_ocean
towns_and_towers:pillager_outpost_old_growth_taiga
towns_and_towers:pillager_outpost_savanna
towns_and_towers:pillager_outpost_savanna_plateau
towns_and_towers:pillager_outpost_snowy_beach
towns_and_towers:pillager_outpost_snowy_plains
towns_and_towers:pillager_outpost_snowy_slopes
towns_and_towers:pillager_outpost_snowy_taiga
towns_and_towers:pillager_outpost_sparse_jungle
towns_and_towers:pillager_outpost_sunflower_plains
towns_and_towers:pillager_outpost_swamp
towns_and_towers:pillager_outpost_taiga
towns_and_towers:pillager_outpost_wooded_badlands
towns_and_towers:village_badlands
towns_and_towers:village_beach
towns_and_towers:village_birch_forest
towns_and_towers:village_flower_forest
towns_and_towers:village_forest
towns_and_towers:village_grove
towns_and_towers:village_jungle
towns_and_towers:village_meadow
towns_and_towers:village_mushroom_fields
towns_and_towers:village_ocean
towns_and_towers:village_old_growth_taiga
towns_and_towers:village_savanna_plateau
towns_and_towers:village_snowy_slopes
towns_and_towers:village_snowy_taiga
towns_and_towers:village_sparse_jungle
towns_and_towers:village_sunflower_plains
towns_and_towers:village_swamp
towns_and_towers:village_wooded_badlands
towns_and_towers:wreckage_ocean
```


---

## Дополнение того же дня: каталог и предпросмотр

Владельцу нужен предпросмотр перед постановкой. Предпросмотр умеет
только Axiom (блупринт-браузер: миниатюры, призрак под курсором,
поворот, отмена). Модовые структуры туда попадают лишь одним путём:
поставить в мире → выделить → `Create Blueprint`.

Чтобы не вводить `/place structure` 148 раз, сделан датапак
`tools/structure_catalog/`: 11 функций, каждая ставит до 16 строений
сеткой 4×4 с шагом 128 вокруг игрока. Ставить в `saves/<мир>/datapacks/`.

`lmpc_gmtools` (панель мастера) сюда не трогали: папка Kuragane.
Если захочется кнопку «поставить строение» в панели — это к нему.

**Axiom в игровой папке не установлен** — джарник есть только
в `MODSS/`. Без него предпросмотра не будет.

---

## Конвертер .nbt -> .schem сделан

`tools/nbt2schem.py` — чистый Python 3, без зависимостей. Читает
ванильные структуры из джарника мода, папки или одного файла и пишет
Sponge schematic v2, который читают и Axiom (`File -> Import Schematic`),
и WorldEdit (`//schem load`).

```
python tools/nbt2schem.py mods/<мод>.jar -o <куда> --min-blocks 200
python tools/nbt2schem.py --selftest
```

Решения по формату:

- **Sponge v2, а не v3.** Axiom умеет оба (`SchematicLoader$SpongeSchematic`
  требует `Version`, `DataVersion`, `Width/Height/Length`, `Palette`,
  `BlockData`), WorldEdit 7.3.8 тоже. V2 проще и в нём меньше мест
  ошибиться.
- **`structure_void` -> воздух.** Иначе при вставке в мир появлялись бы
  невидимые блоки.
- **Сущности не переносятся.** В ванильных структурах их почти нет,
  а в v2 они требуют отдельной возни. Блок-сущности (сундуки с лутом,
  таблички, подозрительный песок) переносятся полностью.
- `--min-blocks N` отсекает мусор: из 3312 файлов D&T больше половины —
  маркеры-джигсо на пару блоков.

Грабля, на которой конвертер сначала падал: в Python в присваивании
`d[self.string()] = (t, self.payload(t))` правая часть считается **до**
ключа, поэтому NBT-читалка глотала значение раньше имени тега. Имя
теперь читается в переменную отдельной строкой.

Проверено: самопроверка на синтетической структуре 2×1×2; сверка
готового `.schem` с исходным `.nbt` (размеры, число блок-сущностей,
200 случайных точек); декодирование варинтов в 41 файле даёт ровно
W×H×L блоков, индексы не вылезают за палитру. **В самой игре не
открывалось ни разу** — этого я проверить не могу.

## Что уже лежит в игровой папке

1638 схематик, 7.8 МБ:

```
AppData\Roaming\LMPC\instance\config\worldedit\schematics\mods\
  nova_structures\   Dungeons and Taverns  (1348)
  kaisyn\            Towns and Towers       (245)
  galosphere\ (31), aquamirae\ (7), vinery\ (3), supplementaries\ (2),
  create\ (1), farmersdelight\ (1), minecraft\
```

**Важно:** это куски джигсо, а не целые здания. Целое здание —
по-прежнему `/place structure`, каталог в `tools/structure_catalog/`.

---

## Ревизия 2: целые здания, а не куски

Кусочные схематики владельца не устроили — нужны целые дома
с предпросмотром, желательно блюпринтами Axiom. Куски джигсо этого
не дают в принципе.

**Решение: сборку делает сама игра, мы только вынимаем результат.**
Собственный сборщик джигсо писать не стали — это сотни строк тонкой
математики поворотов и стыков, которую негде проверить. Игра уже
умеет это правильно.

```
tools/make_catalog.py    сканирует mods/, строит датапак-каталог
                         + slots.json с координатами каждого слота
tools/structure_catalog/ сам датапак: go_N (телепорт) и place_N (16 строений)
tools/world2schem.py     читает region/*.mca и вырезает здания в .schem
```

Сетка: партия 4×4, шаг 256 блоков, партии разнесены по X на 2048.
148 строений — 10 партий, 20 команд в игре, один раз.

Решения:

- **Обычный суперплоский, а не пустота.** У структур с
  `project_start_to_heightmap` в пустоте нет поверхности, на которую
  проецироваться. Слои плоского мира (`bedrock/dirt/dirt/grass_block`
  на y −64…−61) `world2schem.py` отбрасывает сам, ключ `--keep-ground`
  отключает отсев.
- **Границы здания ищем по факту**, а не по заявленным размерам:
  всё непустое внутри слота ±128 и есть здание. Поэтому не нужны
  ни bounding box структуры, ни разбор `structures` в чанке.
- Секции с палитрой из одного воздуха пропускаются сразу — в плоском
  мире это почти весь объём, иначе Python не выгреб бы.

Проверено: обе самопроверки (`--selftest`) зелёные; читалка Anvil
проверена на живом мире `LMPC/instance/saves/New World` — 1578 чанков,
DataVersion 3955, в полном чанке 43734 непустых блока с осмысленным
составом (камень, глубинный сланец, андезит, коренная порода).
**Сам проход «поставить в мире → вынуть» ни разу не прогонялся** —
для этого нужна запущенная игра.

### Что делать с кусочными схематиками

1638 файлов от ревизии 1 лежат в
`LMPC/instance/config/worldedit/schematics/mods/`. Целым зданиям они
не мешают, но забивают браузер Axiom. Удалять или нет — решает владелец.

### Передача второму участнику: RUN.bat

Шаги «скопируй датапак в мир» и «выполни команду в терминале»
оказались непонятны. Добавлены `tools/run.py` (меню: положить датапак /
достать здания, само ищет папки игры и миры) и `tools/RUN.bat`.
Архив для передачи собирается в `C:\Users\penis\Desktop\structure_catalog.zip`.

Две грабли Windows, обе нашлись только при живом запуске:

- **Кириллица в имени .bat.** `cmd /c ЗАПУСТИ_МЕНЯ.bat` не находит файл:
  имя не переживает переход в OEM-кодировку консоли. Имя должно быть ASCII.
- **Кириллица внутри .bat вместе с `chcp 65001`.** Смена кодовой страницы
  посреди файла сбивает разбор всего, что идёт дальше: строки рвались
  на куски вида `'язательно' is not recognized`. Сам `.bat` теперь
  чисто ASCII, все русские тексты печатает Python.

Третья грабля, нашлась только двойным кликом: **`python` может быть
.bat-обёрткой.** У владельца стоит pyenv-win, и `where python` первым
отдаёт `shims\python.bat`. Запуск одного `.bat` из другого без `call`
передаёт управление и назад не возвращает — окно закрывалось мгновенно,
до всякого вывода. В `RUN.bat` теперь `call python ...`, запасной
вариант `call py ...`, и `pause` в конце при любом исходе. Чтобы не
просить Enter дважды, `run.py` пропускает свой финальный `input`,
когда видит переменную `LMPC_FROM_BAT`.

Автоопределение папки игры у второго тестера промахнулось (нестандартный
лаунчер). Теперь `run.py` всегда **спрашивает путь**, а найденные папки
печатает лишь подсказкой. `normalize_game` прощает то, что реально
перетаскивают мышкой: кавычки от проводника, саму папку игры, `saves`,
конкретный мир, `mods`/`config`. Проверено восемью вариантами ввода.
