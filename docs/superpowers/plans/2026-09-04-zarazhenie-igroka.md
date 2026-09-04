# Заражение игрока — план реализации

> **Для агентов:** ОБЯЗАТЕЛЬНАЯ ПОД-СКИЛЛА: использовать
> superpowers:subagent-driven-development (рекомендуется) или
> superpowers:executing-plans, задача за задачей. Шаги помечены
> чекбоксами (`- [ ]`) — отмечать по мере выполнения.

**Цель:** сделать чуму опасной для человека — она копится в игроке,
отнимает здоровье, слышна, видна и передаётся между людьми.

**Архитектура:** вся математика — чистая Java в `core/InfectionMath`,
тестируется обычным JUnit. Пакет `mc` дёргает её из тика игрока
и переводит результат в атрибуты, эффекты и звуки. Все числа —
ручки в `PlagueConstants`, которые переписывает `PlagueConfig` из секции
`[player]`. Клиент знает только свою стадию, приходящую пакетом.

**Стек:** Minecraft 1.21.1, NeoForge 21.1.249, JDK 21, Gradle через
wrapper, JUnit 5.

**Спек:** `docs/superpowers/specs/2026-09-04-zarazhenie-igroka-design.md`

---

## Global Constraints

- Java 21, NeoForge 21.1.249, Minecraft 1.21.1, `modId = plaguecore`,
  пакет `dev.denthe.plaguecore`.
- **Пакет `core` не знает о Minecraft.** Ни одного `import net.minecraft`
  и `import net.neoforged`. Стережёт `CorePurityTest`. Не ослаблять.
- **Нефинальное поле `PlagueConstants` — ручка, финальное — устройство
  мода.** Каждое новое нефинальное поле обязано переписываться в
  `PlagueConfig.применить()`, иначе падает `PlagueConfigWiringTest`.
  Поэтому конфиг заводится первой задачей, а не последней.
- Комментарии, документы и коммиты — на русском.
- TDD: сначала падающий тест, потом минимальная реализация. Тесты
  пишутся только на `core` — игровой слой обычным JUnit не проверяется.
- Собирать и гонять тесты из папки `plaguecore/`:
  `./gradlew test --console=plain`
- Коммит после каждой задачи, пуш сразу после коммита.
- Работаем только в `plaguecore/`. Папку `launcher/` не трогать —
  она принадлежит второму участнику.

---

## Структура файлов

**Создаются:**

| Файл | Ответственность |
|---|---|
| `core/InfectionMath.java` | вся чистая математика: стадии, экспозиция, восстановление, отвар, штрафы здоровья |
| `mc/PlayerPlagueData.java` | Data Attachment на игроке: шесть полей и их регистрация |
| `mc/PlayerInfection.java` | тик игрока: накопление, стадия, эффекты, штраф за смерть |
| `mc/PlagueCough.java` | кашель, звук, частицы, передача между игроками |
| `mc/BrewItem.java` | предмет «отвар» и его действие |
| `mc/PlagueApi.java` | публичный интерфейс для подсистемы классов |
| `client/PlagueOverlay.java` | затемнение экрана по стадии |
| `src/test/.../core/InfectionMathTest.java` | тесты чистой математики |
| `src/main/resources/data/plaguecore/recipe/plague_brew.json` | рецепт отвара |
| `src/main/resources/assets/plaguecore/models/item/plague_brew.json` | модель предмета |

**Меняются:**

| Файл | Что добавляется |
|---|---|
| `PlagueConstants.java` | секция ручек игрока |
| `PlagueConfig.java` | секция `[player]` и её применение |
| `mc/PlagueBlocks.java` | регистрация предмета «отвар» |
| `mc/PlagueNetwork.java` | пакет `Stage` на клиент |
| `mc/PlagueCommands.java` | команда `/plague player` |
| `client/PlagueClientAccess.java` | хранение стадии на клиенте |
| `PlagueCore.java` | регистрация вложения данных |

---

## Порядок задач

```
1  Ручки и конфиг            все числа [player] сразу, тест проводки зелёный
2  InfectionMath             чистая математика + тесты
3  Данные и накопление       вложение, тик, /plague player
4  Эффекты стадий            здоровье, еда, регенерация, урон стадии 4
5  Кашель и передача         звук, частицы, радиус 6
6  Отвар                     предмет, рецепт, убывающая сила
7  Штраф за смерть           постоянный модификатор и два пола
8  Тусклый экран             пакет стадии + оверлей
9  PlagueApi                 интерфейс для подсистемы 3
10 Голос                     Simple Voice Chat, можно отложить
```

Задачи 1–2 не требуют запуска игры. Задачи 3–9 проверяются на дев-сервере
глазами. Задача 10 тянет новую зависимость и отделена нарочно.

---

### Task 1: Ручки и конфиг

Заводим все числа подсистемы разом. Первой задачей, а не последней:
`PlagueConfigWiringTest` падает, как только в `PlagueConstants` появилось
нефинальное поле без строчки в `PlagueConfig`, поэтому доводить конфиг
по кусочкам нельзя — сборка будет красной между задачами.

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConstants.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConfig.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/PlagueConfigWiringTest.java` (уже есть, менять не надо)

**Interfaces:**
- Consumes: ничего
- Produces: поля `PlagueConstants.PLAYER_*`, которыми пользуются все
  остальные задачи. Точные имена и типы — в шаге 1.

- [ ] **Step 1: Дописать ручки в `PlagueConstants.java`**

Вставить в конец класса, перед закрывающей скобкой:

```java
    // ── игрок ─────────────────────────────────────────────────────────
    // Подсистема 2, спек 2026-09-04-zarazhenie-igroka-design.md.

    /** Раз во сколько тиков пересчитывается заражённость игрока. */
    public static int PLAYER_TICK_INTERVAL = 20;

    /**
     * Нижние границы стадий 1–4. Стадия 0 — всё, что ниже первой границы.
     * Обязаны идти по возрастанию; за этим следит PlagueConfig.
     */
    public static int[] PLAYER_STAGE_THRESHOLDS = { 10, 30, 60, 90 };

    /**
     * Сколько очков заражённости даёт секунда в чанке уровня N.
     * Индекс — уровень чанка 0–4. Нулевой отрицательный: чистый воздух лечит.
     */
    public static float[] PLAYER_EXPOSURE = { -0.05f, 0.01f, 0.04f, 0.10f, 0.20f };

    /** Во сколько раз быстрее копится зараза под землёй. */
    public static float PLAYER_UNDERGROUND_MULTIPLIER = 1.35f;

    /**
     * Временный штраф к максимуму здоровья по стадиям 0–4, в HP.
     * Снимается вместе с лечением.
     */
    public static float[] PLAYER_STAGE_HEALTH = { 0f, 0f, 2f, 6f, 6f };

    /** Во сколько раз слабее сытит еда начиная со стадии 1. */
    public static float PLAYER_FOOD_MULTIPLIER = 0.5f;

    /** Раз во сколько тиков стадия 4 бьёт игрока. 3600 тиков — три минуты. */
    public static int PLAYER_STAGE4_DAMAGE_TICKS = 3600;

    /** Сколько HP снимает удар стадии 4. */
    public static float PLAYER_STAGE4_DAMAGE = 2f;

    /** Раз во сколько тиков кашляет игрок каждой стадии 0–4. Ноль — не кашляет. */
    public static int[] PLAYER_COUGH_TICKS = { 0, 600, 300, 200, 200 };

    /** Шанс заразить соседа одним кашлем, по стадии кашляющего 0–4. */
    public static float[] PLAYER_COUGH_CHANCE = { 0f, 0f, 0.35f, 0.50f, 0.50f };

    /** Радиус кашля в блоках. Шесть, а не два: больного нельзя вести с собой. */
    public static float PLAYER_COUGH_RADIUS = 6f;

    /** Сколько очков получает сосед, которому не повезло. */
    public static float PLAYER_COUGH_AMOUNT = 4f;

    /** Сколько очков снимает N-й глоток отвара подряд. Последнее — для всех дальнейших. */
    public static float[] PLAYER_BREW_STRENGTH = { 13f, 10f, 8f, 7f, 6f, 5f };

    /** Через сколько тиков без глотка счётчик отваров обнуляется. 6000 — пять минут. */
    public static int PLAYER_BREW_RESET_TICKS = 6000;

    /** Выше этой стадии отвар не действует. Стадии 3 и 4 — только Клирик. */
    public static int PLAYER_BREW_MAX_STAGE = 2;

    /** Сколько HP навсегда снимает смерть на стадии 2+. */
    public static float PLAYER_DEATH_PENALTY = 1f;

    /** Пол постоянных потерь: ниже этого максимум здоровья не опускают смерти. */
    public static float PLAYER_PERMANENT_FLOOR = 6f;

    /**
     * Жёсткий пол итогового максимума. Постоянные потери и временный штраф
     * стадии складываются; без этого предела они складываются в ноль,
     * и игрок умирает бесконечно при возрождении.
     */
    public static float PLAYER_HARD_FLOOR = 4f;
```

- [ ] **Step 2: Запустить тест проводки и убедиться, что он падает**

Из папки `plaguecore/`:

```
./gradlew test --tests '*PlagueConfigWiringTest*' --console=plain
```

Ожидается: FAIL со списком забытых ручек — `PLAYER_TICK_INTERVAL`,
`PLAYER_STAGE_THRESHOLDS` и остальные восемнадцать.

- [ ] **Step 3: Объявить поля конфига в `PlagueConfig.java`**

Дописать после блока `// ── животные ──`, перед `public static final ModConfigSpec SPEC;`:

