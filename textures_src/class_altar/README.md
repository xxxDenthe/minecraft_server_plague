# Altar — человеческий алтарь

Каменный алтарь с золотой чашей, свечами и красной тканью.
Палитра **тёплая и чистая** — специально не такая, как у заражённых блоков.
Это метка живых людей, а не заразы.

Высота 24 пикселя = 1.5 блока. 21 куб, одна текстура 64x64.

---

## 1. Что где лежит

Текстура и модель **уже стоят в моде** — заменили заглушку на ванильных
`chiseled_stone_bricks`. Здесь лежит исходник, чтобы можно было переделать.

```
textures_src/class_altar/
├── assets/
│   ├── models/block/class_altar.json   ← копия того, что стоит в моде
│   └── textures/block/class_altar.png  ← атлас 64x64
├── source/
│   ├── class_altar.bbmodel             ← исходник для Blockbench
│   ├── class_altar.py                  ← рисует текстуру
│   └── pngio.py                        ← запись PNG
└── README.md
```

Куда это встало в моде:

| Файл | Путь в `lmpc_classes` |
|---|---|
| модель | `src/main/resources/assets/lmpc_classes/models/block/class_altar.json` |
| текстура | `src/main/resources/assets/lmpc_classes/textures/block/class_altar.png` |

`blockstates/class_altar.json` и `models/item/class_altar.json` **не трогали** —
они и так правильные.

---

## 2. Палитра

Заражённые блоки сидят на обесцвеченном тёмно-сером. Алтарь — наоборот.

| Цвет | Где |
|---|---|
| `#b8ae9a` `#8a8073` `#6b6358` | тёсаный камень, тёплый бежево-серый |
| `#d8b44a` `#eccf72` `#a8802a` | золото: чаша, подсвечники, бахрома, солнце |
| `#8f2b2b` `#a83636` `#5a1919` | красная ткань |
| `#ece3c8` | восковые свечи |
| `#f6c245` `#e07a1e` | огонь |

Если делаешь ещё «человеческие» блоки — держись этих цветов, тогда всё сложится в один набор.

---

## 3. Раскладка атласа 64x64

Плитки по 16x16.

| Позиция | Плитка |
|---|---|
| (0, 0) | `stone_side` — резной камень с рамкой |
| (16, 0) | `stone_top` — плита сверху, швы |
| (32, 0) | `cloth` — красная ткань, золотая бахрома снизу |
| (48, 0) | `gold` — полированное золото |
| (0, 16) | `tablet` — камень с золотым солнцем |
| (16, 16) | `stone_dark` — низ и тень |
| (32, 16) | `wax` — воск свечи |
| (48, 16) | `flame` — огонь, фон прозрачный |

Камень натянут пиксель-в-пиксель по мировым координатам, поэтому кубы стыкуются
без швов. Золото, ткань, солнце и огонь растянуты на всю грань.

---

## 4. Кубы

Три группы:

| Группа | Что внутри |
|---|---|
| `pedestal` | `base`, `base_trim`, `shaft`, `cornice` — ступенчатая тумба, y 0..12 |
| `table` | `slab`, ткань (`cloth_*`), задняя плита `reredos` с солнцем, y 12..24 |
| `regalia` | чаша (`chalice_*`), свечи (`candle_*`), огонь (`flame_*`) |

Лицо алтаря — сторона **north** (−Z). Солнце и чаша смотрят туда же.

---

## 5. Что ещё надо в коде

Блок `ClassAltarBlock` уже есть и работает. Но модель теперь **не сплошной куб
и выше 16 пикселей**, поэтому две вещи в `ClassBlocks.java` надо поправить.

### 5.1. noOcclusion — обязательно

Без этого Minecraft считает блок сплошным и обрезает грани у соседей.
Алтарь будет выглядеть так, будто в мире дырка.

```java
BlockBehaviour.Properties.of()
    .mapColor(MapColor.STONE)
    .strength(2.0F, 6.0F)
    .sound(SoundType.STONE)
    .lightLevel(s -> 7)   // свечи горят
    .noOcclusion()        // <- вот это
```

### 5.2. Хитбокс на 1.5 блока — по желанию

Сейчас у блока обычный куб 16×16×16, а модель торчит вверх на 24. Верхушку
не выделить прицелом. Если мешает:

```java
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 0, 1, 15, 12, 15),   // тумба
        Block.box(0, 12, 0, 16, 24, 16)   // стол и всё что сверху
    );

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p,
                                  CollisionContext c) {
        return SHAPE;
    }
```

Верхняя половина торчит в блок сверху. Игрок пройдёт сквозь неё — для декора
нормально. Честная коллизия на два блока — это `DoubleBlockHalf`, как у двери.

### 5.3. Поворот — пока нет

Алтарь смотрит лицом на **north** и не крутится: у `ClassAltarBlock` нет
свойства `FACING`, и blockstate у него одновариантный. Если нужен поворот —
это `HorizontalDirectionalBlock`, `createBlockStateDefinition`,
`getStateForPlacement` плюс четыре варианта в blockstate. Скажи, сделаю.

---

## 6. Приятные мелочи

**Частицы пламени.** Огоньки на свечах не двигаются. Оживи их:

```java
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (rnd.nextInt(6) != 0) return;
        // свечи стоят на x = 3/16 и 13/16, огонь на y = 22/16, z = 7/16
        for (double dx : new double[]{ 3.0 / 16, 13.0 / 16 }) {
            level.addParticle(ParticleTypes.SMALL_FLAME,
                pos.getX() + dx, pos.getY() + 22.0 / 16, pos.getZ() + 7.0 / 16,
                0.0, 0.0, 0.0);
        }
    }
```

Направление свечей не поворачивается вместе с блоком — координаты выше верны
только для `facing=north`. Разверни их через `state.getValue(FACING)`, если заметно.

**Светящийся огонь.** Хочешь, чтобы пламя светилось само по себе даже в темноте —
нужен emissive-рендер, ванильные json так не умеют. Проще оставить `lightLevel(7)`.

---

## 7. Если надо править

**Модель:** открой `source/altar.bbmodel` в Blockbench.
Формат — **Java Block/Item**, размер текстуры выставлен 64x64.
Box UV выключен, стоит per-face UV — не включай обратно, развёртка слетит.
Экспорт: `File → Export → Java Block/Item Model`.

**Текстуру:** правь `source/altar.py`, потом из папки `source/`:

```
python altar.py
```

Скрипт кладёт свежий `altar.png` рядом с собой. Скопируй его в
`assets/textures/block/`.
