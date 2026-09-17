# Хоррор: режиссёр напряжения — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **В этом проекте подагенты запрещены владельцем** — план исполняется
> последовательно в основной сессии.

**Goal:** Мир сам пугает игрока по ритму — копит напряжение, держит паузы
тишины и тратит бюджет на события трёх цен, вплоть до Наблюдателя.

**Architecture:** Вся арифметика ритма — чистые функции в `core`
(`DreadMath`, `WatcherMath`), проверяются JUnit без запуска игры.
`DreadDirector` раз в секунду переводит мир вокруг игрока в числа,
спрашивает у `DreadMath`, что выдать, и шлёт это адресно одному игроку.
Клиентские эффекты идут пакетом через `PlagueNetwork`.

**Tech Stack:** MC 1.21.1, NeoForge 21.1.249, JDK 21, ModDevGradle,
JUnit 5. Всё внутри `plaguecore`, новых модов и зависимостей нет.

**Spec:** `docs/superpowers/specs/2026-09-17-horror-rezhissyor-design.md`

## Global Constraints

- **Пакет `core` не знает о Minecraft.** Ни одного `import net.minecraft`
  в `core` — это стережёт `CorePurityTest`, который читает исходники.
  Не ослаблять.
- **Идентификаторы русские, имена классов английские** — как во всём
  `plaguecore` (`public static float прирост(...)` в классе `DreadMath`).
  Комментарии русские, стиль окружающих файлов.
- **Все числа — в конфиг.** Дефолты живут в `PlagueConstants`
  (нестатические `final`, чтобы конфиг их перезаписывал), секция
  `[dread]` в `plaguecore-common.toml`. Ни одного захардкоженного порога
  в `DreadDirector`.
- **Адресность.** Событие слышит и видит ровно один игрок. Звук —
  `ClientboundSoundPacket` в `игрок.connection`, не `level.playSound`.
- **Приземлённый источник.** Любое событие объясняется болезнью или живым
  человеком. Ничего мистического в каталог не добавлять.
- **Версия протокола.** При добавлении пакетов поднять `VERSION`
  в `PlagueNetwork` (сейчас `"7"` → станет `"8"`).
- **Персонажи по роли, без имён** — Наблюдатель, а не имя из лора.
- Команда тестов: `cd plaguecore && ./gradlew test`.
  Одного класса: `./gradlew test --tests "*DreadMathTest*"`.

---

### Task 1: DreadMath — арифметика ритма

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/DreadMath.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/DreadMathTest.java`

**Interfaces:**
- Consumes: ничего (первая задача).
- Produces:
  - `DreadMath.Факторы` — record с полями
    `(int свет, int уровеньЧанка, boolean пограничье, int y, boolean ночь,
      int стадия, boolean один, int фаза, int соседей)`
  - `DreadMath.Веса` — record с полями
    `(float темнота, float сумерки, float гниль, float пограничье,
      float глубина, float ночь, float заСтадию, float одиночество,
      float заФазу, float успокоение, float падение, int глубинаY)`
  - `static float прирост(Факторы ф, Веса в)` — сколько напряжения
    добавить за секунду; может быть отрицательным.
  - `static float копить(float напряжение, float прирост)` — с зажимом
    в `0..100`.
  - `enum Уровень { НЕТ, ШОРОХ, ВИДЕНИЕ, ЯВЛЕНИЕ }`
  - `static Уровень уровень(float напряжение, float порогШорох,
    float порогВидение, float порогЯвление, boolean явлениеДоступно)`
  - `static boolean вДолине(long тикСейчас, long тикСобытия, int долгаТиков)`
  - `static boolean бюджетЕсть(int былоЗаЧас, int лимитВЧас)`

- [ ] **Step 1: Написать падающий тест**

Файл `plaguecore/src/test/java/dev/denthe/plaguecore/core/DreadMathTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ритм страха. Спек `2026-09-17-horror-rezhissyor-design.md`, раздел 1.
 *
 * Здесь проверяется только арифметика: что копится, когда срабатывает
 * и когда молчит. Ощущения проверяются на живой сессии, а не тестом.
 */
class DreadMathTest {

    /** Веса по умолчанию из спека — те же числа, что уйдут в конфиг. */
    private static DreadMath.Веса веса() {
        return new DreadMath.Веса(
            0.8f,   // темнота
            0.3f,   // сумерки
            0.6f,   // гниль
            0.3f,   // пограничье
            0.4f,   // глубина
            0.3f,   // ночь
            0.2f,   // за стадию
            1.5f,   // одиночество (множитель)
            0.15f,  // за фазу (множитель)
            0.3f,   // успокоение (множитель)
            1.0f,   // падение в секунду
            40);    // глубина считается ниже этого Y
    }

    private static DreadMath.Факторы город() {
        // Полдень в городе: светло, чисто, рядом двое, здоров.
        return new DreadMath.Факторы(15, 0, false, 70, false, 0, false, 0, 2);
    }

    private static DreadMath.Факторы гниль() {
        // Ночь, один, в темноте, на Гнили, под землёй, вторая стадия.
        return new DreadMath.Факторы(0, 3, false, 20, true, 2, true, 2, 0);
    }

    @Test
    void вГородеДнёмНапряжениеПадает() {
        assertTrue(DreadMath.прирост(город(), веса()) < 0f,
            "светло, людно и чисто — человек должен успокаиваться");
    }

    @Test
    void вГнилиНочьюНапряжениеРастёт() {
        assertTrue(DreadMath.прирост(гниль(), веса()) > 1f,
            "темнота, одиночество и Гниль обязаны копить заметно");
    }

    @Test
    void одиночествоУсиливаетТоЖеМесто() {
        DreadMath.Факторы один = гниль();
        DreadMath.Факторы вдвоём = new DreadMath.Факторы(
            один.свет(), один.уровеньЧанка(), один.пограничье(), один.y(),
            один.ночь(), один.стадия(), false, один.фаза(), 1);
        assertTrue(DreadMath.прирост(один, веса()) > DreadMath.прирост(вдвоём, веса()),
            "одному в том же месте должно быть страшнее");
    }

    @Test
    void напряжениеЗажатоВГраницах() {
        assertEquals(0f, DreadMath.копить(0.5f, -10f), 1e-4f);
        assertEquals(100f, DreadMath.копить(99f, 50f), 1e-4f);
    }