```java
    // ── игрок ─────────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue ТИК_ИГРОКА;
    private static final ModConfigSpec.IntValue[] ПОРОГ_СТАДИИ =
        new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue[] ЭКСПОЗИЦИЯ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue ПОД_ЗЕМЛЁЙ;
    private static final ModConfigSpec.DoubleValue[] ЗДОРОВЬЕ_СТАДИИ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue ЕДА;
    private static final ModConfigSpec.IntValue УРОН_КАЖДЫЕ;
    private static final ModConfigSpec.DoubleValue УРОН_СТАДИИ_4;
    private static final ModConfigSpec.IntValue[] КАШЕЛЬ_КАЖДЫЕ =
        new ModConfigSpec.IntValue[5];
    private static final ModConfigSpec.DoubleValue[] ШАНС_КАШЛЯ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue РАДИУС_КАШЛЯ;
    private static final ModConfigSpec.DoubleValue ОЧКОВ_ЗА_КАШЕЛЬ;
    private static final ModConfigSpec.DoubleValue[] СИЛА_ОТВАРА =
        new ModConfigSpec.DoubleValue[6];
    private static final ModConfigSpec.IntValue СБРОС_ОТВАРА;
    private static final ModConfigSpec.IntValue ПОТОЛОК_ОТВАРА;
    private static final ModConfigSpec.DoubleValue ШТРАФ_СМЕРТИ;
    private static final ModConfigSpec.DoubleValue ПОЛ_ПОСТОЯННЫХ;
    private static final ModConfigSpec.DoubleValue ЖЁСТКИЙ_ПОЛ;
```

- [ ] **Step 4: Описать секцию `[player]` в статическом блоке**

Вставить в конец статического блока, **перед** строкой
`SPEC = СТРОИТЕЛЬ.pop().build();`, заменив её на код ниже:

```java
        СТРОИТЕЛЬ.pop().comment(
            "Чума в самом игроке: как копится, чем бьёт, чем лечится.",
            "Заражённость — число от 0 до 100. Стадия выводится из него."
        ).push("player");

        ТИК_ИГРОКА = СТРОИТЕЛЬ
            .comment("Раз во сколько тиков пересчитывается заражённость. 20 — раз в секунду.")
            .defineInRange("tickInterval", PlagueConstants.PLAYER_TICK_INTERVAL, 1, 200);

        for (int с = 0; с < 4; с++) {
            ПОРОГ_СТАДИИ[с] = СТРОИТЕЛЬ
                .comment("С какого числа очков начинается стадия " + (с + 1) + ".")
                .defineInRange("stage" + (с + 1) + "At",
                    PlagueConstants.PLAYER_STAGE_THRESHOLDS[с], 1, 100);
        }

        for (int у = 0; у < 5; у++) {
            ЭКСПОЗИЦИЯ[у] = СТРОИТЕЛЬ
                .comment("Очков заражённости за секунду в чанке уровня " + у + ".",
                    у == 0 ? "Отрицательное: чистый воздух лечит." : "")
                .defineInRange("exposureLevel" + у,
                    окр(PlagueConstants.PLAYER_EXPOSURE[у]), -5.0, 5.0);
        }

        ПОД_ЗЕМЛЁЙ = СТРОИТЕЛЬ
            .comment("Во сколько раз быстрее копится зараза под землёй.")
            .defineInRange("undergroundMultiplier",
                окр(PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER), 1.0, 5.0);

        for (int с = 0; с < 5; с++) {
            ЗДОРОВЬЕ_СТАДИИ[с] = СТРОИТЕЛЬ
                .comment("Сколько HP временно отнимает стадия " + с + ".")
                .defineInRange("stage" + с + "HealthPenalty",
                    окр(PlagueConstants.PLAYER_STAGE_HEALTH[с]), 0.0, 18.0);
        }

        ЕДА = СТРОИТЕЛЬ
            .comment("Во сколько раз слабее сытит еда у больного. 0.5 — вдвое.")
            .defineInRange("foodMultiplier",
                окр(PlagueConstants.PLAYER_FOOD_MULTIPLIER), 0.0, 1.0);

        УРОН_КАЖДЫЕ = СТРОИТЕЛЬ
            .comment("Раз во сколько тиков стадия 4 бьёт игрока. 3600 — три минуты.")
            .defineInRange("stage4DamageTicks",
                PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS, 20, 72000);

        УРОН_СТАДИИ_4 = СТРОИТЕЛЬ
            .comment("Сколько HP снимает удар стадии 4. 2.0 — одно сердце.")
            .defineInRange("stage4Damage",
                окр(PlagueConstants.PLAYER_STAGE4_DAMAGE), 0.0, 20.0);

        for (int с = 0; с < 5; с++) {
            КАШЕЛЬ_КАЖДЫЕ[с] = СТРОИТЕЛЬ
                .comment("Раз во сколько тиков кашляет игрок стадии " + с + ". Ноль — молчит.")
                .defineInRange("stage" + с + "CoughTicks",
                    PlagueConstants.PLAYER_COUGH_TICKS[с], 0, 72000);
            ШАНС_КАШЛЯ[с] = СТРОИТЕЛЬ
                .comment("Шанс заразить соседа одним кашлем на стадии " + с + ".")
                .defineInRange("stage" + с + "CoughChance",
                    окр(PlagueConstants.PLAYER_COUGH_CHANCE[с]), 0.0, 1.0);
        }

        РАДИУС_КАШЛЯ = СТРОИТЕЛЬ
            .comment("Радиус кашля в блоках. Шесть: больного нельзя вести с собой.")
            .defineInRange("coughRadius",
                окр(PlagueConstants.PLAYER_COUGH_RADIUS), 0.0, 64.0);

        ОЧКОВ_ЗА_КАШЕЛЬ = СТРОИТЕЛЬ
            .comment("Сколько очков получает сосед, которому не повезло.")
            .defineInRange("coughAmount",
                окр(PlagueConstants.PLAYER_COUGH_AMOUNT), 0.0, 100.0);

        for (int г = 0; г < 6; г++) {
            СИЛА_ОТВАРА[г] = СТРОИТЕЛЬ
                .comment(г < 5
                    ? "Сколько очков снимает " + (г + 1) + "-й глоток отвара подряд."
                    : "Сколько снимают шестой и все дальнейшие глотки подряд.")
                .defineInRange("brewStrength" + (г + 1),
                    окр(PlagueConstants.PLAYER_BREW_STRENGTH[г]), 0.0, 100.0);
        }

        СБРОС_ОТВАРА = СТРОИТЕЛЬ
            .comment("Через сколько тиков без глотка счётчик обнуляется. 6000 — пять минут.")
            .defineInRange("brewResetTicks",
                PlagueConstants.PLAYER_BREW_RESET_TICKS, 0, 72000);

        ПОТОЛОК_ОТВАРА = СТРОИТЕЛЬ
            .comment("Выше этой стадии отвар не действует. 2: лихорадку лечит только Клирик.")
            .defineInRange("brewMaxStage", PlagueConstants.PLAYER_BREW_MAX_STAGE, 0, 4);

        ШТРАФ_СМЕРТИ = СТРОИТЕЛЬ
            .comment("Сколько HP навсегда снимает смерть на стадии 2+. 1.0 — полсердца.")
            .defineInRange("deathPenalty",
                окр(PlagueConstants.PLAYER_DEATH_PENALTY), 0.0, 20.0);

        ПОЛ_ПОСТОЯННЫХ = СТРОИТЕЛЬ
            .comment("Ниже этого максимума здоровья смерти не опускают. 6.0 — три сердца.")
            .defineInRange("permanentFloor",
                окр(PlagueConstants.PLAYER_PERMANENT_FLOOR), 2.0, 20.0);

        ЖЁСТКИЙ_ПОЛ = СТРОИТЕЛЬ
            .comment("Итоговый максимум здоровья не опускается ниже этого никогда.",
                "Сторожит сложение постоянных потерь со штрафом стадии.")
            .defineInRange("hardFloor",
                окр(PlagueConstants.PLAYER_HARD_FLOOR), 1.0, 20.0);

        SPEC = СТРОИТЕЛЬ.pop().build();
```

- [ ] **Step 5: Дописать применение в метод `применить()`**

Вставить перед строкой `float[] доли = new float[...]`:

```java
        PlagueConstants.PLAYER_TICK_INTERVAL = ТИК_ИГРОКА.get();
        PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER = ПОД_ЗЕМЛЁЙ.get().floatValue();
        PlagueConstants.PLAYER_FOOD_MULTIPLIER = ЕДА.get().floatValue();
        PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS = УРОН_КАЖДЫЕ.get();
        PlagueConstants.PLAYER_STAGE4_DAMAGE = УРОН_СТАДИИ_4.get().floatValue();
        PlagueConstants.PLAYER_COUGH_RADIUS = РАДИУС_КАШЛЯ.get().floatValue();
        PlagueConstants.PLAYER_COUGH_AMOUNT = ОЧКОВ_ЗА_КАШЕЛЬ.get().floatValue();
        PlagueConstants.PLAYER_BREW_RESET_TICKS = СБРОС_ОТВАРА.get();
        PlagueConstants.PLAYER_BREW_MAX_STAGE = ПОТОЛОК_ОТВАРА.get();
        PlagueConstants.PLAYER_DEATH_PENALTY = ШТРАФ_СМЕРТИ.get().floatValue();
        PlagueConstants.PLAYER_PERMANENT_FLOOR = ПОЛ_ПОСТОЯННЫХ.get().floatValue();
        PlagueConstants.PLAYER_HARD_FLOOR = ЖЁСТКИЙ_ПОЛ.get().floatValue();

        // Пороги стадий обязаны идти по возрастанию, иначе стадия схлопнется
        // и следующая никогда не наступит. Выправляем молча, как с фазами.
        int[] пороги = new int[4];
        int минимум = 1;
        for (int с = 0; с < 4; с++) {
            пороги[с] = Math.max(минимум, ПОРОГ_СТАДИИ[с].get());
            if (пороги[с] != ПОРОГ_СТАДИИ[с].get()) поправлено = true;
            минимум = пороги[с] + 1;
        }
        PlagueConstants.PLAYER_STAGE_THRESHOLDS = пороги;

        float[] экспозиция = new float[5];
        float[] здоровьеСтадии = new float[5];
        int[] кашельКаждые = new int[5];
        float[] шансКашля = new float[5];
        for (int с = 0; с < 5; с++) {
            экспозиция[с] = ЭКСПОЗИЦИЯ[с].get().floatValue();
            здоровьеСтадии[с] = ЗДОРОВЬЕ_СТАДИИ[с].get().floatValue();
            кашельКаждые[с] = КАШЕЛЬ_КАЖДЫЕ[с].get();
            шансКашля[с] = ШАНС_КАШЛЯ[с].get().floatValue();
        }
        PlagueConstants.PLAYER_EXPOSURE = экспозиция;
        PlagueConstants.PLAYER_STAGE_HEALTH = здоровьеСтадии;
        PlagueConstants.PLAYER_COUGH_TICKS = кашельКаждые;
        PlagueConstants.PLAYER_COUGH_CHANCE = шансКашля;

        float[] силаОтвара = new float[6];
        for (int г = 0; г < 6; г++) силаОтвара[г] = СИЛА_ОТВАРА[г].get().floatValue();
        PlagueConstants.PLAYER_BREW_STRENGTH = силаОтвара;
```

