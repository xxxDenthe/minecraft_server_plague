# Plague Bloom — бутон чумы

Иконка предмета `lmpc_classes:plague_bloom`. Заменяет заглушку на ванильной
`minecraft:item/wither_rose`.

Бледный серый бутон на сухом стебле. Скупой фиолетовый — трещина, где видна
сама зараза. Это единственное цветное место на картинке, так и задумано.

---

## 1. Что где лежит

```
textures_src/plague_bloom/
├── assets/
│   ├── blockstates/plague_bloom_crop.json        ← age 0..7 -> восемь моделей
│   ├── models/block/plague_bloom_crop_stage0..7.json  ← восемь моделей грядки
│   ├── models/item/plague_bloom.json             ← правленая модель (было: wither_rose)
│   ├── textures/block/plague_bloom_crop_stage0..7.png ← восемь стадий роста
│   └── textures/item/plague_bloom.png            ← иконка 16x16
├── data/
│   └── lmpc_classes/loot_table/blocks/plague_bloom_crop.json  ← что падает
├── source/
│   ├── plague_bloom.py                  ← рисует иконку
│   ├── plague_bloom_crop.py             ← рисует восемь стадий
│   └── pngio.py                         ← запись PNG
└── README.md
```

Всё это **уже стоит в моде**, в `lmpc_classes/src/main/resources/`.
Здесь лежит исходник, чтобы можно было переделать.

Перезаписан был один файл — `models/item/plague_bloom.json` (была заглушка
на ванильную `wither_rose`). Остальное добавлено новым.

Разница в модели ровно одна строка:

```diff
- "textures": { "layer0": "minecraft:item/wither_rose" }
+ "textures": { "layer0": "lmpc_classes:item/plague_bloom" }
```

Регистрация, язык и рецепты уже есть — трогать не надо:

- `ClassItems.java` → `ПРЕДМЕТЫ.registerSimpleItem("plague_bloom")`
- `lang/ru_ru.json` → «Бутон чумы»
- `data/lmpc_classes/recipe/plague_bloom.json` → крафт из `blighted_grass` ×3 + `spore_sac`
- `data/lmpc_classes/recipe/cleansing_agent.json` → 2 бутона + бутылка

---

## 2. Палитра

По заметке `docs/superpowers/notes/2026-09-03-palitra-chumy.md`, с правкой
от 2026-09-04: **серые нейтрально-тёплые, без синевы и без лиловости.**

| Цвет | Где |
|---|---|
| `#1c1a18` `#2c2b28` | контур, самые тёмные места |
| `#403c36` `#544f47` | стебель, листья, тень бутона |
| `#6b6559` `#847d6f` `#9d9587` | тело бутона, сухая бледная кожура |
| `#4e3654` `#6b4a72` | **фиолет — 4 пикселя**, трещина в бутоне |

Фиолетового намеренно почти нет. Правило из спека: если его много —
получается колдовство, а у нас эпидемия. `plague_bloom` в списке мест,
где акцент разрешён (сама зараза, а не поражённая ею вещь).

---

## 3. Правка картинки

Правь `source/plague_bloom.py`, потом из папки `source/`:

```
python plague_bloom.py
```

Рядом лягут два файла:

- `plague_bloom.png` — иконка, её копировать в мод
- `plague_bloom_preview.png` — та же картинка ×16, чтобы глазами посмотреть

Форма бутона задана словарём `BUD`: ключ — строка, значение — от какого до
какого пикселя закрашивать. Хочешь бутон толще или уже — правь только его,
тень и контур пересчитаются сами.

---

## 4. Грядка: восемь стадий роста

Картинки, модели, blockstate и таблицу дропа я сделал. Остался код блока.

### 4.1. Как выглядит рост

Не один стебель, а **кустик из нескольких ростков** — так же устроена
ванильная пшеница. Ростков со стадиями становится больше, а не только выше.

| Стадия | Что видно |
|---|---|
| 0 | три коротких ростка, чуть выше земли |
| 1–2 | ростков четыре-пять, тянутся вверх |
| 3 | шестой росток, появляются сухие листья |
| 4–5 | на верхушках завязываются бутоны |
| 6 | бутоны налились, ростков семь-восемь |
| 7 | **зрелая** — бутоны треснули, видно фиолет. Собирать |