    @Test
    void уровеньВыбираетсяПоНапряжению() {
        assertEquals(DreadMath.Уровень.НЕТ, DreadMath.уровень(10f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ШОРОХ, DreadMath.уровень(70f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ВИДЕНИЕ, DreadMath.уровень(90f, 60f, 85f, 95f, true));
        assertEquals(DreadMath.Уровень.ЯВЛЕНИЕ, DreadMath.уровень(97f, 60f, 85f, 95f, true));
    }

    @Test
    void безБюджетаЯвлениеПадаетДоВидения() {
        assertEquals(DreadMath.Уровень.ВИДЕНИЕ,
            DreadMath.уровень(97f, 60f, 85f, 95f, false),
            "исчерпанный лимит явлений не должен глушить событие совсем");
    }

    @Test
    void вДолинеСобытийНет() {
        assertTrue(DreadMath.вДолине(1000L, 900L, 200));
        assertFalse(DreadMath.вДолине(1200L, 900L, 200));
    }

    @Test
    void бюджетЧасаЗакрывается() {
        assertTrue(DreadMath.бюджетЕсть(5, 6));
        assertFalse(DreadMath.бюджетЕсть(6, 6));
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `cd plaguecore && ./gradlew test --tests "*DreadMathTest*"`
Expected: FAIL — `cannot find symbol: class DreadMath`.

- [ ] **Step 3: Написать DreadMath**

Файл `plaguecore/src/main/java/dev/denthe/plaguecore/core/DreadMath.java`:

```java
package dev.denthe.plaguecore.core;

/**
 * Ритм страха: сколько напряжения копится, когда оно срабатывает
 * и когда обязано молчать. Спек `2026-09-17-horror-rezhissyor-design.md`.
 *
 * Класс не знает о Minecraft: мир переводит в числа `DreadDirector`,
 * сюда приходят уже готовые факторы. Поэтому весь ритм проверяется
 * обычным JUnit, и класс живёт под `CorePurityTest`.
 *
 * Главная мысль всей системы лежит в `вДолине`: страшна не громкость
 * события, а гарантированная тишина после него. Без долины события
 * становятся фоном и перестают читаться к концу первого вечера.
 */
public final class DreadMath {
    private DreadMath() {}

    /** Потолок напряжения. Ниже нуля не опускаем: отрицательного страха нет. */
    public static final float ПОТОЛОК = 100f;

    /** Что вокруг игрока прямо сейчас. Всё, что нужно для прироста. */
    public record Факторы(int свет, int уровеньЧанка, boolean пограничье, int y,
                          boolean ночь, int стадия, boolean один, int фаза,
                          int соседей) {}

    /** Веса факторов. Все до одного правятся в конфиге живьём. */
    public record Веса(float темнота, float сумерки, float гниль, float пограничье,
                       float глубина, float ночь, float заСтадию, float одиночество,
                       float заФазу, float успокоение, float падение, int глубинаY) {}

    /** Что выдать игроку. НЕТ — молчим. */
    public enum Уровень { НЕТ, ШОРОХ, ВИДЕНИЕ, ЯВЛЕНИЕ }

    /**
     * Прирост напряжения за секунду. Может быть отрицательным: в светлом
     * людном месте человек успокаивается, и это часть ритма — иначе к
     * третьему дню все ходили бы с полным счётчиком.
     */
    public static float прирост(Факторы ф, Веса в) {
        float сумма = 0f;
        if (ф.свет() < 4) сумма += в.темнота();
        else if (ф.свет() < 8) сумма += в.сумерки();

        if (ф.уровеньЧанка() >= 3) сумма += в.гниль();
        if (ф.пограничье()) сумма += в.пограничье();
        if (ф.y() < в.глубинаY()) сумма += в.глубина();
        if (ф.ночь()) сумма += в.ночь();
        сумма += в.заСтадию() * ф.стадия();

        if (сумма <= 0f) return -в.падение();

        if (ф.один()) сумма *= в.одиночество();
        сумма *= 1f + в.заФазу() * ф.фаза();
        // Светло, людно и чисто — компания гасит почти всё.
        if (ф.соседей() >= 2 && ф.свет() >= 8) сумма *= в.успокоение();
        return сумма;
    }

    /** Накопить с зажимом в границы. */
    public static float копить(float напряжение, float прирост) {
        float новое = напряжение + прирост;
        if (новое < 0f) return 0f;
        return Math.min(новое, ПОТОЛОК);
    }

    /**
     * Какого уровня событие заслужено. Если лимит явлений на сессию
     * исчерпан, выдаём видение, а не тишину: человек дошёл до сотни,
     * промолчать здесь значит обмануть его ожидание.
     */
    public static Уровень уровень(float напряжение, float порогШорох,
                                  float порогВидение, float порогЯвление,
                                  boolean явлениеДоступно) {
        if (напряжение >= порогЯвление && явлениеДоступно) return Уровень.ЯВЛЕНИЕ;
        if (напряжение >= порогВидение) return Уровень.ВИДЕНИЕ;
        if (напряжение >= порогШорох) return Уровень.ШОРОХ;
        return Уровень.НЕТ;
    }

    /** Идёт ли гарантированная тишина после прошлого события. */
    public static boolean вДолине(long тикСейчас, long тикСобытия, int долгаТиков) {
        return тикСейчас - тикСобытия < долгаТиков;
    }

    /** Остался ли часовой лимит событий этого уровня. */
    public static boolean бюджетЕсть(int былоЗаЧас, int лимитВЧас) {
        return былоЗаЧас < лимитВЧас;
    }
}
```

- [ ] **Step 4: Убедиться, что тесты проходят**

Run: `cd plaguecore && ./gradlew test --tests "*DreadMathTest*"`
Expected: PASS, 8 тестов.

- [ ] **Step 5: Проверить чистоту core**

Run: `cd plaguecore && ./gradlew test --tests "*CorePurityTest*"`
Expected: PASS — в `DreadMath` нет ни одного `import net.minecraft`.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/core/DreadMath.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/DreadMathTest.java
git commit -m "Ритм страха: напряжение, пороги, долины, бюджет"
```

---

### Task 2: Константы и конфиг `[dread]`

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConstants.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConfig.java`

**Interfaces:**
- Consumes: `DreadMath.Веса` из задачи 1.
- Produces: поля `PlagueConstants` (все `public static`, не `final`, чтобы
  конфиг их переписывал):
  `DREAD_ENABLED`, `DREAD_DARK`, `DREAD_DUSK`, `DREAD_BLIGHT`,
  `DREAD_BORDER`, `DREAD_DEPTH`, `DREAD_NIGHT`, `DREAD_PER_STAGE`,
  `DREAD_ALONE`, `DREAD_PER_PHASE`, `DREAD_CALM`, `DREAD_FALL`,
  `DREAD_DEPTH_Y`, `DREAD_ALONE_RADIUS`,
  `DREAD_RUSTLE_AT`, `DREAD_VISION_AT`, `DREAD_WATCHER_AT`,
  `DREAD_VALLEY_RUSTLE`, `DREAD_VALLEY_VISION`, `DREAD_VALLEY_WATCHER`,
  `DREAD_RUSTLES_PER_HOUR`, `DREAD_VISIONS_PER_HOUR`,
  `DREAD_WATCHERS_PER_SESSION`
  и метод `PlagueConstants.весаСтраха()` → `DreadMath.Веса`.

- [ ] **Step 1: Добавить константы**

В `PlagueConstants.java`, новой секцией в конце (стиль файла: комментарий
шапкой, затем поля):

```java
    // ── страх ─────────────────────────────────────────────────────────
    // Спек `2026-09-17-horror-rezhissyor-design.md`. Все числа —
    // первые прикидки: балансировать придётся на живых людях, поэтому
    // каждое из них правится в конфиге без пересборки.

    /** Выключатель всей подсистемы: если на сессии окажется перебор. */
    public static boolean DREAD_ENABLED = true;

    /** Прирост за секунду в темноте (свет < 4). */
    public static float DREAD_DARK = 0.8f;
    /** Прирост за секунду в сумерках (свет 4–7). */
    public static float DREAD_DUSK = 0.3f;
    /** Прирост за секунду на Гнили (уровень чанка 3+). */
    public static float DREAD_BLIGHT = 0.6f;
    /** Прирост за секунду в Пограничье. */
    public static float DREAD_BORDER = 0.3f;
    /** Прирост за секунду под землёй. */
    public static float DREAD_DEPTH = 0.4f;
    /** Ниже какого Y считается «под землёй». */
    public static int DREAD_DEPTH_Y = 40;
    /** Прирост за секунду ночью. */
    public static float DREAD_NIGHT = 0.3f;
    /** Прирост за секунду за каждую стадию болезни. */
    public static float DREAD_PER_STAGE = 0.2f;
    /** Множитель за одиночество. */
    public static float DREAD_ALONE = 1.5f;
    /** В каком радиусе ищем живого соседа. */
    public static double DREAD_ALONE_RADIUS = 32.0;
    /** Насколько фаза эпидемии усиливает всё сразу. */
    public static float DREAD_PER_PHASE = 0.15f;
    /** Множитель, когда рядом двое и светло. */
    public static float DREAD_CALM = 0.3f;
    /** Падение за секунду в безопасном месте. */
    public static float DREAD_FALL = 1.0f;

    /** Порог шороха. */
    public static float DREAD_RUSTLE_AT = 60f;
    /** Порог видения. */
    public static float DREAD_VISION_AT = 85f;
    /** Порог явления. */
    public static float DREAD_WATCHER_AT = 95f;

    /** Тишина после шороха, тиков (180 с). */
    public static int DREAD_VALLEY_RUSTLE = 3600;
    /** Тишина после видения, тиков (300 с). */
    public static int DREAD_VALLEY_VISION = 6000;
    /** Тишина после явления, тиков (900 с). */
    public static int DREAD_VALLEY_WATCHER = 18000;

    /** Потолок шорохов в час на игрока. */
    public static int DREAD_RUSTLES_PER_HOUR = 6;
    /** Потолок видений в час на игрока. */
    public static int DREAD_VISIONS_PER_HOUR = 2;
    /** Потолок явлений за сессию на весь сервер. */
    public static int DREAD_WATCHERS_PER_SESSION = 3;

    /** Веса для `DreadMath` одним куском — директору не собирать их руками. */
    public static dev.denthe.plaguecore.core.DreadMath.Веса весаСтраха() {
        return new dev.denthe.plaguecore.core.DreadMath.Веса(
            DREAD_DARK, DREAD_DUSK, DREAD_BLIGHT, DREAD_BORDER, DREAD_DEPTH,
            DREAD_NIGHT, DREAD_PER_STAGE, DREAD_ALONE, DREAD_PER_PHASE,
            DREAD_CALM, DREAD_FALL, DREAD_DEPTH_Y);
    }
```

- [ ] **Step 2: Добавить секцию конфига**

В `PlagueConfig.java` — поля рядом с остальными объявлениями:

```java
    // ── страх ─────────────────────────────────────────────────────────
    private static final ModConfigSpec.BooleanValue СТРАХ_ВКЛ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ТЕМНОТА;
    private static final ModConfigSpec.DoubleValue СТРАХ_СУМЕРКИ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ГНИЛЬ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ПОГРАНИЧЬЕ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ГЛУБИНА;
    private static final ModConfigSpec.IntValue СТРАХ_ГЛУБИНА_Y;
    private static final ModConfigSpec.DoubleValue СТРАХ_НОЧЬ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ЗА_СТАДИЮ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ОДИНОЧЕСТВО;
    private static final ModConfigSpec.DoubleValue СТРАХ_РАДИУС;
    private static final ModConfigSpec.DoubleValue СТРАХ_ЗА_ФАЗУ;
    private static final ModConfigSpec.DoubleValue СТРАХ_УСПОКОЕНИЕ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ПАДЕНИЕ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ПОРОГ_ШОРОХ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ПОРОГ_ВИДЕНИЕ;
    private static final ModConfigSpec.DoubleValue СТРАХ_ПОРОГ_ЯВЛЕНИЕ;
    private static final ModConfigSpec.IntValue СТРАХ_ДОЛИНА_ШОРОХ;
    private static final ModConfigSpec.IntValue СТРАХ_ДОЛИНА_ВИДЕНИЕ;
    private static final ModConfigSpec.IntValue СТРАХ_ДОЛИНА_ЯВЛЕНИЕ;
    private static final ModConfigSpec.IntValue СТРАХ_ШОРОХОВ_В_ЧАС;
    private static final ModConfigSpec.IntValue СТРАХ_ВИДЕНИЙ_В_ЧАС;
    private static final ModConfigSpec.IntValue СТРАХ_ЯВЛЕНИЙ_ЗА_СЕССИЮ;
```

В статическом блоке, где строится спека, — рядом с остальными `push`:

```java
        СТРОИТЕЛЬ.pop().comment(
            "Страх: как быстро копится напряжение и когда оно срабатывает.",
            "Спек 2026-09-17-horror-rezhissyor-design.",
            "Долины тишины важнее порогов: без них события становятся фоном."
        ).push("dread");

        СТРАХ_ВКЛ = СТРОИТЕЛЬ
            .comment("Выключатель всей подсистемы страха.")
            .define("enabled", true);
        СТРАХ_ТЕМНОТА = СТРОИТЕЛЬ
            .comment("Прирост напряжения за секунду в темноте (свет < 4).")
            .defineInRange("dark", 0.8, 0.0, 20.0);
        СТРАХ_СУМЕРКИ = СТРОИТЕЛЬ
            .comment("Прирост за секунду в сумерках (свет 4-7).")
            .defineInRange("dusk", 0.3, 0.0, 20.0);
        СТРАХ_ГНИЛЬ = СТРОИТЕЛЬ
            .comment("Прирост за секунду на Гнили (уровень чанка 3+).")
            .defineInRange("blight", 0.6, 0.0, 20.0);
        СТРАХ_ПОГРАНИЧЬЕ = СТРОИТЕЛЬ
            .comment("Прирост за секунду в Пограничье.")
            .defineInRange("border", 0.3, 0.0, 20.0);
        СТРАХ_ГЛУБИНА = СТРОИТЕЛЬ
            .comment("Прирост за секунду под землёй.")
            .defineInRange("depth", 0.4, 0.0, 20.0);
        СТРАХ_ГЛУБИНА_Y = СТРОИТЕЛЬ
            .comment("Ниже какого Y место считается подземельем.")
            .defineInRange("depthY", 40, -64, 320);
        СТРАХ_НОЧЬ = СТРОИТЕЛЬ
            .comment("Прирост за секунду ночью.")
            .defineInRange("night", 0.3, 0.0, 20.0);
        СТРАХ_ЗА_СТАДИЮ = СТРОИТЕЛЬ
            .comment("Прирост за секунду за каждую стадию болезни.")
            .defineInRange("perStage", 0.2, 0.0, 20.0);
        СТРАХ_ОДИНОЧЕСТВО = СТРОИТЕЛЬ
            .comment("Множитель, когда рядом нет живого игрока.")
            .defineInRange("alone", 1.5, 1.0, 10.0);
        СТРАХ_РАДИУС = СТРОИТЕЛЬ
            .comment("В каком радиусе ищется сосед.")
            .defineInRange("aloneRadius", 32.0, 4.0, 256.0);
        СТРАХ_ЗА_ФАЗУ = СТРОИТЕЛЬ
            .comment("Насколько фаза эпидемии усиливает весь страх.")
            .defineInRange("perPhase", 0.15, 0.0, 2.0);
        СТРАХ_УСПОКОЕНИЕ = СТРОИТЕЛЬ
            .comment("Множитель, когда рядом двое и светло.")
            .defineInRange("calm", 0.3, 0.0, 1.0);
        СТРАХ_ПАДЕНИЕ = СТРОИТЕЛЬ
            .comment("Падение напряжения за секунду в безопасном месте.")
            .defineInRange("fall", 1.0, 0.0, 20.0);
        СТРАХ_ПОРОГ_ШОРОХ = СТРОИТЕЛЬ
            .comment("Порог шороха.")
            .defineInRange("rustleAt", 60.0, 1.0, 100.0);
        СТРАХ_ПОРОГ_ВИДЕНИЕ = СТРОИТЕЛЬ
            .comment("Порог видения.")
            .defineInRange("visionAt", 85.0, 1.0, 100.0);
        СТРАХ_ПОРОГ_ЯВЛЕНИЕ = СТРОИТЕЛЬ
            .comment("Порог явления Наблюдателя.")
            .defineInRange("watcherAt", 95.0, 1.0, 100.0);
        СТРАХ_ДОЛИНА_ШОРОХ = СТРОИТЕЛЬ
            .comment("Тишина после шороха, тиков.")
            .defineInRange("valleyRustle", 3600, 0, 216000);
        СТРАХ_ДОЛИНА_ВИДЕНИЕ = СТРОИТЕЛЬ
            .comment("Тишина после видения, тиков.")
            .defineInRange("valleyVision", 6000, 0, 216000);
        СТРАХ_ДОЛИНА_ЯВЛЕНИЕ = СТРОИТЕЛЬ
            .comment("Тишина после явления, тиков.")
            .defineInRange("valleyWatcher", 18000, 0, 216000);
        СТРАХ_ШОРОХОВ_В_ЧАС = СТРОИТЕЛЬ
            .comment("Потолок шорохов в час на игрока.")
            .defineInRange("rustlesPerHour", 6, 0, 120);
        СТРАХ_ВИДЕНИЙ_В_ЧАС = СТРОИТЕЛЬ
            .comment("Потолок видений в час на игрока.")
            .defineInRange("visionsPerHour", 2, 0, 60);
        СТРАХ_ЯВЛЕНИЙ_ЗА_СЕССИЮ = СТРОИТЕЛЬ
            .comment("Потолок явлений Наблюдателя за сессию, на весь сервер.")
            .defineInRange("watchersPerSession", 3, 0, 50);
```

- [ ] **Step 3: Переписать константы из конфига**

В том же методе `PlagueConfig`, где значения переносятся в
`PlagueConstants` (рядом с остальными присваиваниями), добавить:

```java
        PlagueConstants.DREAD_ENABLED = СТРАХ_ВКЛ.get();
        PlagueConstants.DREAD_DARK = СТРАХ_ТЕМНОТА.get().floatValue();
        PlagueConstants.DREAD_DUSK = СТРАХ_СУМЕРКИ.get().floatValue();
        PlagueConstants.DREAD_BLIGHT = СТРАХ_ГНИЛЬ.get().floatValue();
        PlagueConstants.DREAD_BORDER = СТРАХ_ПОГРАНИЧЬЕ.get().floatValue();
        PlagueConstants.DREAD_DEPTH = СТРАХ_ГЛУБИНА.get().floatValue();
        PlagueConstants.DREAD_DEPTH_Y = СТРАХ_ГЛУБИНА_Y.get();
        PlagueConstants.DREAD_NIGHT = СТРАХ_НОЧЬ.get().floatValue();
        PlagueConstants.DREAD_PER_STAGE = СТРАХ_ЗА_СТАДИЮ.get().floatValue();
        PlagueConstants.DREAD_ALONE = СТРАХ_ОДИНОЧЕСТВО.get().floatValue();
        PlagueConstants.DREAD_ALONE_RADIUS = СТРАХ_РАДИУС.get();
        PlagueConstants.DREAD_PER_PHASE = СТРАХ_ЗА_ФАЗУ.get().floatValue();
        PlagueConstants.DREAD_CALM = СТРАХ_УСПОКОЕНИЕ.get().floatValue();
        PlagueConstants.DREAD_FALL = СТРАХ_ПАДЕНИЕ.get().floatValue();
        PlagueConstants.DREAD_RUSTLE_AT = СТРАХ_ПОРОГ_ШОРОХ.get().floatValue();
        PlagueConstants.DREAD_VISION_AT = СТРАХ_ПОРОГ_ВИДЕНИЕ.get().floatValue();
        PlagueConstants.DREAD_WATCHER_AT = СТРАХ_ПОРОГ_ЯВЛЕНИЕ.get().floatValue();
        PlagueConstants.DREAD_VALLEY_RUSTLE = СТРАХ_ДОЛИНА_ШОРОХ.get();
        PlagueConstants.DREAD_VALLEY_VISION = СТРАХ_ДОЛИНА_ВИДЕНИЕ.get();
        PlagueConstants.DREAD_VALLEY_WATCHER = СТРАХ_ДОЛИНА_ЯВЛЕНИЕ.get();
        PlagueConstants.DREAD_RUSTLES_PER_HOUR = СТРАХ_ШОРОХОВ_В_ЧАС.get();
        PlagueConstants.DREAD_VISIONS_PER_HOUR = СТРАХ_ВИДЕНИЙ_В_ЧАС.get();
        PlagueConstants.DREAD_WATCHERS_PER_SESSION = СТРАХ_ЯВЛЕНИЙ_ЗА_СЕССИЮ.get();
```

- [ ] **Step 4: Собрать**

Run: `cd plaguecore && ./gradlew build`
Expected: BUILD SUCCESSFUL, прежние тесты зелёные.

- [ ] **Step 5: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConstants.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConfig.java
git commit -m "Секция [dread] в конфиге: веса, пороги, долины, бюджеты"
```

---

### Task 3: Режиссёр и шорохи

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadDirector.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadCatalog.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/DreadMathTest.java` (дополнить)

**Interfaces:**
- Consumes: `DreadMath`, `PlagueConstants.весаСтраха()`,
  `PlagueApi.getStage`, `PlagueApi.getChunkLevelAt`, `PlagueState.phase()`.
- Produces:
  - `DreadDirector.состояние(ServerPlayer)` → `DreadDirector.Счёт`
    (record `(float напряжение, long последнее, int шороховЗаЧас,
    int виденийЗаЧас)`) — нужен панели мастера в задаче 6.
  - `static void выдать(ServerPlayer игрок, DreadMath.Уровень уровень)` —
    ручная выдача события с той же долиной, для команд задачи 6.
  - `static void тишина(ServerPlayer игрок, int тиков)`.
  - `DreadCatalog.шорох(ServerPlayer игрок, RandomSource случай)` —
    проигрывает один случайный шорох адресно.

- [ ] **Step 1: Дописать тест на точку шороха**

Логика «куда поставить звук» чистая — её место в `core`. Добавить
в `DreadMathTest`:

```java
    @Test
    void шорохСтавитсяЗаСпиной() {
        // Смотрим на восток (yaw = -90 в Minecraft), значит источник
        // должен оказаться западнее игрока.
        float[] точка = DreadMath.заСпиной(100f, 64f, 100f, -90f, 4f);
        assertTrue(точка[0] < 100f, "звук обязан быть позади, а не перед лицом");
        assertEquals(64f, точка[1], 1e-3f);
    }
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `cd plaguecore && ./gradlew test --tests "*DreadMathTest*"`
Expected: FAIL — `cannot find symbol: method заСпиной`.

- [ ] **Step 3: Добавить `заСпиной` в DreadMath**

```java
    /**
     * Точка в `дальность` блоках за спиной игрока. Чистая тригонометрия:
     * директор отдаёт сюда позицию и поворот, получает координаты звука.
     *
     * Звук именно за спиной, а не вокруг: шаг сбоку человек проверяет
     * поворотом головы и успокаивается, шаг сзади — нет.
     */
    public static float[] заСпиной(float x, float y, float z, float yaw, float дальность) {
        double радианы = Math.toRadians(yaw);
        // Взгляд: (-sin, cos). Спина — та же ось со знаком минус.
        float dx = (float) Math.sin(радианы) * дальность;
        float dz = (float) -Math.cos(радианы) * дальность;
        return new float[] { x + dx, y, z + dz };
    }
```

- [ ] **Step 4: Убедиться, что тесты проходят**

Run: `cd plaguecore && ./gradlew test --tests "*DreadMathTest*"`
Expected: PASS, 9 тестов.

- [ ] **Step 5: Написать каталог шорохов**

Файл `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadCatalog.java`:

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.DreadMath;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Каталог шорохов: чем именно мир скрипит за спиной.
 * Спек `2026-09-17-horror-rezhissyor-design.md`, раздел 2.
 *
 * Звуки ванильные, с переписанным питчем, плюс собственный кашель.
 * Своих сэмплов не пишем: каждый файл — это вес в раздаче пака, а восемь
 * человек за четыре дня не успеют выучить ванильную библиотеку шорохов.
 *
 * Пакет шлётся лично игроку, а не через `level.playSound`: соседи не
 * должны слышать ничего. Весь смысл в ответе «ты слышал? — нет».
 */
public final class DreadCatalog {
    private DreadCatalog() {}

    /** Один шорох: звук, громкость и питч. */
    private record Шорох(SoundEvent звук, float громкость, float питч) {}

    private static final Шорох[] ШОРОХИ = {
        new Шорох(SoundEvents.GRAVEL_STEP, 0.7f, 1.0f),
        new Шорох(SoundEvents.STONE_STEP, 0.7f, 1.0f),
        new Шорох(SoundEvents.WOODEN_DOOR_OPEN.value(), 0.5f, 0.6f),
        new Шорох(SoundEvents.STONE_BREAK, 0.6f, 0.8f),
        new Шорох(SoundEvents.ZOMBIE_AMBIENT, 0.4f, 0.5f),
    };

    /** Ближняя и дальняя граница расстояния до источника. */
    private static final float БЛИЖЕ = 3f;
    private static final float ДАЛЬШЕ = 6f;

    /** Проиграть один случайный шорох за спиной игрока. Слышит только он. */
    public static void шорох(ServerPlayer игрок, RandomSource случай) {
        Шорох ш = ШОРОХИ[случай.nextInt(ШОРОХИ.length)];
        // Кашель из-за стены даём отдельной веткой: у него свой звук мода.
        float дальность = БЛИЖЕ + случай.nextFloat() * (ДАЛЬШЕ - БЛИЖЕ);
        float[] точка = DreadMath.заСпиной(
            (float) игрок.getX(), (float) игрок.getY(), (float) игрок.getZ(),
            игрок.getYRot(), дальность);
        послать(игрок, ш.звук(), ш.громкость(), ш.питч(), точка, случай);
    }

    /** Чужой кашель за стеной — свой звук мода, отдельным поводом. */
    public static void кашель(ServerPlayer игрок, RandomSource случай) {
        float[] точка = DreadMath.заСпиной(
            (float) игрок.getX(), (float) игрок.getY(), (float) игрок.getZ(),
            игрок.getYRot(), ДАЛЬШЕ);
        послать(игрок, PlagueSounds.PLAYER_COUGH.get(), 0.6f, 0.8f, точка, случай);
    }

    private static void послать(ServerPlayer игрок, SoundEvent звук, float громкость,
                                float питч, float[] точка, RandomSource случай) {
        игрок.connection.send(new ClientboundSoundPacket(
            net.minecraft.core.Holder.direct(звук), SoundSource.AMBIENT,
            точка[0], точка[1], точка[2], громкость, питч, случай.nextLong()));
    }
}
```

- [ ] **Step 6: Написать режиссёра**

Файл `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadDirector.java`:

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.DreadMath;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Режиссёр напряжения. Спек `2026-09-17-horror-rezhissyor-design.md`.
 *
 * Раз в секунду переводит мир вокруг игрока в `DreadMath.Факторы`,
 * копит напряжение и, когда оно переваливает порог, выдаёт событие.
 * Вся арифметика — в `core`, здесь только чтение мира и исполнение.
 *
 * Счёт живёт в памяти и не сохраняется: переживать перезапуск ему
 * незачем, а после рестарта сервера тишина в начале даже к месту.
 * Единственное, что обязано пережить перезапуск, — счётчик явлений;
 * он лежит в `PlagueState`.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class DreadDirector {
    private DreadDirector() {}

    /** Счёт одного игрока. */
    public record Счёт(float напряжение, long последнее, int долина,
                       int шороховЗаЧас, int виденийЗаЧас, long началоЧаса) {}

    private static final Map<UUID, Счёт> СЧЁТ = new HashMap<>();

    /** Тиков в часе реального времени. */
    private static final long ЧАС = 72000L;

    public static Счёт состояние(ServerPlayer игрок) {
        return СЧЁТ.getOrDefault(игрок.getUUID(), new Счёт(0f, 0L, 0, 0, 0, 0L));
    }

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!PlagueConstants.DREAD_ENABLED) return;
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;
        if (игрок.level().dimension() != Level.OVERWORLD) return;

        ServerLevel уровень = игрок.serverLevel();
        long сейчас = уровень.getGameTime();
        Счёт счёт = сбросЧаса(состояние(игрок), сейчас);

        float прирост = DreadMath.прирост(факторы(игрок, уровень),
            PlagueConstants.весаСтраха());
        // Прирост считается на секунду, а тик режиссёра — раз в секунду.
        float напряжение = DreadMath.копить(счёт.напряжение(), прирост);

        if (DreadMath.вДолине(сейчас, счёт.последнее(), счёт.долина())) {
            запомнить(игрок, new Счёт(напряжение, счёт.последнее(), счёт.долина(),
                счёт.шороховЗаЧас(), счёт.виденийЗаЧас(), счёт.началоЧаса()));
            return;
        }

        DreadMath.Уровень уровеньСобытия = DreadMath.уровень(напряжение,
            PlagueConstants.DREAD_RUSTLE_AT, PlagueConstants.DREAD_VISION_AT,
            PlagueConstants.DREAD_WATCHER_AT, явлениеДоступно(уровень));

        уровеньСобытия = поБюджету(уровеньСобытия, счёт);
        if (уровеньСобытия == DreadMath.Уровень.НЕТ) {
            запомнить(игрок, new Счёт(напряжение, счёт.последнее(), счёт.долина(),
                счёт.шороховЗаЧас(), счёт.виденийЗаЧас(), счёт.началоЧаса()));
            return;
        }

        выдать(игрок, уровеньСобытия);
    }

    /** Выдать событие и завести долину. Этим же пользуется панель мастера. */
    public static void выдать(ServerPlayer игрок, DreadMath.Уровень уровень) {
        Счёт счёт = состояние(игрок);
        long сейчас = игрок.serverLevel().getGameTime();
        int долина;
        int шорохов = счёт.шороховЗаЧас();
        int видений = счёт.виденийЗаЧас();

        switch (уровень) {
            case ШОРОХ -> {
                DreadCatalog.шорох(игрок, игрок.serverLevel().getRandom());
                долина = PlagueConstants.DREAD_VALLEY_RUSTLE;
                шорохов++;
            }
            case ВИДЕНИЕ -> {
                // Видения приедут в задаче 4; пока тот же шорох, но
                // с длинной долиной — чтобы ритм читался уже сейчас.
                DreadCatalog.кашель(игрок, игрок.serverLevel().getRandom());
                долина = PlagueConstants.DREAD_VALLEY_VISION;
                видения++;
            }
            case ЯВЛЕНИЕ -> {
                // Наблюдатель приедет в задаче 5.
                DreadCatalog.кашель(игрок, игрок.serverLevel().getRandom());
                долина = PlagueConstants.DREAD_VALLEY_WATCHER;
            }
            default -> {
                return;
            }
        }

        запомнить(игрок, new Счёт(0f, сейчас, долина, шорохов, видения,
            счёт.началоЧаса()));
    }

    /** Заткнуть режиссёра на время — ручка мастера перед ролевой сценой. */
    public static void тишина(ServerPlayer игрок, int тиков) {
        Счёт счёт = состояние(игрок);
        запомнить(игрок, new Счёт(счёт.напряжение(), игрок.serverLevel().getGameTime(),
            тиков, счёт.шороховЗаЧас(), счёт.виденийЗаЧас(), счёт.началоЧаса()));
    }

    private static DreadMath.Факторы факторы(ServerPlayer игрок, ServerLevel уровень) {
        int свет = уровень.getMaxLocalRawBrightness(игрок.blockPosition());
        int уровеньЧанка = Math.max(0, PlagueApi.getChunkLevelAt(уровень, игрок.blockPosition()));
        boolean ночь = !уровень.isDay();
        int стадия = PlagueApi.getStage(игрок);
        int фаза = PlagueState.get(уровень).phase();
        int соседей = соседей(игрок, уровень);
        boolean пограничье = вПограничье(уровень, игрок);

        return new DreadMath.Факторы(свет, уровеньЧанка, пограничье,
            игрок.blockPosition().getY(), ночь, стадия, соседей == 0, фаза, соседей);
    }

    private static int соседей(ServerPlayer игрок, ServerLevel уровень) {
        double радиус = PlagueConstants.DREAD_ALONE_RADIUS;
        int счёт = 0;
        for (Player другой : уровень.players()) {
            if (другой == игрок || другой.isSpectator()) continue;
            if (другой.distanceToSqr(игрок) <= радиус * радиус) счёт++;
        }
        return счёт;
    }

    /** Пограничье — кольцо вокруг очага: уровни 1–2 сетки. */
    private static boolean вПограничье(ServerLevel уровень, ServerPlayer игрок) {
        int у = PlagueApi.getChunkLevel(уровень,
            SectionPos.blockToSectionCoord(игрок.getBlockX()),
            SectionPos.blockToSectionCoord(игрок.getBlockZ()));
        return у == 1 || у == 2;
    }

    private static boolean явлениеДоступно(ServerLevel уровень) {
        // Наблюдатель приедет в задаче 5; до тех пор явлений нет.
        return false;
    }

    private static DreadMath.Уровень поБюджету(DreadMath.Уровень уровень, Счёт счёт) {
        if (уровень == DreadMath.Уровень.ВИДЕНИЕ
            && !DreadMath.бюджетЕсть(счёт.виденийЗаЧас(), PlagueConstants.DREAD_VISIONS_PER_HOUR)) {
            уровень = DreadMath.Уровень.ШОРОХ;
        }
        if (уровень == DreadMath.Уровень.ШОРОХ
            && !DreadMath.бюджетЕсть(счёт.шороховЗаЧас(), PlagueConstants.DREAD_RUSTLES_PER_HOUR)) {
            return DreadMath.Уровень.НЕТ;
        }
        return уровень;
    }

    private static Счёт сбросЧаса(Счёт счёт, long сейчас) {
        if (сейчас - счёт.началоЧаса() < ЧАС) return счёт;
        return new Счёт(счёт.напряжение(), счёт.последнее(), счёт.долина(), 0, 0, сейчас);
    }

    private static void запомнить(ServerPlayer игрок, Счёт счёт) {
        СЧЁТ.put(игрок.getUUID(), счёт);
    }
}
```

- [ ] **Step 7: Собрать и прогнать тесты**

Run: `cd plaguecore && ./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadDirector.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadCatalog.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/core/DreadMath.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/DreadMathTest.java
git commit -m "Режиссёр напряжения и шорохи за спиной"
```

---

### Task 4: Видения

**Files:**
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/client/DreadClient.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueOverlay.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadDirector.java`

**Interfaces:**
- Consumes: `DreadDirector.выдать`, `PlagueNetwork` registrar-паттерн.
- Produces:
  - пакет `PlagueNetwork.Vision(int вид, int тиков, double x, double y, double z)`,
    где вид: `0` — темнота, `1` — сердцебиение, `2` — силуэт;
  - `DreadClient.принять(Vision)`;
  - `DreadClient.темнотаДо()` / `DreadClient.силаТемноты(float частичный)` —
    читает `PlagueOverlay` при отрисовке.

- [ ] **Step 1: Добавить пакет**

В `PlagueNetwork.java`, рядом с остальными записями:

```java
    /**
     * Видение — короткий обман чувств на клиенте.
     * `вид`: 0 — темнота, 1 — сердцебиение, 2 — силуэт.
     * Для силуэта x/y/z — где его нарисовать; для остальных они нули.
     */
    public record Vision(int вид, int тиков, double x, double y, double z)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Vision> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "vision"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Vision> CODEC =
            StreamCodec.of(
                (buf, v) -> {
                    buf.writeVarInt(v.вид);
                    buf.writeVarInt(v.тиков);
                    buf.writeDouble(v.x);
                    buf.writeDouble(v.y);
                    buf.writeDouble(v.z);
                },
                buf -> new Vision(buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
```

Там же поднять версию протокола и зарегистрировать (рядом с прочими
`playToClient`, лямбдой на `PlagueClientAccess` — на сервере она не
выполняется):

```java
    private static final String VERSION = "8";
```

```java
        registrar.playToClient(Vision.TYPE, Vision.CODEC,
            (пакет, контекст) -> контекст.enqueueWork(
                () -> PlagueClientAccess.видение(пакет)));
```

- [ ] **Step 2: Клиентская сторона**

Файл `plaguecore/src/main/java/dev/denthe/plaguecore/client/DreadClient.java`:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Клиентская половина видений. Спек раздел 2.
 *
 * Силуэт — клиентская сущность, добавленная только в свой `ClientLevel`.
 * Сервер о ней не знает: её нельзя ударить, она не бьёт, не толкает и не
 * попадает в чужие экраны. Это самый дешёвый способ показать человека на
 * краю зрения — писать свой рендерер ради полутора секунд незачем.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class DreadClient {
    private DreadClient() {}

    public static final int ТЕМНОТА = 0;
    public static final int СЕРДЦЕ = 1;
    public static final int СИЛУЭТ = 2;

    /** До какого клиентского тика держится темнота. */
    private static long темнотаДо;
    /** Сколько всего тиков длится текущая темнота — для плавности. */
    private static int темнотаВсего;

    private static Zombie силуэт;
    private static long силуэтДо;

    private static long тик;

    public static void принять(PlagueNetwork.Vision пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        switch (пакет.вид()) {
            case ТЕМНОТА -> {
                темнотаДо = тик + пакет.тиков();
                темнотаВсего = пакет.тиков();
            }
            case СЕРДЦЕ -> mc.player.playSound(
                SoundEvents.WARDEN_HEARTBEAT, 0.6f, 1.0f);
            case СИЛУЭТ -> поставитьСилуэт(mc, пакет);
            default -> { }
        }
    }

    private static void поставитьСилуэт(Minecraft mc, PlagueNetwork.Vision пакет) {
        убратьСилуэт(mc);
        Zombie з = EntityType.ZOMBIE.create(mc.level);
        if (з == null) return;
        з.setPos(пакет.x(), пакет.y(), пакет.z());
        з.setNoAi(true);
        з.setSilent(true);
        // Развернуть лицом к игроку: силуэт, стоящий спиной, не читается.
        з.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            mc.player.position());
        mc.level.putNonPlayerEntity(з.getId(), з);
        силуэт = з;
        силуэтДо = тик + пакет.тиков();
    }

    private static void убратьСилуэт(Minecraft mc) {
        if (силуэт != null && mc.level != null) {
            mc.level.removeEntity(силуэт.getId(),
                net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        силуэт = null;
    }

    /** Плотность темноты сейчас, 0..1. Читает `PlagueOverlay`. */
    public static float силаТемноты() {
        if (тик >= темнотаДо || темнотаВсего <= 0) return 0f;
        long осталось = темнотаДо - тик;
        // Гаснет резко, возвращается плавно — так это читается как
        // задутый факел, а не как плавная анимация интерфейса.
        float доля = (float) осталось / темнотаВсего;
        return Math.min(1f, доля * 1.6f);
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        тик++;
        if (силуэт != null && тик >= силуэтДо) убратьСилуэт(Minecraft.getInstance());
    }
}
```

- [ ] **Step 3: Прокинуть вызов через PlagueClientAccess**

В `PlagueClientAccess.java` добавить (в стиле соседних методов-мостиков):

```java
    public static void видение(PlagueNetwork.Vision пакет) {
        DreadClient.принять(пакет);
    }
```

- [ ] **Step 4: Подмешать темноту в оверлей**

В `PlagueOverlay.нарисовать`, там где считается итоговая плотность плёнки,
взять максимум с темнотой видения:

```java
        float плотность = Math.max(плотность, DreadClient.силаТемноты());
```

(переименовав локальную переменную по месту, если имя занято: плёнка
болезни и темнота видения — разные источники одной и той же плотности,
и побеждать должен больший).

- [ ] **Step 5: Выдавать видения из режиссёра**

В `DreadDirector.выдать`, ветка `ВИДЕНИЕ` — заменить временный кашель:

```java
            case ВИДЕНИЕ -> {
                видение(игрок);
                долина = PlagueConstants.DREAD_VALLEY_VISION;
                видения++;
            }
```

и добавить метод:

```java
    /** Одно случайное видение: темнота, сердцебиение или силуэт. */
    private static void видение(ServerPlayer игрок) {
        RandomSource случай = игрок.serverLevel().getRandom();
        int вид = случай.nextInt(3);
        int тиков = вид == DreadClientKinds.СИЛУЭТ ? 20 : 50;
        float[] точка = DreadMath.заСпиной(
            (float) игрок.getX(), (float) игрок.getY(), (float) игрок.getZ(),
            игрок.getYRot() + 110f, 8f + случай.nextFloat() * 6f);
        PacketDistributor.sendToPlayer(игрок, new PlagueNetwork.Vision(
            вид, тиков, точка[0], точка[1], точка[2]));
    }
```

Виды на сервере держим в маленьком классе, чтобы не тянуть клиентский
`DreadClient` в общий код — файл
`plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadClientKinds.java`:

```java
package dev.denthe.plaguecore.mc;

/** Номера видений. Общие для сервера и клиента, без клиентских классов. */
public final class DreadClientKinds {
    private DreadClientKinds() {}

    public static final int ТЕМНОТА = 0;
    public static final int СЕРДЦЕ = 1;
    public static final int СИЛУЭТ = 2;
}
```

и в `DreadClient` использовать эти же константы вместо своих.

- [ ] **Step 6: Собрать**

Run: `cd plaguecore && ./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/
git commit -m "Видения: темнота, сердцебиение и силуэт на краю зрения"
```

---

### Task 5: Наблюдатель

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/WatcherMath.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/WatcherMathTest.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/Watcher.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueEntities.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueState.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadDirector.java`

**Interfaces:**
- Consumes: `DreadMath.Уровень.ЯВЛЕНИЕ`, `PlagueState`, `MutatedZombie`
  (как образец регистрации сущности и рендера).
- Produces:
  - `WatcherMath.Решение` — `enum { СТОЯТЬ, ПОДОЙТИ, РАСТВОРИТЬСЯ, НАПАСТЬ }`
  - `static Решение решение(boolean наНегоСмотрят, double дистанция,
    boolean настоящий, double дистанцияРастворения)`
  - `PlagueState.watchers()` / `PlagueState.addWatcher()` — счётчик явлений
    за сессию, переживает перезапуск.
  - `Watcher.явить(ServerPlayer игрок)` — спавн у игрока.

- [ ] **Step 1: Тест на поведение**

Файл `plaguecore/src/test/java/dev/denthe/plaguecore/core/WatcherMathTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Поведение Наблюдателя построено на взгляде. Спек, раздел 3.
 */
class WatcherMathTest {

    @Test
    void подВзглядомСтоит() {
        assertEquals(WatcherMath.Решение.СТОЯТЬ,
            WatcherMath.решение(true, 30.0, false, 10.0));
    }

    @Test
    void безВзглядаПодходит() {
        assertEquals(WatcherMath.Решение.ПОДОЙТИ,
            WatcherMath.решение(false, 30.0, false, 10.0));
    }

    @Test
    void вблизиРастворяется() {
        assertEquals(WatcherMath.Решение.РАСТВОРИТЬСЯ,
            WatcherMath.решение(true, 8.0, false, 10.0));
    }

    @Test
    void настоящийВблизиНападает() {
        assertEquals(WatcherMath.Решение.НАПАСТЬ,
            WatcherMath.решение(true, 8.0, true, 10.0));
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `cd plaguecore && ./gradlew test --tests "*WatcherMathTest*"`
Expected: FAIL — `cannot find symbol: class WatcherMath`.

- [ ] **Step 3: Написать WatcherMath**

```java
package dev.denthe.plaguecore.core;

/**
 * Что Наблюдатель делает прямо сейчас. Спек раздел 3.
 *
 * Правило одно: под взглядом он неподвижен, без взгляда — ближе,
 * вплотную — исчезает. Первые два явления за сессию всегда кончаются
 * растворением, и только третье настоящее: игроки должны успеть решить,
 * что он безвреден, иначе настоящая атака не будет стоить ничего.
 */
public final class WatcherMath {
    private WatcherMath() {}

    public enum Решение { СТОЯТЬ, ПОДОЙТИ, РАСТВОРИТЬСЯ, НАПАСТЬ }

    public static Решение решение(boolean наНегоСмотрят, double дистанция,
                                  boolean настоящий, double дистанцияРастворения) {
        if (дистанция <= дистанцияРастворения) {
            return настоящий ? Решение.НАПАСТЬ : Решение.РАСТВОРИТЬСЯ;
        }
        return наНегоСмотрят ? Решение.СТОЯТЬ : Решение.ПОДОЙТИ;
    }
}
```

- [ ] **Step 4: Убедиться, что тесты проходят**

Run: `cd plaguecore && ./gradlew test --tests "*WatcherMathTest*"`
Expected: PASS, 4 теста.

- [ ] **Step 5: Сущность**

Файл `Watcher.java` — по образцу `MutatedZombie` (тот же способ
регистрации типа, атрибутов и рендерера). Отличия, которые обязаны быть:

- `Watcher extends Monster`, без стандартного `MeleeAttackGoal`, пока он
  не настоящий: поведением правит `WatcherMath` в `aiStep`;
- поле `boolean настоящий` — третье явление за сессию;
- `наНегоСмотрят`: скалярное произведение взгляда игрока и направления
  на Наблюдателя больше 0.5 **и** есть прямая видимость
  (`hasLineOfSight`);
- `РАСТВОРИТЬСЯ` — `discard()` без частиц, дропа и звука: исчез, и всё;
- `НАПАСТЬ` — включить `MeleeAttackGoal`, скорость 1.35, здоровье 40,
  урон в полтора раза больше зомби; дроп — `ArchiveRecordItem`;
- регистрация в `PlagueEntities` и рендерер в `PlagueEntityRenderers`
  с моделью `MutatedZombieModel` — своей модели не заводим.

- [ ] **Step 6: Счётчик явлений в PlagueState**

В `PlagueState` — поле `int watchers`, чтение/запись в NBT рядом с
остальными (`night`, `phase`), методы:

```java
    public int watchers() { return watchers; }

    public void addWatcher() {
        watchers++;
        setDirty();
    }
```

- [ ] **Step 7: Включить явления в режиссёре**

В `DreadDirector`:

```java
    private static boolean явлениеДоступно(ServerLevel уровень) {
        return PlagueState.get(уровень).watchers()
            < PlagueConstants.DREAD_WATCHERS_PER_SESSION;
    }
```

и ветка `ЯВЛЕНИЕ` вызывает `Watcher.явить(игрок)`. Условия спавна —
игрок один, вне города, ночь или Y ниже `DREAD_DEPTH_Y`; точка в 24–40
блоках, на твёрдой земле, вне текущего поля зрения.

- [ ] **Step 8: Собрать и прогнать всё**

Run: `cd plaguecore && ./gradlew build`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 9: Коммит**

```bash
git add plaguecore/src/
git commit -m "Наблюдатель: стоит под взглядом, подходит без него, третий настоящий"
```

---

### Task 6: Команды и раздел «Страх» в панели мастера

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/DreadCommands.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java`
- Modify: `lmpc_gmtools/src/main/java/dev/denthe/gmtools/GmPanelScreen.java`
- Modify: `lmpc_gmtools/CLAUDE.md` (раздел «Что сделано»)

**Interfaces:**
- Consumes: `DreadDirector.состояние`, `DreadDirector.выдать`,
  `DreadDirector.тишина`, `Watcher.явить`.
- Produces: команды
  `/plague dread info`, `/plague dread rustle <игрок>`,
  `/plague dread vision <игрок>`, `/plague dread watcher <игрок>`,
  `/plague dread silence <игрок> <минут>`,
  `/plague dread push <игрок> <сколько>`.

- [ ] **Step 1: Дерево команд**

`DreadCommands.java` — по образцу `PlagueCommands` (право 2,
`Commands.literal`, `EntityArgument.player()`). `info` печатает таблицу:
игрок, напряжение, сколько осталось долины, потрачено шорохов и видений
за час. Панель мастера читает именно её.

- [ ] **Step 2: Подключить в PlagueCommands**

```java
        корень.then(DreadCommands.ветка());
```

- [ ] **Step 3: Раздел в панели**

В `GmPanelScreen` — новый раздел «Страх» в левой колонке, по образцу
существующих: список игроков с напряжением, кнопки «шорох», «видение»,
«явление», «тишина 10 мин», «поднять». Каждая кнопка шлёт свою команду
через `ClientPacketListener.sendCommand`. Опасных действий здесь нет,
второй клик не нужен.

- [ ] **Step 4: Собрать оба мода**

Run: `cd plaguecore && ./gradlew build`
Run: `cd lmpc_gmtools && ./gradlew build`
Expected: BUILD SUCCESSFUL в обоих.

- [ ] **Step 5: Разложить джарники**

Собранные джарники — в три места, как заведено в проекте: `mods/`
репозитория, профиль Modrinth `LMPCCHUMA`, `mods/` сервера. Версии
клиента и сервера для `lmpc_gmtools` обязаны совпадать.

- [ ] **Step 6: Записать итог в спек**

В `docs/superpowers/specs/2026-09-17-horror-rezhissyor-design.md` — раздел
«Итог реализации»: что построено, где лежит, что ждёт живой проверки
(ритм, сила, частота — проверяются только на людях).

- [ ] **Step 7: Коммит**

```bash
git add plaguecore/src/ lmpc_gmtools/ docs/superpowers/specs/
git commit -m "Пульт страха: команды /plague dread и раздел в панели мастера"
```

---

## Порядок и что можно показывать владельцу

После задачи 3 подсистема уже играбельна: мир сам шуршит за спиной
с правильным ритмом. Это первая точка, где имеет смысл зайти на сервер
и покрутить числа в конфиге. Задачи 4–6 добавляют видения, Наблюдателя
и пульт поверх уже работающего ритма.