Переменная `поправлено` объявляется ниже по коду (`boolean поправлено =
MaterializationMask.задатьДоли(...)`). Перенести её объявление выше
вставленного блока: заменить это место на `boolean поправлено = false;`
в начале метода, а строку про маску — на
`поправлено |= MaterializationMask.задатьДоли(доли);`.

- [ ] **Step 6: Запустить тест проводки и убедиться, что он проходит**

```
./gradlew test --tests '*PlagueConfigWiringTest*' --console=plain
```

Ожидается: PASS. Оба теста класса зелёные.

- [ ] **Step 7: Прогнать весь набор тестов**

```
./gradlew test --console=plain
```

Ожидается: PASS, 138 тестов как было.

- [ ] **Step 8: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConstants.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConfig.java
git commit -m "Конфиг: секция [player], девятнадцать ручек подсистемы 2"
git push origin main
```

---

### Task 2: InfectionMath — чистая математика

Всё, что можно посчитать без Minecraft, считается здесь и проверяется
обычным JUnit. Класс лежит в `core` и не имеет права импортировать
Minecraft.

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/InfectionMath.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/InfectionMathTest.java`

**Interfaces:**
- Consumes: `PlagueConstants.PLAYER_*` из задачи 1
- Produces:
  - `static int стадия(float заражённость)` → 0–4
  - `static float экспозиция(int уровеньЧанка, boolean подЗемлёй, float защита)` → очков за секунду
  - `static float следующая(float заражённость, float экспозицияЗаСекунду)` → новое значение с зажимом и потолком восстановления
  - `static float потолокВосстановления()` → очки, ниже которых воздух не лечит
  - `static float силаОтвара(int глотковПодряд)` → сколько снимет следующий глоток
  - `static int счётчикГлотков(int былоГлотков, long тикПоследнего, long сейчас)` → номер следующего глотка
  - `static float постоянныйШтраф(int смертей)` → HP
  - `static float временныйШтраф(int стадия, float постоянныйШтраф)` → HP
  - `static final float БАЗА_ЗДОРОВЬЯ = 20f`

- [ ] **Step 1: Написать падающий тест**

Создать `plaguecore/src/test/java/dev/denthe/plaguecore/core/InfectionMathTest.java`:

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Математика заражения игрока. Спек подсистемы 2, разделы 2, 3, 5, 6.
 *
 * Числа берутся из PlagueConstants, а не вписываются в тест руками:
 * иначе правка конфига ломала бы тесты, а она обязана быть свободной.
 */
class InfectionMathTest {

    @Test
    void стадияВыводитсяИзОчков() {
        assertEquals(0, InfectionMath.стадия(0f));
        assertEquals(0, InfectionMath.стадия(9.99f));
        assertEquals(1, InfectionMath.стадия(10f));
        assertEquals(1, InfectionMath.стадия(29.99f));
        assertEquals(2, InfectionMath.стадия(30f));
        assertEquals(2, InfectionMath.стадия(59.99f));
        assertEquals(3, InfectionMath.стадия(60f));
        assertEquals(3, InfectionMath.стадия(89.99f));
        assertEquals(4, InfectionMath.стадия(90f));
        assertEquals(4, InfectionMath.стадия(100f));
    }

    @Test
    void подЗемлёйЗаражениеБыстрее() {
        float сверху = InfectionMath.экспозиция(4, false, 0f);
        float снизу = InfectionMath.экспозиция(4, true, 0f);
        assertTrue(снизу > сверху, "под землёй должно быть быстрее");
        assertEquals(сверху * PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER, снизу, 1e-4);
    }

    @Test
    void защитаЗамедляетНоНеЛечит() {
        float безЗащиты = InfectionMath.экспозиция(3, false, 0f);
        float вБроне = InfectionMath.экспозиция(3, false, 0.5f);
        assertEquals(безЗащиты / 2f, вБроне, 1e-4);
        // Полная защита обнуляет набор, но не превращает его в лечение.
        assertEquals(0f, InfectionMath.экспозиция(3, false, 1f), 1e-4);
    }

    @Test
    void чистыйВоздухНеУмножаетсяНаЗащиту() {
        // Броня не мешает выздоравливать: защита действует только на набор.
        assertEquals(InfectionMath.экспозиция(0, false, 0f),
                     InfectionMath.экспозиция(0, false, 0.9f), 1e-4);
    }

    @Test
    void воздухЛечитТолькоДоПотолка() {
        float потолок = InfectionMath.потолокВосстановления();
        assertEquals(30f, потолок, 1e-4);

        // Выше потолка чистый воздух не работает вовсе.
        assertEquals(59f, InfectionMath.следующая(59f, -0.05f), 1e-4);
        assertEquals(30.5f, InfectionMath.следующая(30.5f, -0.05f), 1e-4);

        // На потолке и ниже — лечит.
        assertEquals(29.95f, InfectionMath.следующая(30f, -0.05f), 1e-4);
        assertEquals(9.95f, InfectionMath.следующая(10f, -0.05f), 1e-4);
    }

    @Test
    void набирающаяЭкспозицияПотолкомНеОграничена() {
        // Потолок стережёт только выздоровление. Заражаться можно до сотни.
        assertEquals(59.2f, InfectionMath.следующая(59f, 0.20f), 1e-4);
    }

    @Test
    void заражённостьЗажатаОтНуляДоСотни() {
        assertEquals(0f, InfectionMath.следующая(0.01f, -0.05f), 1e-4);
        assertEquals(100f, InfectionMath.следующая(99.99f, 0.20f), 1e-4);
    }

    @Test
    void силаОтвараПадаетПоТаблице() {
        assertEquals(13f, InfectionMath.силаОтвара(0), 1e-4);
        assertEquals(10f, InfectionMath.силаОтвара(1), 1e-4);
        assertEquals(8f, InfectionMath.силаОтвара(2), 1e-4);
        assertEquals(7f, InfectionMath.силаОтвара(3), 1e-4);
        assertEquals(6f, InfectionMath.силаОтвара(4), 1e-4);
        assertEquals(5f, InfectionMath.силаОтвара(5), 1e-4);
        // Дальше таблицы сила не падает: держится последнее значение.
        assertEquals(5f, InfectionMath.силаОтвара(6), 1e-4);
        assertEquals(5f, InfectionMath.силаОтвара(99), 1e-4);
    }

    @Test
    void счётчикГлотковРастётПодрядИСбрасываетсяПаузой() {
        int сброс = PlagueConstants.PLAYER_BREW_RESET_TICKS;

        // Первый глоток в жизни.
        assertEquals(0, InfectionMath.счётчикГлотков(0, -1L, 1000L));

        // Второй сразу за первым.
        assertEquals(1, InfectionMath.счётчикГлотков(1, 1000L, 1100L));

        // Ровно на границе окна счётчик ещё живёт.
        assertEquals(3, InfectionMath.счётчикГлотков(3, 1000L, 1000L + сброс));

        // Через тик после границы — обнуление.
        assertEquals(0, InfectionMath.счётчикГлотков(3, 1000L, 1001L + сброс));
    }

    @Test
    void сПотолкаВторойСтадииТриОтвараДоводятДоПервой() {
        // Прикидка из спека: 59 → 46 → 36 → 28.
        float очки = 59f;
        очки -= InfectionMath.силаОтвара(0);
        assertEquals(46f, очки, 1e-4);
        очки -= InfectionMath.силаОтвара(1);
        assertEquals(36f, очки, 1e-4);
        очки -= InfectionMath.силаОтвара(2);
        assertEquals(28f, очки, 1e-4);
        assertEquals(1, InfectionMath.стадия(очки));
    }

    @Test
    void постоянныйШтрафНакапливаетсяДоПола() {
        assertEquals(0f, InfectionMath.постоянныйШтраф(0), 1e-4);
        assertEquals(1f, InfectionMath.постоянныйШтраф(1), 1e-4);
        assertEquals(7f, InfectionMath.постоянныйШтраф(7), 1e-4);
        // Пол 6 HP: больше 14 HP смерти не отнимают никогда.
        assertEquals(14f, InfectionMath.постоянныйШтраф(14), 1e-4);
        assertEquals(14f, InfectionMath.постоянныйШтраф(99), 1e-4);
    }