Фиолетовый есть **только на стадии 7**. Это удобно: издалека видно,
какая грядка готова, а какая ещё нет.

Текстуры нарисованы под ванильную модель `minecraft:block/crop` —
две скрещенные плоскости. Поэтому в игре кустик выглядит гуще,
чем на плоской картинке.

### 4.2. Блок

```java
public class PlagueBloomCropBlock extends CropBlock {

    public static final MapCodec<PlagueBloomCropBlock> CODEC =
        simpleCodec(PlagueBloomCropBlock::new);

    public PlagueBloomCropBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    /** Что сажают, чтобы получить эту культуру. */
    @Override
    protected ItemLike getBaseSeedId() {
        return ClassItems.PLAGUE_BLOOM.get();
    }
}
```

Регистрация блока:

```java
public static final DeferredBlock<Block> PLAGUE_BLOOM_CROP =
    БЛОКИ.register("plague_bloom_crop",
        () -> new PlagueBloomCropBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .noCollission()
            .randomTicks()          // без этого не растёт вообще
            .instabreak()
            .sound(SoundType.CROP)
            .pushReaction(PushReaction.DESTROY)));
```

### 4.3. Сделать бутон сажаемым

Сейчас в `ClassItems.java` стоит:

```java
ПРЕДМЕТЫ.registerSimpleItem("plague_bloom");
```

Надо заменить на предмет, который умеет сажать блок:

```java
public static final DeferredItem<Item> PLAGUE_BLOOM =
    ПРЕДМЕТЫ.register("plague_bloom",
        () -> new ItemNameBlockItem(
            ClassBlocks.PLAGUE_BLOOM_CROP.get(), new Item.Properties()));
```

`ItemNameBlockItem` — это ровно тот класс, на котором сидят ванильные
пшеница, морковь и картошка: в инвентаре обычный предмет, ПКМ по грядке сажает.

> Порядок регистрации важен: блоки должны регистрироваться до предметов,
> иначе `ClassBlocks.PLAGUE_BLOOM_CROP.get()` упадёт. У ванильных модов
> это решается тем, что `DeferredItem` создаётся лениво — лямбда `() -> ...`
> выполнится позже. Не вытаскивай `.get()` наружу из лямбды.

### 4.4. Прозрачность — не забудь

Культуры рисуются с прозрачностью. Без этой строки грядка будет
чёрными квадратами:

```java
@SubscribeEvent
public static void clientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
        ClassBlocks.PLAGUE_BLOOM_CROP.get(), RenderType.cutout()));
}
```

### 4.5. Название блока

В `lang/ru_ru.json` и `en_us.json`:

```json
"block.lmpc_classes.plague_bloom_crop": "Бутон чумы"
```

### 4.6. Что падает

`data/lmpc_classes/loot_table/blocks/plague_bloom_crop.json` уже готов:

- сломал незрелую — вернётся один бутон, который сажал
- сломал зрелую (age 7) — один бутон плюс бонус от Удачи

Если хочешь, чтобы зрелая давала 2–3 бутона всегда, а не только с Удачей —
скажи, добавлю второй пул.

### 4.7. Чего тут ещё нет

По спеку бутон **сначала** добывается диким кустом в Гнили, и только Фермер
может его выращивать. Ни дикого куста, ни проверки класса тут нет —
это отдельная работа. Сейчас грядку сможет засадить кто угодно.

---

## 5. Правка стадий роста

Правь `source/plague_bloom_crop.py`, потом из папки `source/`:

```
python plague_bloom_crop.py
```

Рядом лягут девять файлов: восемь `plague_bloom_crop_stage0..7.png` (их копировать
в `assets/textures/block/`) и `plague_bloom_crop_stages_preview.png` — все восемь
стадий в ряд на фоне неба и земли, чтобы глазами проверить контраст.

Что где крутить:

| Переменная | Что задаёт |
|---|---|
| `STALKS` | список ростков: `(x, насколько ниже, оттенок, сторона листа, с какой стадии растёт)` |
| `TOPS` | до какой строки дотягивается самый высокий росток, по стадиям |
| `BUDS` | размер бутона `(высота, ширина)`, по стадиям |
| `BUDDED` | сколько ростков несут бутон, по стадиям |

Хочешь гуще — добавь строку в `STALKS`. Хочешь, чтобы созревало дольше —
растяни `TOPS`.