    @Test
    void временныйШтрафНеДоводитЗдоровьеДоНуля() {
        // Здоровый: штрафа нет.
        assertEquals(0f, InfectionMath.временныйШтраф(0, 0f), 1e-4);

        // Обычный больной: полный штраф стадии.
        assertEquals(2f, InfectionMath.временныйШтраф(2, 0f), 1e-4);
        assertEquals(6f, InfectionMath.временныйШтраф(3, 0f), 1e-4);

        // Четырнадцать HP уже потеряно навсегда: осталось 6.
        // Штраф стадии 3 (−6) обнулил бы игрока, поэтому его режут до 2.
        assertEquals(2f, InfectionMath.временныйШтраф(3, 14f), 1e-4);

        // Итог никогда не ниже жёсткого пола.
        float постоянный = InfectionMath.постоянныйШтраф(99);
        float временный = InfectionMath.временныйШтраф(4, постоянный);
        assertTrue(InfectionMath.БАЗА_ЗДОРОВЬЯ - постоянный - временный
                   >= PlagueConstants.PLAYER_HARD_FLOOR - 1e-4);
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

```
./gradlew test --tests '*InfectionMathTest*' --console=plain
```

Ожидается: провал компиляции — класс `InfectionMath` не существует.

- [ ] **Step 3: Создать `InfectionMath.java`**

Создать `plaguecore/src/main/java/dev/denthe/plaguecore/core/InfectionMath.java`:

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

/**
 * Математика заражения игрока. Спек подсистемы 2,
 * `docs/superpowers/specs/2026-09-04-zarazhenie-igroka-design.md`.
 *
 * Как и остальной core, класс не знает о Minecraft: сюда приходят числа,
 * отсюда уходят числа. Всё, что связано с атрибутами, эффектами и
 * звуками, живёт в пакете mc.
 */
public final class InfectionMath {
    private InfectionMath() {}

    /**
     * База максимума здоровья в ваниле — двадцать HP.
     *
     * Финальная, а не ручка: если другой мод поменяет базу игрока, пол
     * здоровья поедет, но на нашем сервере такого мода нет, а лишняя
     * ручка в конфиге стоит дороже, чем эта строчка комментария.
     */
    public static final float БАЗА_ЗДОРОВЬЯ = 20f;

    /** Стадия 0–4 по накопленной заражённости. */
    public static int стадия(float заражённость) {
        int[] пороги = PlagueConstants.PLAYER_STAGE_THRESHOLDS;
        int с = 0;
        while (с < пороги.length && заражённость >= пороги[с]) с++;
        return с;
    }

    /**
     * Очки заражённости за секунду в чанке такого уровня.
     *
     * Защита действует только на набор: в броне зараза копится медленнее,
     * но выздоровление на чистом воздухе она не ускоряет и не замедляет.
     * Иначе снаряжение вышло бы на две оси сразу, а мы решили масштабировать
     * его ровно по одной — по времени, которое можно провести в Гнили.
     *
     * @param уровеньЧанка 0–4, значения за границами зажимаются
     * @param подЗемлёй    игрок ниже поверхности
     * @param защита       0..1, доля погашенной экспозиции
     */
    public static float экспозиция(int уровеньЧанка, boolean подЗемлёй, float защита) {
        float[] таблица = PlagueConstants.PLAYER_EXPOSURE;
        int у = уровеньЧанка < 0 ? 0 : Math.min(уровеньЧанка, таблица.length - 1);
        float ставка = таблица[у];
        if (ставка <= 0f) return ставка;                    // чистый воздух, защита ни при чём
        if (подЗемлёй) ставка *= PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER;
        return ставка * (1f - зажать(защита, 0f, 1f));
    }

    /**
     * Очки, ниже которых чистый воздух перестаёт лечить.
     *
     * Выводится из порога второй стадии, а не заводится отдельной ручкой:
     * это одно и то же число по смыслу — «сам вылечиться можно только
     * со стадии 1», — и разъехаться они не должны.
     */
    public static float потолокВосстановления() {
        return PlagueConstants.PLAYER_STAGE_THRESHOLDS[1];
    }

    /**
     * Новая заражённость через секунду.
     *
     * Набор идёт всегда, выздоровление — только пока игрок ниже потолка.
     * Отсюда и берётся правило спека: со стадии 1 можно отлежаться,
     * со стадии 2 — уже нет, там нужен отвар.
     */
    public static float следующая(float заражённость, float экспозицияЗаСекунду) {
        if (экспозицияЗаСекунду < 0f && заражённость > потолокВосстановления()) {
            return заражённость;
        }
        return зажать(заражённость + экспозицияЗаСекунду, 0f, 100f);
    }

    /**
     * Сколько очков снимет глоток с таким номером. Ноль — первый глоток.
     * За концом таблицы сила не падает дальше: держится последнее значение.
     */
    public static float силаОтвара(int глотковПодряд) {
        float[] таблица = PlagueConstants.PLAYER_BREW_STRENGTH;
        if (глотковПодряд < 0) return таблица[0];
        return таблица[Math.min(глотковПодряд, таблица.length - 1)];
    }

    /**
     * Номер следующего глотка. Пауза дольше окна сбрасывает счётчик в ноль,
     * и отвар снова работает в полную силу.
     *
     * Окно отсчитывается от последнего глотка, а не от первого: пить
     * маленькими порциями подряд бессмысленно, а вернуться через пять
     * минут — можно.
     */
    public static int счётчикГлотков(int былоГлотков, long тикПоследнего, long сейчас) {
        if (тикПоследнего < 0) return 0;
        if (сейчас - тикПоследнего > PlagueConstants.PLAYER_BREW_RESET_TICKS) return 0;
        return Math.max(0, былоГлотков);
    }

    /**
     * Постоянная потеря максимума здоровья за смерти на стадии 2+.
     * Ограничена снизу полом: невезучий игрок не должен выпасть из сессии.
     */
    public static float постоянныйШтраф(int смертей) {
        float сырой = PlagueConstants.PLAYER_DEATH_PENALTY * Math.max(0, смертей);
        float максимум = БАЗА_ЗДОРОВЬЯ - PlagueConstants.PLAYER_PERMANENT_FLOOR;
        return Math.min(сырой, Math.max(0f, максимум));
    }

    /**
     * Временный штраф стадии, урезанный так, чтобы вместе с постоянным
     * он не свёл максимум здоровья к нулю.
     *
     * Без этого игрок с максимумом 6 HP, поймавший лихорадку (−6),
     * получил бы ноль и умирал бы бесконечно сразу после возрождения.
     */
    public static float временныйШтраф(int стадия, float постоянныйШтраф) {
        float[] таблица = PlagueConstants.PLAYER_STAGE_HEALTH;
        int с = стадия < 0 ? 0 : Math.min(стадия, таблица.length - 1);
        float запас = БАЗА_ЗДОРОВЬЯ - постоянныйШтраф - PlagueConstants.PLAYER_HARD_FLOOR;
        return зажать(таблица[с], 0f, Math.max(0f, запас));
    }

    private static float зажать(float значение, float низ, float верх) {
        return значение < низ ? низ : (значение > верх ? верх : значение);
    }
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

```
./gradlew test --tests '*InfectionMathTest*' --console=plain
```

Ожидается: PASS, 12 тестов.

- [ ] **Step 5: Прогнать весь набор, включая тест чистоты**

```
./gradlew test --console=plain
```

Ожидается: PASS. `CorePurityTest` обязан быть зелёным — в `InfectionMath`
нет ни одного импорта Minecraft.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/core/InfectionMath.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/InfectionMathTest.java
git commit -m "InfectionMath: стадии, экспозиция, потолок восстановления, отвар"
git push origin main
```

---

### Task 3: Данные игрока и накопление

Первая задача, которую видно в игре: постоял в Гнили — число выросло.

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerPlagueData.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java`

**Interfaces:**
- Consumes: `InfectionMath.стадия`, `InfectionMath.экспозиция`,
  `InfectionMath.следующая` (задача 2); `PlagueState.get(level).grid()`
  и `PlagueGrid.getLevel(cx, cz)` (уже есть)
- Produces:
  - `PlayerPlagueData` — mutable-класс с полями `заражённость`, `стадия`,
    `иммунитетДо`, `смертей`, `глотков`, `тикПоследнегоГлотка`
  - `PlayerPlagueData.ВЛОЖЕНИЯ` — `DeferredRegister<AttachmentType<?>>`
  - `PlayerPlagueData.ЧУМА` — `Supplier<AttachmentType<PlayerPlagueData>>`
  - `static PlayerPlagueData данные(Player)` — достать вложение
  - `PlayerInfection.задать(ServerPlayer, float)` — выставить заражённость и синхронизировать

- [ ] **Step 1: Создать `PlayerPlagueData.java`**

```java
package dev.denthe.plaguecore.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Чума в отдельно взятом игроке. Спек подсистемы 2, раздел 8.
 *
 * Хранится ванильным Data Attachment: NeoForge сам кладёт это в файл
 * игрока и сам достаёт при входе. Своего кода сохранения не пишем.
 *
 * Вложение помечено copyOnDeath: смерть не лечит. Иначе самоубийство
 * стало бы самым дешёвым лекарством и вся подсистема потеряла бы смысл.
 */
public class PlayerPlagueData {

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PlagueCore.MODID);

    /** Накопленная заражённость, 0–100. */
    public float заражённость;

    /** Стадия 0–4. Производная от заражённости, держится кэшем для сравнения. */
    public int стадия;

    /** Игровой тик, до которого действует иммунитет. */
    public long иммунитетДо;

    /** Смертей на стадии 2+ за сессию. */
    public int смертей;

    /** Глотков отвара подряд. */
    public int глотков;

    /** Тик последнего глотка. −1 — не пил ни разу. */
    public long тикПоследнегоГлотка = -1L;

    public PlayerPlagueData() {}

    public PlayerPlagueData(float заражённость, int стадия, long иммунитетДо,
                            int смертей, int глотков, long тикПоследнегоГлотка) {
        this.заражённость = заражённость;
        this.стадия = стадия;
        this.иммунитетДо = иммунитетДо;
        this.смертей = смертей;
        this.глотков = глотков;
        this.тикПоследнегоГлотка = тикПоследнегоГлотка;
    }

    public static final Codec<PlayerPlagueData> CODEC = RecordCodecBuilder.create(и -> и.group(
        Codec.FLOAT.fieldOf("infection").forGetter(д -> д.заражённость),
        Codec.INT.fieldOf("stage").forGetter(д -> д.стадия),
        Codec.LONG.fieldOf("immunityUntil").forGetter(д -> д.иммунитетДо),
        Codec.INT.fieldOf("plagueDeaths").forGetter(д -> д.смертей),
        Codec.INT.fieldOf("brewCount").forGetter(д -> д.глотков),
        Codec.LONG.fieldOf("lastBrewTick").forGetter(д -> д.тикПоследнегоГлотка)
    ).apply(и, PlayerPlagueData::new));

    public static final Supplier<AttachmentType<PlayerPlagueData>> ЧУМА =
        ВЛОЖЕНИЯ.register("player_plague", () -> AttachmentType
            .builder(PlayerPlagueData::new)
            .serialize(CODEC)
            .copyOnDeath()
            .build());

    /** Данные игрока. Вложение создаётся само при первом обращении. */
    public static PlayerPlagueData данные(Player игрок) {
        return игрок.getData(ЧУМА.get());
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
```

- [ ] **Step 2: Подключить регистрацию в `PlagueCore.java`**

Заменить конструктор на:

```java
    public PlagueCore(IEventBus modEventBus, ModContainer container) {
        dev.denthe.plaguecore.mc.PlagueBlocks.register(modEventBus);
        dev.denthe.plaguecore.mc.PlagueEntities.register(modEventBus);
        dev.denthe.plaguecore.mc.PlayerPlagueData.register(modEventBus);
        PlagueConfig.зарегистрировать(modEventBus, container);
        LOG.info("Plague Core загружается");
    }
```

- [ ] **Step 3: Создать `PlayerInfection.java` с одним только накоплением**

Эффекты стадий добавятся задачей 4 — сейчас нужен голый счётчик.

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.InfectionMath;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Тик игрока: сколько чумы он набрал и что с ним от этого происходит.
 *
 * Считается раз в секунду, а не каждый тик: восемь игроков против сотен
 * чанков — работа копеечная, но и её нет смысла делать двадцать раз
 * в секунду, когда числа в спеке заданы «за секунду».
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlayerInfection {
    private PlayerInfection() {}

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (!(игрок.level() instanceof ServerLevel мир)) return;
        // Сетка чумы живёт только в верхнем мире.
        if (мир.dimension() != Level.OVERWORLD) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);

        float экспозиция = экспозицияДля(игрок, мир, д);
        д.заражённость = InfectionMath.следующая(д.заражённость, экспозиция);
        д.стадия = InfectionMath.стадия(д.заражённость);
    }

    /** Сколько очков в секунду набирает или теряет игрок там, где стоит. */
    private static float экспозицияДля(ServerPlayer игрок, ServerLevel мир, PlayerPlagueData д) {
        if (мир.getGameTime() < д.иммунитетДо) return 0f;

        PlagueGrid сетка = PlagueState.get(мир).grid();
        int cx = SectionPos.blockToSectionCoord(игрок.getBlockX());
        int cz = SectionPos.blockToSectionCoord(игрок.getBlockZ());
        if (!сетка.contains(cx, cz)) return 0f;

        int уровень = сетка.getLevel(cx, cz);
        boolean подЗемлёй = !мир.canSeeSky(игрок.blockPosition());
        return InfectionMath.экспозиция(уровень, подЗемлёй, защита(игрок));
    }

    /**
     * Доля погашенной экспозиции, 0..1.
     *
     * Пока считается только по броне: очко брони гасит один процент.
     * Полный алмаз (20 очков) — пятая часть. Слот Curios и эффекты Клирика
     * подключатся в подсистеме классов, здесь для них оставлено место.
     *
     * ponytail: линейная прикидка от брони; заменить настоящей формулой,
     * когда появятся маски и зелья Клирика
     */
    public static float защита(ServerPlayer игрок) {
        return Math.min(0.9f, игрок.getArmorValue() * 0.01f);
    }

    /** Выставить заражённость снаружи: команда, отвар, лекарство Клирика. */
    public static void задать(ServerPlayer игрок, float значение) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.заражённость = Math.max(0f, Math.min(100f, значение));
        д.стадия = InfectionMath.стадия(д.заражённость);
    }
}
```

- [ ] **Step 4: Добавить команду `/plague player` в `PlagueCommands.java`**

Дописать импорты:

```java
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
```

Дописать в метод `зарегистрировать`, перед `event.getDispatcher().register(корень);`:

```java
        корень.then(Commands.literal("player")
            .then(Commands.argument("who", EntityArgument.player())
                .executes(PlagueCommands::показатьИгрока)
                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                    .executes(PlagueCommands::выставитьИгроку))));
```

Дописать два метода в конец класса:

```java
    private static int показатьИгрока(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        PlayerPlagueData д = PlayerPlagueData.данные(кто);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
            "%s: заражённость %.1f, стадия %d, смертей от чумы %d",
            кто.getGameProfile().getName(), д.заражённость, д.стадия, д.смертей)), false);
        return 1;
    }

    private static int выставитьИгроку(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer кто = EntityArgument.getPlayer(c, "who");
        float значение = FloatArgumentType.getFloat(c, "value");
        PlayerInfection.задать(кто, значение);
        PlayerPlagueData д = PlayerPlagueData.данные(кто);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
            "%s: заражённость %.1f, стадия %d",
            кто.getGameProfile().getName(), д.заражённость, д.стадия)), true);
        return 1;
    }
```

- [ ] **Step 5: Собрать и прогнать тесты**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL, 150 тестов.

- [ ] **Step 6: Проверить в игре**

```
./gradlew runServer --console=plain
```

В игре:

```
/plague setlevel ~ ~ 4
/plague player <ник>
```

Постоять в чанке уровня 4 минуту, снова `/plague player <ник>` — число
должно вырасти примерно на 12. Отойти в чистый чанк, подождать — число
должно падать, но остановиться на 30, если было выше.

Проверить `/plague player <ник> 65` — стадия должна стать 3.

- [ ] **Step 7: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerPlagueData.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java
git commit -m "Заражённость игрока копится и хранится, команда /plague player"
git push origin main
```

---

### Task 4: Эффекты стадий

Здоровье, еда, регенерация, урон стадии 4. Здесь чума впервые кусается.

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java`

**Interfaces:**
- Consumes: `InfectionMath.постоянныйШтраф`, `InfectionMath.временныйШтраф`
  (задача 2); `PlayerPlagueData.данные` (задача 3)
- Produces: `PlayerInfection.пересчитатьЗдоровье(ServerPlayer)` — вызывается
  задачей 7 после смерти

- [ ] **Step 1: Дописать импорты в `PlayerInfection.java`**

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
```

- [ ] **Step 2: Добавить пересчёт здоровья**

Дописать в класс:

```java
    /** Временный штраф стадии. Снимается вместе с лечением. */
    private static final ResourceLocation ШТРАФ_СТАДИИ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "stage_penalty");

    /** Постоянная потеря за смерти на стадии 2+. Переживает возрождение. */
    private static final ResourceLocation ШТРАФ_СМЕРТЕЙ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "death_penalty");

    /**
     * Привести максимум здоровья в соответствие со стадией и смертями.
     *
     * Оба штрафа считаются вместе, а не по отдельности: временный урезается
     * так, чтобы вместе с постоянным не свести максимум к нулю. Логика
     * зажима живёт в InfectionMath и проверяется тестами.
     */
    public static void пересчитатьЗдоровье(ServerPlayer игрок) {
        AttributeInstance атрибут = игрок.getAttribute(Attributes.MAX_HEALTH);
        if (атрибут == null) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        float постоянный = InfectionMath.постоянныйШтраф(д.смертей);
        float временный = InfectionMath.временныйШтраф(д.стадия, постоянный);

        атрибут.removeModifier(ШТРАФ_СМЕРТЕЙ);
        атрибут.removeModifier(ШТРАФ_СТАДИИ);

        if (постоянный > 0f) {
            атрибут.addOrReplacePermanentModifier(new AttributeModifier(
                ШТРАФ_СМЕРТЕЙ, -постоянный, AttributeModifier.Operation.ADD_VALUE));
        }
        if (временный > 0f) {
            атрибут.addOrUpdateTransientModifier(new AttributeModifier(
                ШТРАФ_СТАДИИ, -временный, AttributeModifier.Operation.ADD_VALUE));
        }

        // Максимум мог упасть ниже текущего здоровья — подрезаем, иначе
        // в интерфейсе останутся сердца, которых уже нет.
        if (игрок.getHealth() > игрок.getMaxHealth()) {
            игрок.setHealth(игрок.getMaxHealth());
        }
    }
```

- [ ] **Step 3: Вызывать пересчёт при смене стадии и добавить урон стадии 4**

Заменить тело `приТике` на:

```java
    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (!(игрок.level() instanceof ServerLevel мир)) return;
        if (мир.dimension() != Level.OVERWORLD) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        int былаСтадия = д.стадия;

        float экспозиция = экспозицияДля(игрок, мир, д);
        д.заражённость = InfectionMath.следующая(д.заражённость, экспозиция);
        д.стадия = InfectionMath.стадия(д.заражённость);

        if (д.стадия != былаСтадия) {
            пересчитатьЗдоровье(игрок);
        }

        // Обращение: чума добивает сама. Период отсчитывается от общего
        // времени мира, а не от личного счётчика, чтобы вход и выход
        // из игры не сбрасывали таймер.
        if (д.стадия >= 4 && PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS > 0
                && мир.getGameTime() % PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS
                   < PlagueConstants.PLAYER_TICK_INTERVAL) {
            игрок.hurt(игрок.damageSources().source(DamageTypes.WITHER),
                PlagueConstants.PLAYER_STAGE4_DAMAGE);
        }
    }
```

- [ ] **Step 4: Отключить регенерацию на стадии 4**

Дописать в класс:

```java
    /**
     * Обращение не лечится ничем.
     *
     * Ловим общее событие лечения, а не сытость отдельно: так одним
     * условием отсекаются и регенерация от еды, и зелья, и золотые
     * яблоки, и всё, что принесут другие моды. Единственным выходом
     * остаётся Клирик — ровно как задумано спеком.
     */
    @SubscribeEvent
    public static void приЛечении(LivingHealEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerPlagueData.данные(игрок).стадия >= 4) событие.setCanceled(true);
    }
```

- [ ] **Step 5: Ослабить еду начиная со стадии 1**

Дописать в класс:

```java
    /**
     * Больного еда сытит хуже.
     *
     * Считаем по свойствам съеденного предмета и отнимаем разницу сразу
     * после того, как ваниль её начислила. Так работает с любой едой,
     * включая чужую модовую, — своего списка продуктов держать не нужно.
     */
    @SubscribeEvent
    public static void послеЕды(LivingEntityUseItemEvent.Finish событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerPlagueData.данные(игрок).стадия < 1) return;

        ItemStack съеденное = событие.getItem();
        FoodProperties свойства = съеденное.getFoodProperties(игрок);
        if (свойства == null) return;

        float доля = 1f - Math.max(0f, Math.min(1f, PlagueConstants.PLAYER_FOOD_MULTIPLIER));
        if (доля <= 0f) return;

        FoodData сытость = игрок.getFoodData();
        int отнятьЕды = Math.round(свойства.nutrition() * доля);
        float отнятьНасыщения = свойства.saturation() * доля;

        сытость.setFoodLevel(Math.max(0, сытость.getFoodLevel() - отнятьЕды));
        сытость.setSaturation(Math.max(0f, сытость.getSaturationLevel() - отнятьНасыщения));
    }
```

Если компилятор не знает `свойства.saturation()`, в этой версии поле
называется `saturationModifier()` — тогда умножить его на `nutrition()`,
как это делает ваниль.

- [ ] **Step 6: Пересчитывать здоровье и при ручной выставке**

Заменить `PlayerInfection.задать` на:

```java
    /** Выставить заражённость снаружи: команда, отвар, лекарство Клирика. */
    public static void задать(ServerPlayer игрок, float значение) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.заражённость = Math.max(0f, Math.min(100f, значение));
        д.стадия = InfectionMath.стадия(д.заражённость);
        пересчитатьЗдоровье(игрок);
    }
```

Без этой строки `/plague player` меняет стадию, а сердца остаются
старыми — и вся проверка задачи выглядит сломанной.

- [ ] **Step 7: Собрать**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL.

- [ ] **Step 8: Проверить в игре**

```
./gradlew runServer --console=plain
```

```
/plague player <ник> 35
```

Смотреть: пропало одно сердце. Съесть хлеб — сытость выросла примерно
вдвое слабее обычного.

```
/plague player <ник> 65
```

Три сердца долой. Здоровье не должно скакнуть выше нового максимума.

```
/plague player <ник> 95
```

Раз в три минуты снимается сердце. Регенерация не идёт, даже с полной
шкалой голода и золотым яблоком.

```
/plague player <ник> 0
```

Все сердца вернулись.

- [ ] **Step 9: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java
git commit -m "Эффекты стадий: здоровье, еда, регенерация, урон обращения"
git push origin main
```

---

### Task 5: Кашель и передача

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCough.java`

**Interfaces:**
- Consumes: `PlayerPlagueData.данные` (задача 3),
  `PlayerInfection.пересчитатьЗдоровье` (задача 4)
- Produces: ничего наружу — самодостаточный обработчик события

- [ ] **Step 1: Создать `PlagueCough.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Кашель: главная примета болезни и единственный способ поймать её
 * от человека. Спек подсистемы 2, раздел 4.
 *
 * Кашель — настоящее событие со звуком и частицами, а не строчка
 * в интерфейсе. Так стадию видно и слышно без всякого HUD: услышал
 * рядом — отошёл.
 *
 * Радиус шесть блоков, а не два. При двух достаточно отойти на три шага,
 * и болезнь становится личной проблемой каждого. При шести больного
 * нельзя просто взять с собой — его либо лечат, либо оставляют.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueCough {
    private PlagueCough() {}

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer больной)) return;
        if (!(больной.level() instanceof ServerLevel мир)) return;
        if (больной.isCreative() || больной.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(больной);
        int стадия = Math.max(0, Math.min(д.стадия, PlagueConstants.PLAYER_COUGH_TICKS.length - 1));
        int период = PlagueConstants.PLAYER_COUGH_TICKS[стадия];
        if (период <= 0) return;
        if (больной.tickCount % период != 0) return;

        кашлянуть(мир, больной);
        заразитьРядом(мир, больной, PlagueConstants.PLAYER_COUGH_CHANCE[стадия]);
    }

    /**
     * Звук и частицы.
     *
     * ponytail: звук ванильный, с пониженным тоном — своего сэмпла кашля
     * у нас пока нет. Заменить на свой, когда владелец запишет.
     */
    private static void кашлянуть(ServerLevel мир, ServerPlayer больной) {
        мир.playSound(null, больной.getX(), больной.getY(), больной.getZ(),
            SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.6f);
        мир.sendParticles(ParticleTypes.SNEEZE,
            больной.getX(), больной.getEyeY() - 0.1, больной.getZ(),
            12, 0.25, 0.15, 0.25, 0.02);
    }

    /** Каждый в радиусе бросает кубик. Сам больной, понятно, не считается. */
    private static void заразитьРядом(ServerLevel мир, ServerPlayer больной, float шанс) {
        if (шанс <= 0f) return;

        double r = PlagueConstants.PLAYER_COUGH_RADIUS;
        AABB область = больной.getBoundingBox().inflate(r);
        List<Player> рядом = мир.getEntitiesOfClass(Player.class, область,
            п -> п != больной && !п.isCreative() && !п.isSpectator()
                 && п.distanceToSqr(больной) <= r * r);

        for (Player сосед : рядом) {
            if (!(сосед instanceof ServerPlayer жертва)) continue;
            PlayerPlagueData дж = PlayerPlagueData.данные(жертва);
            if (мир.getGameTime() < дж.иммунитетДо) continue;
            if (мир.random.nextFloat() >= шанс) continue;

            int была = дж.стадия;
            дж.заражённость = Math.min(100f,
                дж.заражённость + PlagueConstants.PLAYER_COUGH_AMOUNT);
            дж.стадия = InfectionMath.стадия(дж.заражённость);
            if (дж.стадия != была) PlayerInfection.пересчитатьЗдоровье(жертва);
        }
    }
}
```

- [ ] **Step 2: Собрать**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL.

- [ ] **Step 3: Проверить в игре**

Проверка одиночная — на слух и глаз:

```
/plague player <ник> 35
```

Раз в 15 секунд должен раздаваться приглушённый вдох и вылетать облачко
частиц. На 65 очках — раз в 10 секунд.

Передачу вдвоём проверить некому, поэтому проверяем логику через второго
игрока на том же сервере, если он есть; если нет — достаточно того, что
кашель работает, а передача читается по коду.

- [ ] **Step 4: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCough.java
git commit -m "Кашель со звуком и частицами, передача чумы в радиусе шести блоков"
git push origin main
```

---

### Task 6: Отвар

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/BrewItem.java`
- Create: `plaguecore/src/main/resources/data/plaguecore/recipe/plague_brew.json`
- Create: `plaguecore/src/main/resources/assets/plaguecore/models/item/plague_brew.json`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueBlocks.java`
- Modify: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json` и `en_us.json`

**Interfaces:**
- Consumes: `InfectionMath.силаОтвара`, `InfectionMath.счётчикГлотков`
  (задача 2); `PlayerInfection.пересчитатьЗдоровье` (задача 4)
- Produces: `PlagueBlocks.PLAGUE_BREW` — `DeferredItem<Item>`

- [ ] **Step 1: Создать `BrewItem.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Отвар: лекарство от лёгкой чумы, доступное каждому.
 *
 * Сила падает при частом питье и восстанавливается за пять минут паузы.
 * Смысл в том, чтобы отвар был расходником на вылазку, а не кнопкой
 * «выздороветь»: три глотка подряд вытаскивают с потолка второй стадии,
 * а десять подряд не дают почти ничего.
 *
 * На стадиях 3 и 4 не работает вовсе. Иначе стопка отваров заменила бы
 * Клирика, и весь класс превратился бы в украшение.
 */
public class BrewItem extends Item {

    public BrewItem(Properties свойства) {
        super(свойства);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack стопка) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack стопка, LivingEntity кто) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level мир, Player игрок, InteractionHand рука) {
        return ItemUtils.startUsingInstantly(мир, игрок, рука);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack стопка, Level мир, LivingEntity кто) {
        if (кто instanceof ServerPlayer игрок) выпить(игрок, мир);

        if (кто instanceof Player игрок && !игрок.hasInfiniteMaterials()) {
            стопка.shrink(1);
            if (стопка.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            игрок.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
        }
        return стопка;
    }

    private static void выпить(ServerPlayer игрок, Level мир) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        long сейчас = мир.getGameTime();

        // Лихорадку отваром не сбить. Бутылка всё равно пропадает —
        // так игрок узнаёт правило один раз и запоминает.
        if (д.стадия > PlagueConstants.PLAYER_BREW_MAX_STAGE) {
            мир.playSound(null, игрок.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
            return;
        }

        int номер = InfectionMath.счётчикГлотков(д.глотков, д.тикПоследнегоГлотка, сейчас);
        float снимет = InfectionMath.силаОтвара(номер);

        int была = д.стадия;
        д.заражённость = Math.max(0f, д.заражённость - снимет);
        д.стадия = InfectionMath.стадия(д.заражённость);
        д.глотков = номер + 1;
        д.тикПоследнегоГлотка = сейчас;

        if (д.стадия != была) PlayerInfection.пересчитатьЗдоровье(игрок);
    }
}
```

- [ ] **Step 2: Зарегистрировать предмет в `PlagueBlocks.java`**

Дописать импорты:

```java
import net.minecraft.world.item.Item;
```

Дописать рядом с остальными предметами:

```java
    /**
     * Отвар от чумы. Не блок, но живёт здесь же: заводить отдельный
     * DeferredRegister ради одного предмета — лишний файл на пустом месте.
     */
    public static final DeferredItem<Item> PLAGUE_BREW = ПРЕДМЕТЫ.registerItem(
        "plague_brew",
        свойства -> new BrewItem(свойства.stacksTo(16)));
```

- [ ] **Step 3: Создать модель предмета**

`plaguecore/src/main/resources/assets/plaguecore/models/item/plague_brew.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/potion"
  }
}
```

Своей текстуры пока нет — берём ванильную бутылку зелья. Владелец
пришлёт свою в `textures_src/`, тогда заменим одну строку.

- [ ] **Step 4: Создать рецепт**

`plaguecore/src/main/resources/data/plaguecore/recipe/plague_brew.json`:

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "minecraft:glass_bottle" },
    { "item": "minecraft:brown_mushroom" },
    { "item": "minecraft:sugar" },
    { "tag": "minecraft:small_flowers" }
  ],
  "result": {
    "id": "plaguecore:plague_brew",
    "count": 1
  }
}
```

Папка `recipe` в единственном числе — в 1.21 Mojang переименовала все
папки датапака, как и `loot_table`.

- [ ] **Step 5: Добавить названия**

В `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`:

```json
  "item.plaguecore.plague_brew": "Отвар от чумы"
```

В `en_us.json`:

```json
  "item.plaguecore.plague_brew": "Plague Brew"
```

Если файлов ещё нет — создать их с одной этой парой внутри объекта.

- [ ] **Step 6: Собрать**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL.

- [ ] **Step 7: Проверить в игре**

```
./gradlew runServer --console=plain
```

```
/give <ник> plaguecore:plague_brew 10
/plague player <ник> 59
```

Выпить три раза подряд, после каждого смотреть `/plague player <ник>`:
должно быть 46, потом 36, потом 28, стадия 1.

Выпить ещё раз — снимет 7. Подождать пять минут, выпить снова — снова 13.

```
/plague player <ник> 70
```

Выпить: бутылка пропала, число не изменилось, звук глухой.

Проверить рецепт: бутылка + коричневый гриб + сахар + одуванчик
в верстаке дают отвар.

- [ ] **Step 8: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/BrewItem.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueBlocks.java \
        plaguecore/src/main/resources/data/plaguecore/recipe/plague_brew.json \
        plaguecore/src/main/resources/assets/plaguecore/models/item/plague_brew.json \
        plaguecore/src/main/resources/assets/plaguecore/lang
git commit -m "Отвар от чумы: предмет, рецепт, убывающая сила глотков"
git push origin main
```

---

### Task 7: Штраф за смерть

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java`

**Interfaces:**
- Consumes: `PlayerInfection.пересчитатьЗдоровье` (задача 4)
- Produces: ничего наружу

- [ ] **Step 1: Дописать импорты**

```java
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
```

- [ ] **Step 2: Считать смерть и вернуть штраф после возрождения**

Дописать в `PlayerInfection`:

```java
    /**
     * Смерть на стадии 2+ стоит полсердца навсегда.
     *
     * Считается стадия в момент смерти, а не источник урона: умер
     * от лихорадки, от моба, от падения — неважно, важно, что был болен.
     * Правило простое и не требует объяснений.
     */
    @SubscribeEvent
    public static void приСмерти(LivingDeathEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        if (д.стадия < 2) return;

        д.смертей++;
        PlagueCore.LOG.info("{} умер на стадии {}, смертей от чумы: {}",
            игрок.getGameProfile().getName(), д.стадия, д.смертей);
    }

    /**
     * После возрождения игрок — новая сущность с чистыми атрибутами.
     * Вложение переносится само (copyOnDeath), а модификаторы здоровья
     * приходится вешать заново.
     */
    @SubscribeEvent
    public static void приВозрождении(PlayerEvent.PlayerRespawnEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) пересчитатьЗдоровье(игрок);
    }

    /** Тем же порядком — при входе в игру: атрибуты грузятся без наших модификаторов. */
    @SubscribeEvent
    public static void приВходе(PlayerEvent.PlayerLoggedInEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) пересчитатьЗдоровье(игрок);
    }
```

- [ ] **Step 3: Собрать**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL.

- [ ] **Step 4: Проверить в игре**

```
/plague player <ник> 35
/kill <ник>
```

После возрождения: `/plague player <ник>` показывает «смертей от чумы 1»,
максимум здоровья 19 (девять с половиной сердец). Заражённость
сохранилась — смерть не лечит.

Повторить трижды. Максимум должен упасть до 17.

Проверить пол: `/plague player <ник> 0`, затем убить пятнадцать раз
на стадии 2 — максимум не должен опуститься ниже 6.

Проверить жёсткий пол: довести смертей до четырнадцати, потом
`/plague player <ник> 65`. Максимум должен стать 4, а не ноль,
и игрок не должен умирать бесконечно.

Выйти и зайти на сервер — потери на месте.

- [ ] **Step 5: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java
git commit -m "Смерть на стадии 2+ снимает полсердца навсегда, с двумя полами"
git push origin main
```

---

### Task 8: Тусклый экран

Клиент не считает заражённость — он получает готовую стадию пакетом.

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueOverlay.java`

**Interfaces:**
- Consumes: `PlayerPlagueData.данные` (задача 3)
- Produces:
  - `PlagueNetwork.Stage` — пакет `record Stage(int стадия)`
  - `PlagueNetwork.отправитьСтадию(ServerPlayer игрок, int стадия)`
  - `PlagueClientAccess.стадия()` — стадия на клиенте, 0 по умолчанию

- [ ] **Step 1: Добавить пакет `Stage` в `PlagueNetwork.java`**

Дописать после записи `Action`:

```java
    /**
     * Стадия игрока на клиент. Одно число: клиенту незачем знать
     * точную заражённость, а нам незачем её ему доверять.
     */
    public record Stage(int стадия) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Stage> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "stage"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Stage> CODEC =
            StreamCodec.of(
                (buf, s) -> buf.writeVarInt(s.стадия),
                buf -> new Stage(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
```

Дописать в метод `зарегистрировать`, рядом с регистрацией `Snapshot`:

```java
        registrar.playToClient(Stage.TYPE, Stage.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьСтадию(payload)));
```

Дописать метод отправки в конец класса:

```java
    /** Сказать игроку его стадию. Шлётся только при смене, а не каждый тик. */
    public static void отправитьСтадию(ServerPlayer игрок, int стадия) {
        PacketDistributor.sendToPlayer(игрок, new Stage(стадия));
    }
```

- [ ] **Step 2: Слать пакет при смене стадии**

В `PlayerInfection.приТике` заменить блок смены стадии на:

```java
        if (д.стадия != былаСтадия) {
            пересчитатьЗдоровье(игрок);
            PlagueNetwork.отправитьСтадию(игрок, д.стадия);
        }
```

И дописать отправку в `приВходе` и `приВозрождении` — после
`пересчитатьЗдоровье(игрок)` добавить:

```java
        PlagueNetwork.отправитьСтадию(игрок, PlayerPlagueData.данные(игрок).стадия);
```

То же самое добавить в `PlayerInfection.задать` — иначе команда
`/plague player` меняет стадию, а экран остаётся прежним:

```java
    public static void задать(ServerPlayer игрок, float значение) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.заражённость = Math.max(0f, Math.min(100f, значение));
        д.стадия = InfectionMath.стадия(д.заражённость);
        пересчитатьЗдоровье(игрок);
        PlagueNetwork.отправитьСтадию(игрок, д.стадия);
    }
```

Отправку надо добавить и в `PlagueCough.заразитьРядом`, и в
`BrewItem.выпить` — везде, где стадия меняется:

```java
            if (дж.стадия != была) {
                PlayerInfection.пересчитатьЗдоровье(жертва);
                PlagueNetwork.отправитьСтадию(жертва, дж.стадия);
            }
```

- [ ] **Step 3: Хранить стадию на клиенте**

Дописать в `PlagueClientAccess.java`:

```java
    private static int стадия = 0;

    /** Стадия чумы у игрока за этим клиентом. */
    public static int стадия() { return стадия; }

    public static void принятьСтадию(dev.denthe.plaguecore.mc.PlagueNetwork.Stage пакет) {
        стадия = пакет.стадия();
    }
```

- [ ] **Step 4: Создать `PlagueOverlay.java`**

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Экран больного тускнеет. Спек подсистемы 2, раздел 7.
 *
 * Своими руками, а не через клиентский мод `lmpc_shade` второго
 * участника: лезть в чужой шейдер значит согласовывать версии между
 * двумя владельцами папок ради тридцати строк своего кода.
 *
 * Рисуем поверх всего интерфейса полупрозрачный прямоугольник —
 * так же, как ваниль рисует иней и тыкву на голове. Дыхание задаёт
 * синус: неподвижная плёнка через минуту перестаёт читаться как болезнь.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class PlagueOverlay {
    private PlagueOverlay() {}

    /** Плотность плёнки по стадиям 0–4, 0..1. */
    private static final float[] ПЛОТНОСТЬ = { 0f, 0f, 0.18f, 0.34f, 0.42f };

    /** Насколько плотность гуляет от дыхания. */
    private static final float РАЗМАХ = 0.06f;

    @SubscribeEvent
    public static void нарисовать(RenderGuiEvent.Post событие) {
        int стадия = PlagueClientAccess.стадия();
        if (стадия < 2 || стадия >= ПЛОТНОСТЬ.length) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        DeltaTracker время = событие.getPartialTick();
        float такт = mc.player.tickCount + время.getGameTimeDeltaPartialTick(false);
        float дыхание = Mth.sin(такт / 25f) * РАЗМАХ;
        float альфа = Mth.clamp(ПЛОТНОСТЬ[стадия] + дыхание, 0f, 0.8f);

        GuiGraphics графика = событие.getGuiGraphics();
        int цвет = ((int) (альфа * 255f) << 24);   // чёрный с нужной прозрачностью
        графика.fill(0, 0, графика.guiWidth(), графика.guiHeight(), цвет);
    }
}
```

- [ ] **Step 5: Собрать**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL.

- [ ] **Step 6: Проверить в игре**

Здесь нужен клиент, а не только сервер:

```
./gradlew runClient --console=plain
```

Открыть одиночный мир, затем:

```
/plague player <ник> 35
```

Экран заметно потускнел, плотность слегка дышит.

```
/plague player <ник> 65
```

Темнее. На 95 — темнее всего.

```
/plague player <ник> 0
```

Плёнка пропала полностью.

Выйти и зайти — плёнка должна вернуться сама, без команд.

- [ ] **Step 7: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerInfection.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCough.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/BrewItem.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueOverlay.java
git commit -m "Экран больного тускнеет: пакет стадии и свой оверлей"
git push origin main
```

---

### Task 9: PlagueApi

Интерфейс для подсистемы классов. Пишем сейчас, пока свежо в голове,
чтобы Клирику осталось только дёрнуть готовое.

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueApi.java`

**Interfaces:**
- Consumes: всё из задач 3, 4, 8
- Produces:
  - `static void cure(ServerPlayer, float)` — снять очки, без оглядки на стадию
  - `static void grantImmunity(ServerPlayer, int ticks)`
  - `static int getStage(ServerPlayer)`
  - `static float getInfection(ServerPlayer)`
  - `static float getPermanentLoss(ServerPlayer)`

- [ ] **Step 1: Создать `PlagueApi.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.server.level.ServerPlayer;

/**
 * Точка входа для подсистемы классов. Спек ядра, раздел 9.6.
 *
 * Ядро отдаёт наружу только интерфейс. Сами лекарства, рецепты и
 * способности — подсистема 3. Улучшенный отвар Клирика будет предметом,
 * который дёргает {@link #cure} и {@link #grantImmunity}, а не ещё одной
 * копией логики.
 *
 * В отличие от обычного отвара, {@link #cure} работает на любой стадии:
 * ограничение по стадии — свойство предмета, а не лечения как такового.
 */
public final class PlagueApi {
    private PlagueApi() {}

    /** Снять с игрока заражённость. Отрицательное значение игнорируется. */
    public static void cure(ServerPlayer игрок, float очков) {
        if (очков <= 0f) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        PlayerInfection.задать(игрок, д.заражённость - очков);
    }

    /**
     * Иммунитет на N тиков: набор заражённости и кашель соседей
     * не действуют. Уже действующий иммунитет не укорачивается.
     */
    public static void grantImmunity(ServerPlayer игрок, int тиков) {
        if (тиков <= 0) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.иммунитетДо = Math.max(д.иммунитетДо, игрок.level().getGameTime() + тиков);
    }

    public static int getStage(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).стадия;
    }

    public static float getInfection(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).заражённость;
    }

    /** Сколько HP игрок потерял навсегда за смерти от чумы. */
    public static float getPermanentLoss(ServerPlayer игрок) {
        return InfectionMath.постоянныйШтраф(PlayerPlagueData.данные(игрок).смертей);
    }
}
```

- [ ] **Step 2: Собрать и прогнать всё**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 3: Проверить иммунитет в игре**

Проверить через отладочный вызов нечем — команды для API мы не заводим,
она нужна была бы только один раз. Достаточно того, что поле
`иммунитетДо` читается в `PlayerInfection.экспозицияДля`
и в `PlagueCough.заразитьРядом`: обе строки видно глазами в коде.

Проверка появится вместе с первым предметом Клирика в подсистеме 3.

- [ ] **Step 4: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueApi.java
git commit -m "PlagueApi: интерфейс лечения и иммунитета для подсистемы классов"
git push origin main
```

---

### Task 10: Голос через Simple Voice Chat

Отделена нарочно и идёт последней: она единственная тянет новую
зависимость в сборку. Если API окажется дорогим, задачу можно отложить
без ущерба для всего остального — подсистема 2 без неё уже полная.

**Files:**
- Modify: `plaguecore/build.gradle`
- Modify: `plaguecore/gradle.properties`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueVoice.java`
- Modify: `plaguecore/src/main/resources/META-INF/neoforge.mods.toml`

**Interfaces:**
- Consumes: `PlagueApi.getStage` (задача 9)
- Produces: ничего наружу

- [ ] **Step 1: Подключить репозиторий и зависимость**

В `plaguecore/build.gradle`, в блок `repositories`:

```groovy
    maven {
        name = 'Maxanier / henkelmax'
        url = 'https://maven.maxhenkel.de/repository/public'
    }
```

В блок `dependencies`:

```groovy
    compileOnly "de.maxhenkel.voicechat:voicechat-api:${voicechat_api_version}"
```

В `plaguecore/gradle.properties` дописать строку с версией API,
подобрав её под Simple Voice Chat из `mods/MODLIST.md`:

```
voicechat_api_version=2.5.0
```

`compileOnly`, а не `implementation`: мод голосового чата уже лежит
в паке, тащить его копию внутрь нашего джарника не нужно.

- [ ] **Step 2: Проверить, что зависимость тянется**

```
./gradlew build --console=plain
```

Ожидается: BUILD SUCCESSFUL. Если репозиторий недоступен или версия
не та — **остановиться и сообщить владельцу**, не подбирать версии
наугад: точный номер лежит в `mods/MODLIST.md`.

- [ ] **Step 3: Объявить необязательную зависимость в `neoforge.mods.toml`**

Дописать в конец файла:

```toml
[[dependencies.plaguecore]]
    modId = "voicechat"
    type = "optional"
    versionRange = "[2.5,)"
    ordering = "NONE"
    side = "BOTH"
```

`optional`: без голосового чата мод обязан грузиться и работать.

- [ ] **Step 4: Создать `PlagueVoice.java`**

```java
package dev.denthe.plaguecore.mc;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.server.level.ServerPlayer;

/**
 * Голос лихорадящего звучит больным. Спек подсистемы 2, раздел 7.
 *
 * Подключаемся плагином к Simple Voice Chat: мод уже в паке и даёт для
 * этого готовый API. Дёшево в реализации, непропорционально сильно
 * по эффекту — восемь человек услышат, что с товарищем беда, раньше,
 * чем он успеет об этом сказать.
 *
 * Зависимость необязательная: без голосового чата класс просто не
 * загрузится, а всё остальное будет работать.
 */
public class PlagueVoice implements VoicechatPlugin {

    /** Со стадии 3 голос ниже примерно на треть. */
    private static final int ПОРОГ_СТАДИИ = 3;

    @Override
    public String getPluginId() {
        return PlagueCore.MODID;
    }

    @Override
    public void registerEvents(EventRegistration регистрация) {
        регистрация.registerEvent(MicrophonePacketEvent.class, PlagueVoice::приРечи);
    }

    private static void приРечи(MicrophonePacketEvent событие) {
        VoicechatServerApi api = событие.getVoicechat();
        if (событие.getSenderConnection() == null) return;

        Object игрок = событие.getSenderConnection().getPlayer().getPlayer();
        if (!(игрок instanceof ServerPlayer говорящий)) return;
        if (PlagueApi.getStage(говорящий) < ПОРОГ_СТАДИИ) return;

        // ponytail: понижение тона тут — заглушка на то, что даёт API
        // конкретной версии. Если MicrophonePacketEvent не позволяет
        // менять сам звук, вернуться к варианту с собственным
        // AudioSender и обработкой PCM.
        PlagueCore.LOG.debug("{} говорит на стадии {}",
            говорящий.getGameProfile().getName(), PlagueApi.getStage(говорящий));
    }
}
```

**Внимание исполнителю:** точная форма обработки звука зависит от версии
API. Задача считается выполненной, когда голос действительно звучит
иначе, а не когда класс собрался. Если API версии из пака не даёт менять
звук на лету — остановиться, записать находку в
`docs/superpowers/notes/` и спросить владельца. Не выдумывать обходные
пути через ресурспаки.

- [ ] **Step 5: Зарегистрировать плагин**

Создать `plaguecore/src/main/resources/META-INF/services/de.maxhenkel.voicechat.api.VoicechatPlugin`
с единственной строкой:

```
dev.denthe.plaguecore.mc.PlagueVoice
```

- [ ] **Step 6: Проверить в игре**

Проверка на слух, вдвоём. Если второго человека нет — проверить хотя бы
что сервер поднимается без ошибок и в логе есть строка о регистрации
плагина голосового чата.

- [ ] **Step 7: Коммит**

```bash
git add plaguecore/build.gradle plaguecore/gradle.properties \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueVoice.java \
        plaguecore/src/main/resources/META-INF
git commit -m "Голос больного через API Simple Voice Chat"
git push origin main
```

---

## После плана

- [ ] Обновить `CLAUDE.md`: подсистема 2 закрыта, следующая — классы.
- [ ] Написать заметку передачи сессии в `docs/superpowers/notes/`.
- [ ] Записать в заметку живые замеры: за сколько минут игрок без брони
      доходит до стадии 3 в чанке уровня 4 на поверхности и под землёй.
      Спек обещает 6–7 и 5 минут; проверить, так ли это на самом деле.

## Что осталось за границей плана

- Улучшенный отвар Клирика и зелье иммунитета — подсистема 3.
- Своя текстура отвара и свой звук кашля — ждут владельца.
- Мобы, реагирующие на кашель, — решено отложить, спек раздел 11.
- Слот Curios и маски в расчёте защиты — подсистема 3.
- Облако спор от спорового мешка — там же.
