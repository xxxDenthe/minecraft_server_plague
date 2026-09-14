# Интерфейс «Состояние здоровья» — план реализации

> **Для исполнителя:** ОБЯЗАТЕЛЬНЫЙ ПОДНАВЫК — `superpowers:subagent-driven-development`
> (рекомендуется) или `superpowers:executing-plans`. Шаги помечены чекбоксами
> (`- [ ]`) для отметки хода работы.

**Цель:** дать игроку экран и HUD, которыми он осматривает себя и соседа
субъективными словами, ни разу не показав ни одного числа болезни.

**Архитектура:** выбор текста — чистые функции в `core/Wellbeing`, под
обычным JUnit и под `CorePurityTest`. Всё остальное — клиентские классы
в `client/`, читающие уже существующие данные: стадию из пакета `Stage`,
здоровье и голод из ванильного игрока, класс и жажду через уже
написанные мосты рефлексии. Новой сети ровно два пакета, и оба только
под осмотр соседа.

**Стек:** Java 21, Minecraft 1.21.1, NeoForge 21.1.249, ModDevGradle,
JUnit 5, Python 3 с Pillow для генерации атласа значков.

**Спек:** `docs/superpowers/specs/2026-09-14-sostoyanie-zdorovya-design.md`

## Общие ограничения

Действуют в каждой задаче, повторять в каждой не будем:

- **Пакет `core` не знает о Minecraft.** Ни одного `import net.minecraft`
  в `src/main/java/dev/denthe/plaguecore/core/`. Это стережёт
  `CorePurityTest`, и ослаблять его нельзя.
- **Ни одной новой механики болезни.** Интерфейс только читает. Никаких
  новых счётчиков, стадий, эффектов и источников заражения.
- **Игроку не показывается** заражение, процент, стадия, номер и
  название стадии, пороги, скорость, источник, защита, коэффициенты,
  технические названия эффектов и их уровни, длительности эффектов.
  Это правило сильнее удобства.
- **Ни одной строки видимого текста в Java.** Всё через
  `Component.translatable` и ключи из `Wellbeing`.
- **Комментарии, документы и сообщения коммитов — на русском.**
  Имена классов, методов и ключей — как уже принято в моде:
  классы по-английски, поля и методы по-русски.
- **На выделенном сервере клиентские классы не грузятся.** Любой вход
  с общего кода в `client/` идёт только через `PlagueClientAccess`.
- **Тихий отказ вместо падения.** Нет `lmpc_classes` — нет Клирика;
  нет мода жажды — нет строки жажды. Ни в одном случае не падаем.
- **Каталог мода:** `plaguecore/`. Тесты запускаются оттуда:
  `./gradlew test`. Сборка: `./gradlew build`.
- **Коммит после каждой задачи**, с русским сообщением и хвостом
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

---

### Задача 1: `Wellbeing` — ступени самочувствия и части тела

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java`
- Создать: `plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java`

**Интерфейсы:**
- Потребляет: ничего.
- Отдаёт: `Wellbeing.СТУПЕНЕЙ` (int, = 5); `Wellbeing.Часть`
  (enum `HEAD, TORSO, ARMS, LEGS`); `Wellbeing.ступень(int стадия)` → int;
  `Wellbeing.общее(int ступень)` → String; `Wellbeing.общееКратко(int)` → String;
  `Wellbeing.часть(Часть, int ступень)` → String;
  `Wellbeing.чужоеОбщее(int ступень)` → String;
  `Wellbeing.чужаяЧасть(Часть, int ступень)` → String.

- [ ] **Шаг 1: написать падающий тест**

`plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Выбор субъективного текста. Спек интерфейса «Состояние здоровья»,
 * раздел 6.
 *
 * Тест стережёт главное правило: наружу уходят только ключи
 * локализации, и ни в одном из них нет ни стадии, ни заражения.
 */
class WellbeingTest {

    @Test
    void ступеньПовторяетСтадиюИНеВыходитЗаКрая() {
        assertEquals(0, Wellbeing.ступень(0));
        assertEquals(3, Wellbeing.ступень(3));
        assertEquals(4, Wellbeing.ступень(4));
        assertEquals(0, Wellbeing.ступень(-7), "мусор снизу прижимается к нулю");
        assertEquals(4, Wellbeing.ступень(99), "мусор сверху прижимается к краю");
    }

    @Test
    void укаждойСтупениСвойКлюч() {
        for (int a = 0; a < Wellbeing.СТУПЕНЕЙ; a++) {
            for (int b = a + 1; b < Wellbeing.СТУПЕНЕЙ; b++) {
                assertNotEquals(Wellbeing.общее(a), Wellbeing.общее(b));
                assertNotEquals(Wellbeing.общееКратко(a), Wellbeing.общееКратко(b));
            }
        }
    }

    @Test
    void укаждойЧастиСвойКлюч() {
        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            for (Wellbeing.Часть другая : Wellbeing.Часть.values()) {
                if (часть == другая) continue;
                assertNotEquals(Wellbeing.часть(часть, 2), Wellbeing.часть(другая, 2));
            }
        }
    }

    @Test
    void чужойОсмотрГоворитДругимиКлючами() {
        assertNotEquals(Wellbeing.общее(2), Wellbeing.чужоеОбщее(2));
        assertNotEquals(
            Wellbeing.часть(Wellbeing.Часть.ARMS, 2),
            Wellbeing.чужаяЧасть(Wellbeing.Часть.ARMS, 2));
    }

    @Test
    void вКлючахНетСловМеханики() {
        for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
            проверить(Wellbeing.общее(с));
            проверить(Wellbeing.общееКратко(с));
            проверить(Wellbeing.чужоеОбщее(с));
            for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
                проверить(Wellbeing.часть(часть, с));
                проверить(Wellbeing.чужаяЧасть(часть, с));
            }
        }
    }

    private static void проверить(String ключ) {
        assertFalse(ключ.contains("stage"), "в ключе не должно быть стадии: " + ключ);
        assertFalse(ключ.contains("infection"), "в ключе не должно быть заражения: " + ключ);
        assertTrue(ключ.startsWith("plaguecore.health."), "чужой корень ключа: " + ключ);
    }
}
```

- [ ] **Шаг 2: запустить тест и убедиться, что он падает**

Запустить из `plaguecore/`: `./gradlew test --tests "*WellbeingTest*"`
Ожидается: провал компиляции, `cannot find symbol: class Wellbeing`.

- [ ] **Шаг 3: минимальная реализация**

`plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java`:

```java
package dev.denthe.plaguecore.core;

import java.util.Locale;

/**
 * Что игрок чувствует и какими словами он об этом думает.
 * Спек интерфейса «Состояние здоровья», раздел 6.
 *
 * Класс не знает ни о Minecraft, ни о текстах: наружу уходят только
 * ключи локализации, а сами слова лежат в языковых файлах. Поэтому
 * вся система текстов проверяется обычным JUnit, без запуска игры,
 * и живёт под {@code CorePurityTest}.
 *
 * Главное правило спека: ни одно число болезни наружу не выходит.
 * Ступень самочувствия совпадает со стадией один в один намеренно —
 * своя шкала поверх серверной была бы второй механикой, а вторую
 * механику болезни заводить запрещено.
 */
public final class Wellbeing {
    private Wellbeing() {}

    /** Ступеней самочувствия: здоров, слегка нехорошо, нехорошо, плохо, край. */
    public static final int СТУПЕНЕЙ = 5;

    /** Общий корень всех ключей. Держит их в одном месте языкового файла. */
    private static final String КОРЕНЬ = "plaguecore.health.";

    /**
     * Части тела, у которых свой текст. Рук и ног по две, но говорят
     * они одно и то же: болезнь стороны не различает, а притворяться,
     * что различает, значит врать игроку.
     */
    public enum Часть { HEAD, TORSO, ARMS, LEGS }

    /** Ступень самочувствия по стадии болезни. Мусор прижимается к краям. */
    public static int ступень(int стадия) {
        if (стадия < 0) return 0;
        return Math.min(стадия, СТУПЕНЕЙ - 1);
    }

    /** Общее самочувствие на экране — одна-две фразы от первого лица. */
    public static String общее(int ступень) {
        return КОРЕНЬ + "overall." + ступень(ступень);
    }

    /** То же короче, для HUD: там длинная фраза не читается. */
    public static String общееКратко(int ступень) {
        return КОРЕНЬ + "hud." + ступень(ступень);
    }

    /** Что человек чувствует в этой части тела. */
    public static String часть(Часть часть, int ступень) {
        return КОРЕНЬ + "body." + имя(часть) + "." + ступень(ступень);
    }

    /** Как сосед выглядит со стороны. Это наблюдение, а не ощущение. */
    public static String чужоеОбщее(int ступень) {
        return КОРЕНЬ + "other.overall." + ступень(ступень);
    }

    /** Как со стороны выглядит часть тела соседа. */
    public static String чужаяЧасть(Часть часть, int ступень) {
        return КОРЕНЬ + "other.body." + имя(часть) + "." + ступень(ступень);
    }

    private static String имя(Часть часть) {
        return часть.name().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Шаг 4: запустить тест и убедиться, что он проходит**

Запустить: `./gradlew test --tests "*WellbeingTest*"`
Ожидается: PASS, пять тестов.

- [ ] **Шаг 5: проверить, что ядро осталось чистым**

Запустить: `./gradlew test --tests "*CorePurityTest*"`
Ожидается: PASS.

- [ ] **Шаг 6: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java
git commit -m "Ядро интерфейса здоровья: ступени самочувствия и части тела

Выбор текста — чистые функции над ключами локализации, без Minecraft
и без единого числа болезни наружу.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Задача 2: `Wellbeing` — голод, жажда и ощущения от эффектов

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java`

**Интерфейсы:**
- Потребляет: `Wellbeing` из задачи 1.
- Отдаёт: `Wellbeing.голод(int сытость)` → String;
  `Wellbeing.жажда(int уровень)` → String;
  `Wellbeing.ощущение(String идентификаторЭффекта)` → String или `null`.

- [ ] **Шаг 1: дописать падающие тесты**

Добавить в `WellbeingTest`:

```java
    @Test
    void голодРазбиваетсяНаЧетыреСтупени() {
        assertEquals(Wellbeing.голод(0), Wellbeing.голод(3));
        assertNotEquals(Wellbeing.голод(3), Wellbeing.голод(4));
        assertNotEquals(Wellbeing.голод(10), Wellbeing.голод(11));
        assertNotEquals(Wellbeing.голод(17), Wellbeing.голод(18));
        assertEquals(Wellbeing.голод(18), Wellbeing.голод(20));
        assertEquals(Wellbeing.голод(0), Wellbeing.голод(-5), "мусор снизу");
        assertEquals(Wellbeing.голод(20), Wellbeing.голод(99), "мусор сверху");
    }

    @Test
    void жаждаРазбиваетсяТакЖе() {
        assertEquals(Wellbeing.жажда(0), Wellbeing.жажда(3));
        assertNotEquals(Wellbeing.жажда(3), Wellbeing.жажда(4));
        assertEquals(Wellbeing.жажда(20), Wellbeing.жажда(99));
    }

    @Test
    void знакомыйЭффектПревращаетсяВОщущение() {
        assertEquals("plaguecore.health.feel.weakness",
            Wellbeing.ощущение("minecraft:weakness"));
        assertEquals("plaguecore.health.feel.water_breathing",
            Wellbeing.ощущение("minecraft:water_breathing"));
    }

    @Test
    void незнакомыйЭффектМолчит() {
        assertNull(Wellbeing.ощущение("somemod:quantum_flux"));
        assertNull(Wellbeing.ощущение(""));
        assertNull(Wellbeing.ощущение(null));
    }
```

- [ ] **Шаг 2: запустить тест и убедиться, что он падает**

Запустить: `./gradlew test --tests "*WellbeingTest*"`
Ожидается: провал компиляции, `cannot find symbol: method голод(int)`.

- [ ] **Шаг 3: реализация**

Дописать в `Wellbeing.java` (перед закрывающей скобкой класса, импорты
`java.util.Map` и `java.util.Map.entry` — вверх файла):

```java
    /**
     * Голод — четыре ступени по ванильной сытости 0..20.
     *
     * Коэффициенты болезни сюда не попадают принципиально: игрок
     * чувствует, что есть хочется чаще, а не что стадия добавляет
     * 0.04 истощения в секунду.
     */
    public static String голод(int сытость) {
        return КОРЕНЬ + "hunger." + четверть(сытость);
    }

    /** Жажда — та же шкала 0..20, что у мода жажды. */
    public static String жажда(int уровень) {
        return КОРЕНЬ + "thirst." + четверть(уровень);
    }

    /** 0..20 → ступень 0..3. Мусор прижимается к краям. */
    private static int четверть(int значение) {
        if (значение <= 3) return 0;
        if (значение <= 10) return 1;
        if (значение <= 17) return 2;
        return 3;
    }

    /**
     * Действующий эффект человеческими словами.
     *
     * Незнакомый эффект молчит, а не показывает свой идентификатор:
     * лучше промолчать, чем вывалить игроку «somemod:quantum_flux».
     * Ни названия, ни уровня, ни длительности — всё это техническое.
     *
     * @return ключ локализации или {@code null}, если эффект незнаком
     */
    public static String ощущение(String идентификатор) {
        if (идентификатор == null) return null;
        String хвост = ОЩУЩЕНИЯ.get(идентификатор);
        return хвост == null ? null : КОРЕНЬ + "feel." + хвост;
    }

    /**
     * Эффекты, которым есть что сказать телом. Список нарочно неполный:
     * сюда входит то, что действительно встречается в паке.
     */
    private static final Map<String, String> ОЩУЩЕНИЯ = Map.ofEntries(
        Map.entry("minecraft:weakness", "weakness"),
        Map.entry("minecraft:mining_fatigue", "mining_fatigue"),
        Map.entry("minecraft:slowness", "slowness"),
        Map.entry("minecraft:nausea", "nausea"),
        Map.entry("minecraft:hunger", "hunger"),
        Map.entry("minecraft:poison", "poison"),
        Map.entry("minecraft:wither", "wither"),
        Map.entry("minecraft:blindness", "blindness"),
        Map.entry("minecraft:darkness", "darkness"),
        Map.entry("minecraft:regeneration", "regeneration"),
        Map.entry("minecraft:speed", "speed"),
        Map.entry("minecraft:haste", "haste"),
        Map.entry("minecraft:strength", "strength"),
        Map.entry("minecraft:resistance", "resistance"),
        Map.entry("minecraft:fire_resistance", "fire_resistance"),
        Map.entry("minecraft:water_breathing", "water_breathing"),
        Map.entry("minecraft:night_vision", "night_vision"),
        Map.entry("minecraft:invisibility", "invisibility"),
        Map.entry("minecraft:jump_boost", "jump_boost"),
        Map.entry("minecraft:slow_falling", "slow_falling"),
        Map.entry("minecraft:absorption", "absorption"),
        Map.entry("minecraft:health_boost", "health_boost"),
        Map.entry("minecraft:saturation", "saturation"),
        Map.entry("minecraft:levitation", "levitation"),
        Map.entry("minecraft:glowing", "glowing"));
```

- [ ] **Шаг 4: запустить тесты и убедиться, что они проходят**

Запустить: `./gradlew test --tests "*WellbeingTest*" --tests "*CorePurityTest*"`
Ожидается: PASS, девять тестов.

- [ ] **Шаг 5: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java
git commit -m "Голод, жажда и эффекты человеческими словами

Незнакомый эффект молчит: лучше ничего, чем идентификатор в лицо.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Задача 3: тексты — русский и английский языковые файлы

**Файлы:**
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/en_us.json`
- Создать: `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`

**Интерфейсы:**
- Потребляет: ключи из `Wellbeing` (задачи 1 и 2).
- Отдаёт: полный набор строк; ключи вкладок, заголовка и чужих фраз
  одержимости, которыми пользуются задачи 9, 10 и 14.

Тексты пишутся от первого лица и звучат как мысли человека, а не как
отчёт. Плохо: «Состояние: немного плохое». Хорошо: «Кажется, сегодня я
не в лучшей форме». Это требование спека, а не вкусовщина.

- [ ] **Шаг 1: написать падающий тест на полноту**

`plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`:

```java
package dev.denthe.plaguecore;

import dev.denthe.plaguecore.core.Wellbeing;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Каждый ключ, который умеет выдать Wellbeing, обязан быть в обоих
 * языковых файлах. Без этого проверки игрок увидит голый ключ вида
 * plaguecore.health.body.arms.3 — и это худшее, что может случиться
 * с интерфейсом, который весь состоит из текста.
 *
 * Файлы читаются как текст, а не разбираются JSON-библиотекой: тянуть
 * зависимость ради поиска подстроки незачем.
 */
class LangCoverageTest {

    private static final Path RU =
        Path.of("src/main/resources/assets/plaguecore/lang/ru_ru.json");
    private static final Path EN =
        Path.of("src/main/resources/assets/plaguecore/lang/en_us.json");

    @Test
    void всеКлючиЕстьВОбоихЯзыках() throws IOException {
        String ru = Files.readString(RU, StandardCharsets.UTF_8);
        String en = Files.readString(EN, StandardCharsets.UTF_8);

        List<String> пропущены = new ArrayList<>();
        for (String ключ : всеКлючи()) {
            if (!ru.contains('"' + ключ + '"')) пропущены.add("ru: " + ключ);
            if (!en.contains('"' + ключ + '"')) пропущены.add("en: " + ключ);
        }
        assertTrue(пропущены.isEmpty(), "нет перевода: " + пропущены);
    }

    private static List<String> всеКлючи() {
        List<String> ключи = new ArrayList<>();
        for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
            ключи.add(Wellbeing.общее(с));
            ключи.add(Wellbeing.общееКратко(с));
            ключи.add(Wellbeing.чужоеОбщее(с));
            for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
                ключи.add(Wellbeing.часть(часть, с));
                ключи.add(Wellbeing.чужаяЧасть(часть, с));
            }
        }
        for (int ч = 0; ч <= 20; ч++) {
            ключи.add(Wellbeing.голод(ч));
            ключи.add(Wellbeing.жажда(ч));
        }
        ключи.add("plaguecore.health.title");
        ключи.add("plaguecore.health.tab.state");
        ключи.add("plaguecore.health.tab.body");
        ключи.add("plaguecore.health.tab.feel");
        ключи.add("plaguecore.health.tab.memory");
        ключи.add("plaguecore.health.hint.pick");
        ключи.add("plaguecore.health.feel.none");
        ключи.add("plaguecore.health.memory.none");
        ключи.add("key.plaguecore.health");
        ключи.add("key.categories.plaguecore");
        return ключи;
    }
}
```

- [ ] **Шаг 2: запустить тест и убедиться, что он падает**

Запустить: `./gradlew test --tests "*LangCoverageTest*"`
Ожидается: FAIL со списком непереведённых ключей.

- [ ] **Шаг 3: дописать русские строки**

Вставить в `ru_ru.json`, сохраняя алфавитный порядок ключей, принятый
в файле:

```json
  "key.categories.plaguecore": "Чума",
  "key.plaguecore.health": "Состояние здоровья",

  "plaguecore.health.title": "Состояние здоровья",
  "plaguecore.health.tab.state": "Самочувствие",
  "plaguecore.health.tab.body": "Тело",
  "plaguecore.health.tab.feel": "Ощущения",
  "plaguecore.health.tab.memory": "Память",
  "plaguecore.health.hint.pick": "Прислушаться к себе можно, нажав на тело.",
  "plaguecore.health.feel.none": "Ничего особенного я в себе не замечаю.",
  "plaguecore.health.memory.none": "Я пока ничего за собой не заметил.",

  "plaguecore.health.overall.0": "Кажется, я здоров.",
  "plaguecore.health.overall.1": "Сегодня я чувствую себя немного хуже обычного.",
  "plaguecore.health.overall.2": "Мне что-то нехорошо, и я не понимаю почему.",
  "plaguecore.health.overall.3": "Я чувствую себя очень плохо.",
  "plaguecore.health.overall.4": "Со мной происходит что-то ужасное.",

  "plaguecore.health.hud.0": "Я в порядке",
  "plaguecore.health.hud.1": "Что-то я неважно себя чувствую",
  "plaguecore.health.hud.2": "Мне нехорошо",
  "plaguecore.health.hud.3": "Мне очень плохо",
  "plaguecore.health.hud.4": "Я больше не понимаю, что со мной",

  "plaguecore.health.body.head.0": "Голова ясная. Думается легко.",
  "plaguecore.health.body.head.1": "Кажется, я немного устал. Мысли даются тяжелее.",
  "plaguecore.health.body.head.2": "В голове какая-то тяжесть. Мне трудно сосредоточиться.",
  "plaguecore.health.body.head.3": "Мысли стали странно медленными. Я с трудом понимаю, что со мной происходит.",
  "plaguecore.health.body.head.4": "Я больше не уверен, что полностью управляю собой.",

  "plaguecore.health.body.torso.0": "Дышится легко, ничего не беспокоит.",
  "plaguecore.health.body.torso.1": "Иногда становится тяжело вдохнуть полной грудью.",
  "plaguecore.health.body.torso.2": "В груди тяжесть, и меня то и дело тянет кашлять.",
  "plaguecore.health.body.torso.3": "Каждый вдох даётся с трудом. Кашель рвёт грудь.",
  "plaguecore.health.body.torso.4": "Внутри будто что-то шевелится само по себе.",

  "plaguecore.health.body.arms.0": "Руки слушаются как всегда.",
  "plaguecore.health.body.arms.1": "Руки кажутся чуть тяжелее обычного.",
  "plaguecore.health.body.arms.2": "В руках слабость. Работа даётся тяжелее.",
  "plaguecore.health.body.arms.3": "Руки почти не держат. Инструмент валится сам.",
  "plaguecore.health.body.arms.4": "Иногда руки делают не то, что я им велю.",

  "plaguecore.health.body.legs.0": "Ноги крепкие, идти легко.",
  "plaguecore.health.body.legs.1": "В ногах появилась какая-то усталость.",
  "plaguecore.health.body.legs.2": "Ноги будто стали тяжелее. Бежать не выходит.",
  "plaguecore.health.body.legs.3": "Ноги едва держат меня. Каждый шаг — усилие.",
  "plaguecore.health.body.legs.4": "Я не помню, как оказался там, куда они меня принесли.",

  "plaguecore.health.other.overall.0": "Он выглядит здоровым.",
  "plaguecore.health.other.overall.1": "Он выглядит немного уставшим.",
  "plaguecore.health.other.overall.2": "Он выглядит нехорошо.",
  "plaguecore.health.other.overall.3": "Ему явно очень тяжело.",
  "plaguecore.health.other.overall.4": "На него страшно смотреть.",

  "plaguecore.health.other.body.head.0": "Взгляд у него ясный.",
  "plaguecore.health.other.body.head.1": "Он выглядит рассеянным.",
  "plaguecore.health.other.body.head.2": "Он смотрит мимо и отвечает не сразу.",
  "plaguecore.health.other.body.head.3": "Он будто не совсем здесь.",
  "plaguecore.health.other.body.head.4": "Он смотрит так, будто внутри кто-то другой.",

  "plaguecore.health.other.body.torso.0": "Дышит он ровно.",
  "plaguecore.health.other.body.torso.1": "Дыхание у него чуть сбитое.",
  "plaguecore.health.other.body.torso.2": "Он тяжело дышит и покашливает.",
  "plaguecore.health.other.body.torso.3": "Его выламывает кашлем.",
  "plaguecore.health.other.body.torso.4": "Грудь у него ходит ходуном, и звук этот нехороший.",

  "plaguecore.health.other.body.arms.0": "Руки у него твёрдые.",
  "plaguecore.health.other.body.arms.1": "Движения у него чуть вялые.",
  "plaguecore.health.other.body.arms.2": "Его руки выглядят слабыми.",
  "plaguecore.health.other.body.arms.3": "Он с трудом удерживает то, что берёт.",
  "plaguecore.health.other.body.arms.4": "Руки у него дёргаются сами.",

  "plaguecore.health.other.body.legs.0": "Держится он твёрдо.",
  "plaguecore.health.other.body.legs.1": "Он слегка припадает на ходу.",
  "plaguecore.health.other.body.legs.2": "Ходит он тяжело.",
  "plaguecore.health.other.body.legs.3": "Он еле переставляет ноги.",
  "plaguecore.health.other.body.legs.4": "Он движется рывками, будто его ведут.",

  "plaguecore.health.hunger.0": "Я умираю с голоду.",
  "plaguecore.health.hunger.1": "Мне очень хочется есть.",
  "plaguecore.health.hunger.2": "Есть хочется чаще обычного.",
  "plaguecore.health.hunger.3": "Есть пока не хочется.",

  "plaguecore.health.thirst.0": "Во рту пересохло совсем.",
  "plaguecore.health.thirst.1": "Мне всё время хочется пить.",
  "plaguecore.health.thirst.2": "Пить хочется чаще обычного.",
  "plaguecore.health.thirst.3": "Пить не хочется.",

  "plaguecore.health.feel.weakness": "Руки кажутся тяжелее обычного.",
  "plaguecore.health.feel.mining_fatigue": "Работа даётся тяжелее.",
  "plaguecore.health.feel.slowness": "Мне тяжело двигаться.",
  "plaguecore.health.feel.nausea": "Меня мутит, и всё плывёт перед глазами.",
  "plaguecore.health.feel.hunger": "Есть хочется чаще обычного.",
  "plaguecore.health.feel.poison": "Внутри жжёт.",
  "plaguecore.health.feel.wither": "Из меня будто уходят силы.",
  "plaguecore.health.feel.blindness": "Я почти ничего не вижу.",
  "plaguecore.health.feel.darkness": "Темнота будто липнет к глазам.",
  "plaguecore.health.feel.regeneration": "Мне становится легче с каждой минутой.",
  "plaguecore.health.feel.speed": "Я двигаюсь быстрее обычного.",
  "plaguecore.health.feel.haste": "Работа идёт на удивление споро.",
  "plaguecore.health.feel.strength": "Я чувствую в себе силу.",
  "plaguecore.health.feel.resistance": "Удары почти не чувствуются.",
  "plaguecore.health.feel.fire_resistance": "Жар мне сейчас не страшен.",
  "plaguecore.health.feel.water_breathing": "Такое чувство, что я могу очень долго не дышать.",
  "plaguecore.health.feel.night_vision": "В темноте я вижу неожиданно ясно.",
  "plaguecore.health.feel.invisibility": "Меня будто не замечают.",
  "plaguecore.health.feel.jump_boost": "Ноги пружинят сами.",
  "plaguecore.health.feel.slow_falling": "Падаю я почему-то мягко.",
  "plaguecore.health.feel.absorption": "Что-то держит удар за меня.",
  "plaguecore.health.feel.health_boost": "Сил во мне сейчас больше обычного.",
  "plaguecore.health.feel.saturation": "Я сыт надолго.",
  "plaguecore.health.feel.levitation": "Земля уходит из-под ног.",
  "plaguecore.health.feel.glowing": "От меня будто исходит свет."
```

- [ ] **Шаг 4: дописать английские строки**

Те же ключи в `en_us.json`, теми же смыслами, от первого лица. Начало
списка для примера, остальные — по той же логике:

```json
  "key.categories.plaguecore": "Plague",
  "key.plaguecore.health": "Health status",

  "plaguecore.health.title": "Health status",
  "plaguecore.health.tab.state": "How I feel",
  "plaguecore.health.tab.body": "Body",
  "plaguecore.health.tab.feel": "Sensations",
  "plaguecore.health.tab.memory": "Memory",
  "plaguecore.health.hint.pick": "Click on the body to listen to yourself.",
  "plaguecore.health.feel.none": "I notice nothing unusual about myself.",
  "plaguecore.health.memory.none": "I haven't noticed anything about myself yet.",

  "plaguecore.health.overall.0": "I think I'm fine.",
  "plaguecore.health.overall.1": "I feel a little worse than usual today.",
  "plaguecore.health.overall.2": "Something is wrong with me, and I don't know what.",
  "plaguecore.health.overall.3": "I feel very bad.",
  "plaguecore.health.overall.4": "Something terrible is happening to me."
```

- [ ] **Шаг 5: запустить тест и убедиться, что он проходит**

Запустить: `./gradlew test --tests "*LangCoverageTest*"`
Ожидается: PASS. Если падает — в списке провала стоят ровно те ключи,
которых не хватает; дописать их.

- [ ] **Шаг 6: коммит**

```bash
git add plaguecore/src/main/resources/assets/plaguecore/lang \
        plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java
git commit -m "Тексты интерфейса здоровья на двух языках

Тест стережёт полноту: голый ключ вместо фразы — худшее, что может
случиться с интерфейсом, который весь состоит из текста.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Задача 4: мосты к соседним модам — открыть на чтение

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/ClassBridge.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/ThirstBridge.java`

**Интерфейсы:**
- Потребляет: ничего нового.
- Отдаёт: `ClassBridge` и `ThirstBridge` становятся `public`;
  `ClassBridge.класс(Player)` → String (`"NONE"`, если мода нет);
  `ClassBridge.клирик(Player)` → boolean;
  `ThirstBridge.уровень(Player)` → int 0..20, или `-1`, если мода жажды нет.

Второй пары мостов не заводим: эти уже написаны, уже работают через
рефлексию и уже умеют тихо отказывать. Оба читаются и на клиенте:
вложение классов синкается с `lmpc_classes` 0.6.0, а мод жажды сам
рисует по своему вложению HUD, значит у клиента оно есть.

- [ ] **Шаг 1: открыть `ClassBridge` и добавить чтение класса**

В `ClassBridge.java` заменить объявление класса на публичное и добавить
два метода; существующий `летописец` переписать через новый `класс`,
чтобы строка `"CHRONICLER"` не жила в файле дважды:

```java
public final class ClassBridge {
```

```java
    /**
     * Класс игрока строкой: NONE, CLERIC, SMITH, FARMER, CHRONICLER.
     * Без `lmpc_classes` — всегда NONE, и это не ошибка, а отсутствие мода.
     *
     * Работает и на клиенте: вложение класса синкается игроку с 0.6.0.
     */
    public static String класс(Player игрок) {
        инициализировать();
        if (!доступен) return "NONE";
        try {
            Object результат = методКласс.invoke(null, игрок);
            return результат instanceof String строка ? строка : "NONE";
        } catch (ReflectiveOperationException e) {
            return "NONE";
        }
    }

    /** Клирик ли игрок. Ему интерфейс здоровья говорит чуть больше. */
    public static boolean клирик(Player игрок) {
        return "CLERIC".equals(класс(игрок));
    }
```

```java
    static boolean летописец(Player игрок) {
        return "CHRONICLER".equals(класс(игрок));
    }
```

- [ ] **Шаг 2: открыть `ThirstBridge` и добавить чтение уровня**

В `ThirstBridge.java` сделать класс публичным, добавить поле метода
и сам метод. Имя метода взято из джарника пака
(`ThirstWasTaken-1.21.1-2.1.5-nojade.jar`, интерфейс
`dev.ghen.thirst.foundation.common.capability.IThirst`): `getThirst()`
возвращает `int` в той же шкале 0..20, что и ванильный голод.

```java
public final class ThirstBridge {
```

Рядом с `методТратить`:

```java
    private static Method методУровень;
```

В `инициализировать()`, сразу после получения `методТратить`:

```java
            методУровень = Class
                .forName("dev.ghen.thirst.foundation.common.capability.IThirst")
                .getMethod("getThirst");
```

И сам метод:

```java
    /**
     * Уровень жажды 0..20. {@code -1} — мода жажды в сборке нет,
     * и тогда интерфейс просто не рисует строку жажды: пустой или
     * сломанный элемент показывать нельзя.
     */
    public static int уровень(Player игрок) {
        инициализировать();
        if (!доступен) return -1;
        try {
            Object жажда = игрок.getData(тип);
            if (жажда == null) return -1;
            Object результат = методУровень.invoke(жажда);
            return результат instanceof Integer число ? число : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
```

- [ ] **Шаг 3: собрать и убедиться, что старые вызовы не сломались**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL, все прежние тесты проходят.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/ClassBridge.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/ThirstBridge.java
git commit -m "Мосты классов и жажды открыты на чтение"
```

---

### Задача 5: атлас значков

**Файлы:**
- Создать: `tools/health-gfx/icons.py`
- Создать: `tools/health-gfx/ЧИТАЙ_МЕНЯ.md`
- Создать генератором: `plaguecore/src/main/resources/assets/plaguecore/textures/gui/health_icons.png`

**Интерфейсы:**
- Потребляет: ничего.
- Отдаёт: текстуру `plaguecore:textures/gui/health_icons.png`, 64 × 16.
  Ячейки вкладок 12 × 12 по X = 0, 12, 24, 36, Y = 0 — «Самочувствие»,
  «Тело», «Ощущения», «Память». Капля жажды 7 × 9 по X = 48, Y = 0.

Сердца и значок голода берутся ванильные (`hud/heart/full`,
`hud/heart/half`, `hud/heart/container`, `hud/food_full`) — своих рисовать
незачем, они уже в игре и уже в стиле. Своим остаётся только то, чего
в ванили нет.

Графика рисуется скриптом, а не руками: так её можно перекрасить одной
строкой, и это уже принятый в проекте порядок (`tools/zapis-gfx`).
Плавных переходов быть не должно — только плоские тона, курс проекта
на пиксельную приглушённую графику.

- [ ] **Шаг 1: написать генератор**

`tools/health-gfx/icons.py`:

```python
# -*- coding: utf-8 -*-
"""Значки интерфейса «Состояние здоровья»: четыре вкладки и капля жажды.

Сердца и голод берутся ванильные, поэтому здесь только то, чего в игре
нет. Палитра тёмная и серая, по правилу палитры чумы; лилового здесь
нет вовсе — в проекте это редкий акцент, а не цвет интерфейса.
"""
from PIL import Image

ЛИСТ = (64, 16)
ЯЧЕЙКА = 12

СВЕТ = (208, 208, 200, 255)
ТЕНЬ = (120, 124, 116, 255)
ВОДА = (110, 140, 156, 255)
ВОДА_ТЕНЬ = (74, 98, 112, 255)
ПУСТО = (0, 0, 0, 0)


def точки(px, x0, y0, узор, цвет):
    """Узор — список строк; решётка значит закрашенный пиксель."""
    for dy, строка in enumerate(узор):
        for dx, знак in enumerate(строка):
            if знак == "#":
                px[x0 + dx, y0 + dy] = цвет


САМОЧУВСТВИЕ = [
    "  ####  ",
    "  #  #  ",
    "  ####  ",
    " ###### ",
    " # ## # ",
    "   ##   ",
    "  #  #  ",
    "  #  #  ",
]

ТЕЛО = [
    "  ####  ",
    "  ####  ",
    " ###### ",
    "####### ",
    " ###### ",
    "  ####  ",
    "  #  #  ",
    "  #  #  ",
]

ОЩУЩЕНИЯ = [
    "        ",
    " ##  ## ",
    "#  ##  #",
    "        ",
    " ##  ## ",
    "#  ##  #",
    "        ",
    "        ",
]

ПАМЯТЬ = [
    " ###### ",
    " #    # ",
    " # ## # ",
    " #    # ",
    " # ## # ",
    " #    # ",
    " # ## # ",
    " ###### ",
]

КАПЛЯ = [
    "   #   ",
    "  ###  ",
    "  ###  ",
    " ##### ",
    "#######",
    "#######",
    " ##### ",
    "  ###  ",
    "       ",
]


def значок(px, индекс, узор):
    """Значок вкладки: тень на пиксель ниже, сверху сам рисунок."""
    x0 = индекс * ЯЧЕЙКА + 2
    точки(px, x0, 3, узор, ТЕНЬ)
    точки(px, x0, 2, узор, СВЕТ)


def собрать():
    im = Image.new("RGBA", ЛИСТ, ПУСТО)
    px = im.load()

    значок(px, 0, САМОЧУВСТВИЕ)
    значок(px, 1, ТЕЛО)
    значок(px, 2, ОЩУЩЕНИЯ)
    значок(px, 3, ПАМЯТЬ)

    точки(px, 49, 1, КАПЛЯ, ВОДА_ТЕНЬ)
    точки(px, 49, 0, КАПЛЯ, ВОДА)

    return im


if __name__ == "__main__":
    путь = ("../../plaguecore/src/main/resources/assets/plaguecore/"
            "textures/gui/health_icons.png")
    собрать().save(путь)
    print("записано:", путь)
```

- [ ] **Шаг 2: запустить генератор**

```bash
cd tools/health-gfx && python icons.py
```
Ожидается: строка «записано: …/health_icons.png» и файл на месте.

- [ ] **Шаг 3: проверить размер**

```bash
python -c "from PIL import Image; im=Image.open('plaguecore/src/main/resources/assets/plaguecore/textures/gui/health_icons.png'); print(im.size, im.mode)"
```
Ожидается: `(64, 16) RGBA`.

- [ ] **Шаг 4: написать `tools/health-gfx/ЧИТАЙ_МЕНЯ.md`**

Содержание: одна команда запуска, раскладка листа и четыре правила,
которые нельзя нарушать при правке:

1. **Координаты жёсткие** — `HealthScreen` блитит ячейки по этим числам,
   двигать значки в листе значит править и экран.
2. **Плавных переходов быть не должно** — только плоские тона.
3. **Сердца и голод не рисуем** — берутся ванильные спрайты
   `hud/heart/*` и `hud/food_full`; своя копия ванильного значка
   разойдётся с ресурспаком игрока.
4. **Лилового здесь нет** — в проекте это редкий акцент, а не цвет
   интерфейса.

- [ ] **Шаг 5: коммит**

```bash
git add tools/health-gfx \
        plaguecore/src/main/resources/assets/plaguecore/textures/gui/health_icons.png
git commit -m "Значки интерфейса здоровья и их генератор"
```

---

### Задача 6: `HealthSense` — кэш самочувствия

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthSense.java`

**Интерфейсы:**
- Потребляет: `Wellbeing` (задачи 1–2), `ClassBridge.клирик`,
  `ThirstBridge.уровень` (задача 4), `PlagueClientAccess.стадия()`.
- Отдаёт: `HealthSense.ступень()` → int; `HealthSense.клирик()` → boolean;
  `HealthSense.общее()` → `Component`; `HealthSense.общееКратко()` → `Component`;
  `HealthSense.голод()` → `Component`; `HealthSense.жажда()` → `Component`
  или `null`; `HealthSense.ощущения()` → `List<Component>`;
  `HealthSense.часть(Wellbeing.Часть)` → `Component`.

Считается раз в десять тиков, а не каждый кадр: в `render` не должно
создаваться ни строк, ни объектов — прямое требование спека
о производительности.

- [ ] **Шаг 1: написать класс**

`plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthSense.java`:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Wellbeing;
import dev.denthe.plaguecore.mc.ClassBridge;
import dev.denthe.plaguecore.mc.ThirstBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Что человек чувствует прямо сейчас — собранное и готовое к рисованию.
 * Спек интерфейса «Состояние здоровья», разделы 4 и 14.
 *
 * Ничего не спрашивает у сервера: стадия уже приходит пакетом Stage,
 * здоровье, голод и эффекты лежат у клиента, класс и жажда читаются
 * мостами рефлексии. Для осмотра себя новой сети не нужно вовсе.
 *
 * Пересчёт раз в десять тиков, готовые Component лежат полями. Экран
 * и HUD рисуются каждый кадр, и собирать там переводы заново значило бы
 * тысячи объектов в секунду ради текста, который меняется раз в минуту.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthSense {
    private HealthSense() {}

    /** Как часто пересчитываем, тиков. Полсекунды — глазу хватает. */
    private static final int ПЕРИОД = 10;

    private static int ступень;
    private static boolean клирик;
    private static Component общее = Component.empty();
    private static Component общееКратко = Component.empty();
    private static Component голод = Component.empty();
    private static Component жажда;                       // null — мода жажды нет
    private static List<Component> ощущения = List.of();
    private static final Component[] части = new Component[Wellbeing.Часть.values().length];

    public static int ступень() { return ступень; }
    public static boolean клирик() { return клирик; }
    public static Component общее() { return общее; }
    public static Component общееКратко() { return общееКратко; }
    public static Component голод() { return голод; }

    /** {@code null}, если мода жажды в сборке нет: строку тогда не рисуем вовсе. */
    public static Component жажда() { return жажда; }

    public static List<Component> ощущения() { return ощущения; }

    public static Component часть(Wellbeing.Часть часть) {
        Component готово = части[часть.ordinal()];
        return готово == null ? Component.empty() : готово;
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer игрок = mc.player;
        if (игрок == null) return;
        if (игрок.tickCount % ПЕРИОД != 0) return;
        пересчитать(игрок);
    }

    private static void пересчитать(LocalPlayer игрок) {
        ступень = Wellbeing.ступень(PlagueClientAccess.стадия());
        клирик = ClassBridge.клирик(игрок);

        общее = Component.translatable(Wellbeing.общее(ступень));
        общееКратко = Component.translatable(Wellbeing.общееКратко(ступень));
        голод = Component.translatable(Wellbeing.голод(игрок.getFoodData().getFoodLevel()));

        int вода = ThirstBridge.уровень(игрок);
        жажда = вода < 0 ? null : Component.translatable(Wellbeing.жажда(вода));

        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            части[часть.ordinal()] = Component.translatable(Wellbeing.часть(часть, ступень));
        }

        ощущения = собратьОщущения(игрок);
    }

    /**
     * Эффекты человеческими словами. Незнакомые пропускаются молча,
     * уровень и длительность не показываются — они технические.
     */
    private static List<Component> собратьОщущения(LocalPlayer игрок) {
        List<Component> список = new ArrayList<>();
        for (MobEffectInstance эффект : игрок.getActiveEffects()) {
            String идентификатор = BuiltInRegistries.MOB_EFFECT
                .getKey(эффект.getEffect().value()).toString();
            String ключ = Wellbeing.ощущение(идентификатор);
            if (ключ != null) список.add(Component.translatable(ключ));
        }
        return List.copyOf(список);
    }
}
```

- [ ] **Шаг 2: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 3: убедиться, что общий код не тянет клиент**

```bash
grep -rn "plaguecore.client" plaguecore/src/main/java/dev/denthe/plaguecore/mc/ | grep -v PlagueClientAccess
```
Ожидается: пусто. Единственный вход с общего кода в клиентский — через
`PlagueClientAccess`, как заведено в моде.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthSense.java
git commit -m "Кэш самочувствия на клиенте"
```

---

### Задача 7: клавиша `Y` и пустой экран

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthKeys.java`
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`

**Интерфейсы:**
- Потребляет: `HealthSense` (задача 6), ключи `key.plaguecore.health`
  и `key.categories.plaguecore` (задача 3).
- Отдаёт: `HealthKeys.КЛАВИША` (`KeyMapping`);
  `HealthScreen.открытьСвой()`; защищённые поля `левый`, `верхний`,
  `ШИРИНА`, `ВЫСОТА`, `ФОН`, `РАМКА`, `ТЕКСТ`, `ТУСКЛЫЙ` и метод
  `панель(GuiGraphics)`, которыми пользуются задачи 8–14.

- [ ] **Шаг 1: регистрация клавиши**

`plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthKeys.java`:

```java
package dev.denthe.plaguecore.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиша «Состояние здоровья». Спек, раздел 12.
 *
 * Y свободна во всём паке — проверено по образцу настроек
 * `launcher/pack-config/config/lmpc-default-options.txt`. Занят только
 * Ctrl+Y отменой в mapwright, а это другой экран.
 *
 * Клавиша регистрируется обычным способом NeoForge и переназначается
 * в стандартных настройках управления: жёстко прибитая клавиша — это
 * гарантированная жалоба от того, у кого её нет на клавиатуре.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthKeys {
    private HealthKeys() {}

    public static final KeyMapping КЛАВИША = new KeyMapping(
        "key.plaguecore.health",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        "key.categories.plaguecore");

    // bus не указываем: в 21.1 шина определяется по типу события,
    // а RegisterKeyMappingsEvent — модовое
    @SubscribeEvent
    public static void зарегистрировать(RegisterKeyMappingsEvent событие) {
        событие.register(КЛАВИША);
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (КЛАВИША.consumeClick()) {
            HealthScreen.открытьСвой();
        }
    }
}
```

- [ ] **Шаг 2: пустой экран**

`plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`:

```java
package dev.denthe.plaguecore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Экран «Состояние здоровья»: человек осматривает сам себя.
 * Спек интерфейса, раздел 7.
 *
 * Экран ничего не решает и ничего не спрашивает у сервера — он рисует
 * то, что собрал {@link HealthSense}. Ни одного числа болезни наружу
 * не выходит: ни заражения, ни стадии, ни порогов.
 */
public class HealthScreen extends Screen {

    /** Размер панели. Сундук — 176 × 166; здесь шире, текст длинный. */
    protected static final int ШИРИНА = 248, ВЫСОТА = 166;

    protected static final int ФОН = 0xFF101410;
    protected static final int РАМКА = 0xFF4A4A4A;
    protected static final int ТЕКСТ = 0xFFE0E0E0;
    protected static final int ТУСКЛЫЙ = 0xFF909090;

    protected int левый, верхний;

    protected HealthScreen() {
        super(Component.translatable("plaguecore.health.title"));
    }

    /** Открыть осмотр самого себя. */
    public static void открытьСвой() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen());
    }

    /**
     * Игру не останавливаем. Сервер сессионный, пауза там всё равно
     * не работает, а стоять с открытым экраном посреди гнили должно
     * быть опасно.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        левый = (width - ШИРИНА) / 2;
        верхний = (height - ВЫСОТА) / 2;
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        renderBackground(графика, мышьX, мышьY, кадр);
        панель(графика);
        графика.drawString(font, title, левый + 8, верхний + 8, ТЕКСТ, false);
        super.render(графика, мышьX, мышьY, кадр);
    }

    /** Тёмная панель с тонкой рамкой. Палитра — от админского экрана карты. */
    protected void панель(GuiGraphics графика) {
        int x = левый, y = верхний;
        графика.fill(x - 1, y - 1, x + ШИРИНА + 1, y + ВЫСОТА + 1, РАМКА);
        графика.fill(x, y, x + ШИРИНА, y + ВЫСОТА, ФОН);
    }
}
```

`RegisterKeyMappingsEvent` — модовое событие, поэтому `@EventBusSubscriber`
пишется без `bus`, как уже сделано в `PlagueNetwork`. `ClientTickEvent.Post`
из того же класса ловится игровой шиной; обе шины у одного подписчика
уживаются, это штатный случай в 21.1.

- [ ] **Шаг 3: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 4: живая проверка**

Запустить: `./gradlew runClient`
Проверить:
1. в настройках управления есть категория «Чума», в ней «Состояние
   здоровья» на клавише Y;
2. по Y открывается тёмная панель с заголовком;
3. по Escape панель закрывается;
4. игра за экраном продолжает идти — мобы двигаются, время течёт;
5. клавиша переназначается и после переназначения работает.

- [ ] **Шаг 5: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthKeys.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java
git commit -m "Клавиша Y и пустая панель состояния здоровья"
```

---

### Задача 8: модель тела и попадание по частям

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`

**Интерфейсы:**
- Потребляет: поля и `панель()` из задачи 7, `Wellbeing.Часть` из задачи 1.
- Отдаёт: защищённое поле `выбрана` (`Wellbeing.Часть`, может быть `null`);
  `LivingEntity цель()`; методы `нарисоватьМодель(GuiGraphics)`,
  `частьПод(int, int)` → `Wellbeing.Часть` или `null`,
  `прямоугольники(Wellbeing.Часть)` → `int[][]`. Ими пользуются
  задачи 9, 12 и 14.

Модель рисуется **лицом к нам, без поворота за мышью**. Ванильный
`renderEntityInInventoryFollowsMouse` крутит тело вслед за курсором — и
тогда область руки уезжает из-под самой руки, попасть по ней нельзя.
Берётся `renderEntityInInventoryFollowsAngle` с нулевыми углами.

Парные части подсвечиваются парой: текст у левой и правой руки один,
значит и уголки встают сразу на обе. Иначе человек видит выделенной
одну руку, а читает описание про обе.

- [ ] **Шаг 1: добавить геометрию и рисование модели**

Дописать в `HealthScreen` (импорты
`net.minecraft.client.gui.screens.inventory.InventoryScreen`,
`net.minecraft.world.entity.LivingEntity`,
`dev.denthe.plaguecore.core.Wellbeing` — вверх файла):

```java
    /** Окно модели внутри панели. */
    protected static final int МОДЕЛЬ_X = 12, МОДЕЛЬ_Y = 22;
    protected static final int МОДЕЛЬ_Ш = 64, МОДЕЛЬ_В = 132;

    /** Масштаб фигуры. Подбирался глазом: 44 — фигура занимает окно почти целиком. */
    protected static final int МАСШТАБ = 44;

    /** Сдвиг фигуры вверх, тот же, что у ванильного инвентаря. */
    protected static final float СДВИГ = 0.0625f;

    /**
     * Границы частей тела по высоте фигуры, в блоках от подошв.
     * Модель игрока — 32 пикселя, сжатых рендером на 0.9375, то есть
     * 1.875 блока: ноги 12 пикселей, туловище 12, голова 8.
     */
    protected static final float НОГИ_ВЕРХ = 0.703f;
    protected static final float ТУЛОВИЩЕ_ВЕРХ = 1.406f;
    protected static final float ГОЛОВА_ВЕРХ = 1.875f;

    /** Полуширина туловища и всей фигуры с руками, в блоках. */
    protected static final float ПОЛУШИРИНА_ТЕЛА = 0.234f;
    protected static final float ПОЛУШИРИНА_РУК = 0.469f;

    /** Выбранная часть тела. {@code null} — ещё ничего не выбрано. */
    protected Wellbeing.Часть выбрана;

    /** Кого осматриваем. В своём экране — сам игрок; чужого даёт задача 12. */
    protected LivingEntity цель() {
        return Minecraft.getInstance().player;
    }

    protected void нарисоватьМодель(GuiGraphics графика) {
        LivingEntity кто = цель();
        if (кто == null) return;
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            графика,
            левый + МОДЕЛЬ_X, верхний + МОДЕЛЬ_Y,
            левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш, верхний + МОДЕЛЬ_Y + МОДЕЛЬ_В,
            МАСШТАБ, СДВИГ, 0f, 0f, кто);
    }

    /** Середина окна модели по горизонтали. */
    private int центрX() {
        return левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш / 2;
    }

    /** Экранный X для точки фигуры, в блоках от середины. Ось перевёрнута рендером. */
    private int экранX(float блоки) {
        return центрX() - Math.round(МАСШТАБ * блоки);
    }

    /** Экранный Y для точки фигуры, в блоках от подошв. */
    private int экранY(float блоки) {
        LivingEntity кто = цель();
        float половинаРоста = кто == null ? 0.9f : кто.getBbHeight() / 2f;
        int центрY = верхний + МОДЕЛЬ_Y + МОДЕЛЬ_В / 2;
        return центрY + Math.round(МАСШТАБ * (половинаРоста + СДВИГ - блоки));
    }

    /**
     * Прямоугольники части тела на экране: {x1, y1, x2, y2}.
     *
     * У рук и ног их по два — это и есть та самая пара, которую надо
     * подсвечивать целиком. Числа прикидочные, подкручиваются глазом
     * при первой живой проверке.
     */
    protected int[][] прямоугольники(Wellbeing.Часть часть) {
        return switch (часть) {
            case HEAD -> new int[][] {{
                экранX(ПОЛУШИРИНА_ТЕЛА), экранY(ГОЛОВА_ВЕРХ),
                экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ)}};
            case TORSO -> new int[][] {{
                экранX(ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ),
                экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ)}};
            case ARMS -> new int[][] {
                {экранX(ПОЛУШИРИНА_РУК), экранY(ТУЛОВИЩЕ_ВЕРХ),
                 экранX(ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ)},
                {экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ),
                 экранX(-ПОЛУШИРИНА_РУК), экранY(НОГИ_ВЕРХ)}};
            case LEGS -> new int[][] {
                {экранX(ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ),
                 экранX(0f), экранY(0f)},
                {экранX(0f), экранY(НОГИ_ВЕРХ),
                 экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(0f)}};
        };
    }

    /** Какая часть тела под курсором. {@code null} — ни одна. */
    protected Wellbeing.Часть частьПод(int мышьX, int мышьY) {
        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            for (int[] п : прямоугольники(часть)) {
                if (мышьX >= п[0] && мышьX < п[2] && мышьY >= п[1] && мышьY < п[3]) {
                    return часть;
                }
            }
        }
        return null;
    }
```

- [ ] **Шаг 2: уголки вокруг части тела**

Дописать туда же:

```java
    /** Длина уголка, пикселей. Четыре — читается и не спорит с фигурой. */
    private static final int УГОЛОК = 4;

    /**
     * Уголки вокруг части тела, а не заливка: подсветить кусок 3D-модели
     * поверх скина нечем, а уголки читаются и картинку не портят.
     */
    protected void уголки(GuiGraphics графика, Wellbeing.Часть часть, int цвет) {
        for (int[] п : прямоугольники(часть)) {
            рамкаУголками(графика, п[0], п[1], п[2], п[3], цвет);
        }
    }

    private void рамкаУголками(GuiGraphics г, int x1, int y1, int x2, int y2, int цвет) {
        int д = Math.min(УГОЛОК, Math.max(1, Math.min(x2 - x1, y2 - y1) / 2));
        // верхний левый
        г.fill(x1, y1, x1 + д, y1 + 1, цвет);
        г.fill(x1, y1, x1 + 1, y1 + д, цвет);
        // верхний правый
        г.fill(x2 - д, y1, x2, y1 + 1, цвет);
        г.fill(x2 - 1, y1, x2, y1 + д, цвет);
        // нижний левый
        г.fill(x1, y2 - 1, x1 + д, y2, цвет);
        г.fill(x1, y2 - д, x1 + 1, y2, цвет);
        // нижний правый
        г.fill(x2 - д, y2 - 1, x2, y2, цвет);
        г.fill(x2 - 1, y2 - д, x2, y2, цвет);
    }
```

- [ ] **Шаг 3: нажатие и наведение**

Заменить `render` и добавить `mouseClicked`:

```java
    /** Цвет уголков под курсором и у выбранной части. */
    private static final int НАВЕДЕНИЕ = 0xFF9A9A8A;
    private static final int ВЫБРАНО = 0xFFE0E0E0;

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        renderBackground(графика, мышьX, мышьY, кадр);
        панель(графика);
        графика.drawString(font, title, левый + 8, верхний + 8, ТЕКСТ, false);

        нарисоватьМодель(графика);

        Wellbeing.Часть под = частьПод(мышьX, мышьY);
        if (выбрана != null) уголки(графика, выбрана, ВЫБРАНО);
        if (под != null && под != выбрана) уголки(графика, под, НАВЕДЕНИЕ);

        super.render(графика, мышьX, мышьY, кадр);
    }

    @Override
    public boolean mouseClicked(double мышьX, double мышьY, int кнопка) {
        Wellbeing.Часть под = частьПод((int) мышьX, (int) мышьY);
        if (под != null) {
            выбрана = под;
            выбрана(под);
            return true;
        }
        return super.mouseClicked(мышьX, мышьY, кнопка);
    }

    /**
     * Часть тела выбрана. Здесь только тихий щелчок; вкладку переключает
     * задача 9, переопределяя этот метод.
     */
    protected void выбрана(Wellbeing.Часть часть) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.18f, 1.4f);
        }
    }
```

- [ ] **Шаг 4: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 5: живая проверка и подгонка чисел**

Запустить: `./gradlew runClient`, открыть экран по Y. Проверить:
1. фигура стоит лицом к игроку и **не поворачивается** за мышью;
2. фигура целиком помещается в окно, не обрезана сверху и снизу;
3. наведение на голову, грудь, руку и ногу даёт уголки ровно вокруг
   этой части;
4. наведение на руку подсвечивает **обе** руки, на ногу — **обе** ноги;
5. щелчок закрепляет уголки на выбранной части и звучит тихо.

Если рамки не совпадают с фигурой, править `МАСШТАБ`, `МОДЕЛЬ_В`
и три константы границ по высоте — они для того и вынесены отдельно.

- [ ] **Шаг 6: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java
git commit -m "Модель тела и выбор частей в экране здоровья"
```

---

### Задача 9: вкладки и правая панель

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`

**Интерфейсы:**
- Потребляет: всё из задач 6–8, атлас `health_icons.png` из задачи 5.
- Отдаёт: `HealthScreen.Вкладка` (enum `STATE, BODY, FEEL, MEMORY`);
  защищённое поле `вкладка`; метод `правая(GuiGraphics)`;
  `протянуть(GuiGraphics, Component, int y)` → int (новый y).

Модель остаётся слева во всех вкладках. Вкладки меняют только правую
панель — экран не перелистывается на новую страницу, как и требует
задание. Щелчок по части тела сам переключает на вкладку «Тело».

- [ ] **Шаг 1: вкладки**

Дописать в `HealthScreen` (импорты `net.minecraft.resources.ResourceLocation`,
`net.minecraft.util.FormattedCharSequence`, `java.util.List`):

```java
    private static final ResourceLocation ЗНАЧКИ =
        ResourceLocation.fromNamespaceAndPath("plaguecore", "textures/gui/health_icons.png");

    /** Лист значков: 64 × 16, ячейки вкладок 12 × 12 с шагом 12. */
    private static final int ЛИСТ_Ш = 64, ЛИСТ_В = 16, ЯЧЕЙКА = 12;

    /** Капля жажды в листе. */
    private static final int КАПЛЯ_X = 48, КАПЛЯ_Ш = 7, КАПЛЯ_В = 9;

    public enum Вкладка { STATE, BODY, FEEL, MEMORY }

    protected Вкладка вкладка = Вкладка.STATE;

    /** Где начинается правая колонка и сколько ей ширины. */
    protected int правыйX() { return левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш + 12; }
    protected int праваяШирина() { return левый + ШИРИНА - 10 - правыйX(); }

    /** Первая вкладка стоит левее правого края на четыре ячейки с зазором. */
    private int вкладкаX(int номер) {
        return левый + ШИРИНА - 8 - (4 - номер) * (ЯЧЕЙКА + 2);
    }

    private int вкладкаY() { return верхний + 6; }

    private void нарисоватьВкладки(GuiGraphics графика, int мышьX, int мышьY) {
        Вкладка[] все = Вкладка.values();
        for (int i = 0; i < все.length; i++) {
            int x = вкладкаX(i), y = вкладкаY();
            boolean своя = вкладка == все[i];
            boolean под = мышьX >= x && мышьX < x + ЯЧЕЙКА
                       && мышьY >= y && мышьY < y + ЯЧЕЙКА;

            графика.fill(x - 1, y - 1, x + ЯЧЕЙКА + 1, y + ЯЧЕЙКА + 1,
                своя ? 0xFF2A322A : (под ? 0xFF1E241E : 0xFF161C16));
            графика.blit(ЗНАЧКИ, x, y, ЯЧЕЙКА, ЯЧЕЙКА,
                i * ЯЧЕЙКА, 0, ЯЧЕЙКА, ЯЧЕЙКА, ЛИСТ_Ш, ЛИСТ_В);
        }
    }

    private Вкладка вкладкаПод(int мышьX, int мышьY) {
        Вкладка[] все = Вкладка.values();
        for (int i = 0; i < все.length; i++) {
            int x = вкладкаX(i), y = вкладкаY();
            if (мышьX >= x && мышьX < x + ЯЧЕЙКА && мышьY >= y && мышьY < y + ЯЧЕЙКА) {
                return все[i];
            }
        }
        return null;
    }
```

- [ ] **Шаг 2: правая панель**

```java
    /** Печатает текст с переносами по ширине колонки. Возвращает новый y. */
    protected int протянуть(GuiGraphics графика, Component текст, int y, int цвет) {
        List<FormattedCharSequence> строки = font.split(текст, праваяШирина());
        for (FormattedCharSequence строка : строки) {
            графика.drawString(font, строка, правыйX(), y, цвет, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    protected void правая(GuiGraphics графика) {
        int y = верхний + 24;
        switch (вкладка) {
            case STATE -> {
                y = протянуть(графика, HealthSense.общее(), y, ТЕКСТ) + 6;
                y = сердца(графика, y) + 4;
                y = строкаСоЗначком(графика, HealthSense.голод(), y, true);
                if (HealthSense.жажда() != null) {
                    строкаСоЗначком(графика, HealthSense.жажда(), y, false);
                }
            }
            case BODY -> {
                if (выбрана == null) {
                    протянуть(графика,
                        Component.translatable("plaguecore.health.hint.pick"), y, ТУСКЛЫЙ);
                } else {
                    протянуть(графика, HealthSense.часть(выбрана), y, ТЕКСТ);
                }
            }
            case FEEL -> {
                List<Component> что = HealthSense.ощущения();
                if (что.isEmpty()) {
                    протянуть(графика,
                        Component.translatable("plaguecore.health.feel.none"), y, ТУСКЛЫЙ);
                } else {
                    for (Component строка : что) y = протянуть(графика, строка, y, ТЕКСТ) + 2;
                }
            }
            case MEMORY -> протянуть(графика,
                Component.translatable("plaguecore.health.memory.none"), y, ТУСКЛЫЙ);
        }
    }

    /**
     * Сердца ванильными спрайтами плюс число. Максимум учитывается как
     * есть: постоянная потеря за смерти и временный штраф стадии оба
     * уже сидят в getMaxHealth, считать отдельно нечего.
     */
    private int сердца(GuiGraphics графика, int y) {
        LivingEntity кто = цель();
        if (кто == null) return y;

        int здоровье = Mth.ceil(кто.getHealth());
        int максимум = Mth.ceil(кто.getMaxHealth());
        int всего = Math.max(1, Mth.ceil(максимум / 2f));

        int вРяду = 10;
        int x = правыйX();
        for (int i = 0; i < всего; i++) {
            int сx = x + (i % вРяду) * 8;
            int сy = y + (i / вРяду) * 10;
            графика.blitSprite(СЕРДЦЕ_ПУСТО, сx, сy, 9, 9);
            int вЭтом = здоровье - i * 2;
            if (вЭтом >= 2) графика.blitSprite(СЕРДЦЕ_ПОЛНОЕ, сx, сy, 9, 9);
            else if (вЭтом == 1) графика.blitSprite(СЕРДЦЕ_ПОЛОВИНА, сx, сy, 9, 9);
        }
        int рядов = (всего + вРяду - 1) / вРяду;
        int числоY = y + (рядов - 1) * 10;
        графика.drawString(font, здоровье + " / " + максимум,
            x + Math.min(всего, вРяду) * 8 + 6, числоY + 1, ТУСКЛЫЙ, false);
        return y + рядов * 10;
    }

    /** Строка «значок + фраза». Значок голода — ванильный, капля — своя. */
    private int строкаСоЗначком(GuiGraphics графика, Component текст, int y, boolean еда) {
        if (еда) {
            графика.blitSprite(ЕДА, правыйX(), y, 9, 9);
        } else {
            графика.blit(ЗНАЧКИ, правыйX() + 1, y, КАПЛЯ_Ш, КАПЛЯ_В,
                КАПЛЯ_X, 0, КАПЛЯ_Ш, КАПЛЯ_В, ЛИСТ_Ш, ЛИСТ_В);
        }
        List<FormattedCharSequence> строки = font.split(текст, праваяШирина() - 14);
        int сy = y;
        for (FormattedCharSequence строка : строки) {
            графика.drawString(font, строка, правыйX() + 14, сy, ТЕКСТ, false);
            сy += font.lineHeight + 1;
        }
        return Math.max(сy, y + 11);
    }
```

Константы спрайтов — вверх класса:

```java
    private static final ResourceLocation СЕРДЦЕ_ПУСТО =
        ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation СЕРДЦЕ_ПОЛНОЕ =
        ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation СЕРДЦЕ_ПОЛОВИНА =
        ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation ЕДА =
        ResourceLocation.withDefaultNamespace("hud/food_full");
```

- [ ] **Шаг 3: встроить в `render` и нажатия**

В `render`, после уголков, добавить:

```java
        нарисоватьВкладки(графика, мышьX, мышьY);
        правая(графика);
```

В `mouseClicked`, перед проверкой части тела:

```java
        Вкладка нажата = вкладкаПод((int) мышьX, (int) мышьY);
        if (нажата != null) {
            вкладка = нажата;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.1f);
            }
            return true;
        }
```

И в `выбрана(Часть)` добавить переключение вкладки первой строкой:

```java
        вкладка = Вкладка.BODY;
```

- [ ] **Шаг 4: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 5: живая проверка**

`./gradlew runClient`, экран по Y:
1. четыре значка вкладок в шапке, у текущей фон светлее;
2. «Самочувствие» — фраза, сердца с числом, голод, жажда;
3. `/effect give @s minecraft:weakness 60 1` — во вкладке «Ощущения»
   появляется «Руки кажутся тяжелее обычного», без названия и уровня;
4. щелчок по руке переключает на «Тело» и показывает текст про руки;
5. `/plague setstage 3` — фразы меняются на более тяжёлые;
6. экран читается за несколько секунд, чисел болезни нигде нет.

- [ ] **Шаг 6: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java
git commit -m "Вкладки и правая панель экрана здоровья"
```

---

### Задача 10: память — журнал событий на сессию

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthMemory.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/en_us.json`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`

**Интерфейсы:**
- Потребляет: `PlagueClientAccess.принятьСтадию`, `PossessionClient.ведут()`.
- Отдаёт: `HealthMemory.записать(String ключ)`;
  `HealthMemory.записи()` → `List<Component>`; `HealthMemory.забыть()`;
  `HealthMemory.приСмене(int былаСтадия, int сталаСтадия)`.

Журнал пишется **и когда экран закрыт** — иначе он пуст ровно в тот
момент, когда его впервые открывают. Живёт до выхода из мира: это
прямое требование задания.

- [ ] **Шаг 1: написать журнал**

`plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthMemory.java`:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Что человек за собой заметил. Спек интерфейса, раздел 11.
 *
 * Сам игрок сюда ничего не пишет — записи рождаются из событий.
 * Кольцо на двенадцать строк, живёт до выхода из мира и обнуляется
 * при перезаходе: память тут короткая, это не дневник.
 *
 * Пишется и с закрытым экраном. Иначе журнал будет пуст ровно в тот
 * момент, когда его впервые открывают, и вкладка окажется бессмысленной.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthMemory {
    private HealthMemory() {}

    /** Сколько записей помним. Больше — вкладка перестаёт читаться. */
    private static final int ДЛИНА = 12;

    private record Запись(String время, String ключ) {}

    private static final Deque<Запись> кольцо = new ArrayDeque<>();

    /** Записать событие. Ключ — из языкового файла, текста тут нет. */
    public static void записать(String ключ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long сутки = mc.level.getDayTime() % 24000L;
        int час = (int) ((сутки / 1000L + 6L) % 24L);
        int минута = (int) ((сутки % 1000L) * 60L / 1000L);
        String время = String.format("%02d:%02d", час, минута);

        if (кольцо.size() >= ДЛИНА) кольцо.removeFirst();
        кольцо.addLast(new Запись(время, ключ));
    }

    /** Готовые строки журнала, снизу — самое свежее. */
    public static List<Component> записи() {
        List<Component> список = new ArrayList<>(кольцо.size());
        for (Запись з : кольцо) {
            список.add(Component.literal(з.время() + "  ")
                .append(Component.translatable(з.ключ())));
        }
        return список;
    }

    public static void забыть() {
        кольцо.clear();
    }

    /**
     * Стадия сменилась. Зовёт {@link PlagueClientAccess} при получении
     * пакета: это единственное место, где клиент узнаёт о смене.
     */
    public static void приСмене(int была, int стала) {
        if (стала > была) записать("plaguecore.health.memory.worse");
        else if (стала < была) записать("plaguecore.health.memory.better");
    }

    /** Отвар выпит. Ловим окончание использования — не начало. */
    @SubscribeEvent
    public static void приДопитом(LivingEntityUseItemEvent.Finish событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || событие.getEntity() != mc.player) return;
        String имя = событие.getItem().getItem().toString();
        if (имя.contains("plague_brew") || имя.contains("clerics_brew")) {
            записать("plaguecore.health.memory.brew");
        }
    }

    /** Выход из мира стирает память: она живёт только эту сессию. */
    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        забыть();
    }
}
```

- [ ] **Шаг 2: позвать журнал из приёма стадии и из одержимости**

В `PlagueClientAccess.принятьСтадию` заменить тело на:

```java
    public static void принятьСтадию(PlagueNetwork.Stage пакет) {
        int была = стадия;
        стадия = пакет.стадия();
        HealthMemory.приСмене(была, стадия);
    }
```

В `PossessionClient.принять` — после присвоения `ведут`, чтобы поймать
и начало, и конец:

```java
        boolean былоВедут = ведут;
        ведут = пакет.ведут();
        if (ведут != былоВедут) {
            HealthMemory.записать("plaguecore.health.memory.blackout");
        }
```

- [ ] **Шаг 3: дописать строки и расширить тест полноты**

В оба языковых файла:

```json
  "plaguecore.health.memory.worse": "Мне стало хуже.",
  "plaguecore.health.memory.better": "Кажется, мне немного легче.",
  "plaguecore.health.memory.brew": "Я выпил отвар.",
  "plaguecore.health.memory.blackout": "Я не помню, что было только что."
```

В `LangCoverageTest.всеКлючи()` добавить эти четыре ключа.

- [ ] **Шаг 4: показать журнал во вкладке «Память»**

В `HealthScreen.правая()` заменить ветку `MEMORY` на:

```java
            case MEMORY -> {
                List<Component> что = HealthMemory.записи();
                if (что.isEmpty()) {
                    протянуть(графика,
                        Component.translatable("plaguecore.health.memory.none"), y, ТУСКЛЫЙ);
                } else {
                    for (int i = что.size() - 1; i >= 0; i--) {
                        y = протянуть(графика, что.get(i), y, ТУСКЛЫЙ) + 1;
                        if (y > верхний + ВЫСОТА - 14) break;
                    }
                }
            }
```

Свежее сверху: человек открывает журнал ради последнего, а не первого.

- [ ] **Шаг 5: собрать и прогнать тесты**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL, `LangCoverageTest` проходит.

- [ ] **Шаг 6: живая проверка**

`./gradlew runClient`:
1. `/plague setstage 2` — во вкладке «Память» появляется «Мне стало
   хуже» со временем;
2. `/plague setstage 0` — появляется «Кажется, мне немного легче»;
3. выпить отвар — появляется «Я выпил отвар»;
4. выйти в главное меню и зайти обратно — журнал пуст;
5. тринадцатая запись вытесняет первую.

- [ ] **Шаг 7: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client \
        plaguecore/src/main/resources/assets/plaguecore/lang \
        plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java
git commit -m "Память: журнал событий на сессию"
```

---

### Задача 11: HUD

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthHud.java`

**Интерфейсы:**
- Потребляет: `HealthSense.ступень()`, `HealthSense.общееКратко()`.
- Отдаёт: ничего наружу — подписывается на `RenderGuiEvent.Post` сам.

Левый верхний угол, решение владельца. Ни хотбар, ни чат, ни прицел
не задевает. На нулевой ступени не рисуется вовсе: здоровому человеку
нечего сообщать.

- [ ] **Шаг 1: написать HUD**

`plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthHud.java`:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Строка самочувствия на игровом экране. Спек, раздел 8.
 *
 * Левый верхний угол: там ничего нашего нет, а хотбар, чат и прицел
 * остаются свободны. На нулевой ступени не рисуется вовсе — здоровому
 * человеку сообщать нечего, а постоянная строка «я в порядке» за
 * двадцать часов сессии перестала бы читаться.
 *
 * Чем хуже человеку, тем тусклее и тревожнее строка. На краю она
 * начинает дышать, как плёнка {@link PlagueOverlay}: это уже не подпись,
 * а состояние.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthHud {
    private HealthHud() {}

    private static final int X = 4, Y = 4;

    /** Цвет строки по ступеням 0–4. Серый уходит в больной жёлто-серый. */
    private static final int[] ЦВЕТ = {
        0xFFB0B0A8, 0xFFB0B0A8, 0xFFAFA88C, 0xFFAC9A78, 0xFFA88068
    };

    @SubscribeEvent
    public static void нарисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int ступень = HealthSense.ступень();
        if (ступень <= 0) return;

        Component строка = HealthSense.общееКратко();
        GuiGraphics графика = событие.getGuiGraphics();
        int ширина = mc.font.width(строка);

        графика.fill(X - 2, Y - 2, X + ширина + 2, Y + mc.font.lineHeight + 1, 0x60000000);
        графика.drawString(mc.font, строка, X, Y, цвет(mc, ступень), false);
    }

    /** На краю строка дышит: неподвижная надпись перестаёт читаться как болезнь. */
    private static int цвет(Minecraft mc, int ступень) {
        int основной = ЦВЕТ[Math.min(ступень, ЦВЕТ.length - 1)];
        if (ступень < 4 || mc.player == null) return основной;

        float дыхание = (Mth.sin(mc.player.tickCount / 9f) + 1f) / 2f;
        int альфа = 140 + Math.round(дыхание * 115f);
        return (альфа << 24) | (основной & 0xFFFFFF);
    }
}
```

- [ ] **Шаг 2: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 3: живая проверка, в том числе на пересечение**

`./gradlew runClient`:
1. на стадии 0 в углу пусто;
2. `/plague setstage 1` — появляется «Что-то я неважно себя чувствую»;
3. `/plague setstage 4` — строка тревожная и дышит;
4. хотбар, чат и прицел не задеты;
5. **проверить пересечение со значками голосового чата.** Если они
   стоят в том же углу, опустить `Y` на их высоту, а не переезжать
   в другой угол: угол выбрал владелец.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthHud.java
git commit -m "HUD самочувствия в левом верхнем углу"
```

---

### Задача 12: осмотр соседа

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthKeys.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`

**Интерфейсы:**
- Потребляет: `HealthScreen` из задач 7–10, `PlayerPlagueData.данные`.
- Отдаёт: `PlagueNetwork.Look(int сущность)` (клиент → сервер);
  `PlagueNetwork.Impression(int сущность, int впечатление)` (сервер → клиент);
  `PlagueClientAccess.принятьВпечатление(Impression)`;
  `HealthScreen.открытьЧужой(Player кто, int ступень)`;
  защищённое поле `чужой` (boolean) и `чужаяСтупень` (int).

Сервер проверяет всё сам: тот же мир, расстояние не больше восьми
блоков, цель — игрок. Клиенту тут не верим по тому же правилу, что и
в админском экране: экран можно открыть подменённым клиентом.

**Версия протокола меняется.** В `PlagueNetwork.VERSION` стоит `"5"`;
после добавления пакетов поставить `"6"` — иначе клиент старой сборки
молча не поймёт новый обмен.

- [ ] **Шаг 1: два пакета**

Дописать в `PlagueNetwork` рядом с остальными записями:

```java
    /**
     * «Посмотреть на этого человека». Клиент называет только номер
     * сущности — всё остальное сервер выясняет сам и сам решает,
     * отвечать ли.
     */
    public record Look(int сущность) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Look> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "look"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC =
            StreamCodec.of((buf, l) -> buf.writeVarInt(l.сущность),
                           buf -> new Look(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Впечатление о человеке: грубая ступень 0–4, та же шкала, что
     * у себя. Ни заражения, ни точных чисел — их не отдаём даже
     * Клирику, разница в подробности слов, а не в данных.
     */
    public record Impression(int сущность, int впечатление) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Impression> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "impression"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Impression> CODEC =
            StreamCodec.of(
                (buf, i) -> {
                    buf.writeVarInt(i.сущность);
                    buf.writeVarInt(i.впечатление);
                },
                buf -> new Impression(buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
```

- [ ] **Шаг 2: регистрация и обработчик на сервере**

В `зарегистрировать` добавить:

```java
        registrar.playToClient(Impression.TYPE, Impression.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьВпечатление(payload)));

        registrar.playToServer(Look.TYPE, Look.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) осмотреть(player, payload);
            }));
```

И сам обработчик:

```java
    /** Дальше этого осматривать нельзя: за восемь блоков лица уже не видно. */
    private static final double ОСМОТР_ДИСТАНЦИЯ = 8.0;

    /**
     * Осмотр соседа. Всё проверяется здесь: клиент называет только номер,
     * а мир, расстояние и тип цели выясняет сервер. Иначе подменённый
     * клиент спрашивал бы про любого игрока на карте.
     */
    private static void осмотреть(ServerPlayer кто, Look запрос) {
        var сущность = кто.level().getEntity(запрос.сущность());
        if (!(сущность instanceof ServerPlayer цель)) return;
        if (цель.level() != кто.level()) return;
        if (кто.distanceTo(цель) > ОСМОТР_ДИСТАНЦИЯ) return;

        int ступень = PlayerPlagueData.данные(цель).стадия;
        PacketDistributor.sendToPlayer(кто, new Impression(цель.getId(), ступень));
    }
```

- [ ] **Шаг 3: поднять версию протокола**

```java
    private static final String VERSION = "6";
```

- [ ] **Шаг 4: приём на клиенте**

В `PlagueClientAccess`:

```java
    /** Впечатление о соседе. Открывает чужой осмотр. */
    public static void принятьВпечатление(PlagueNetwork.Impression пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getEntity(пакет.сущность()) instanceof Player кто) {
            HealthScreen.открытьЧужой(кто, пакет.впечатление());
        }
    }
```

- [ ] **Шаг 5: клавиша смотрит, на кого наведён взгляд**

В `HealthKeys.приТике` заменить тело цикла на:

```java
        while (КЛАВИША.consumeClick()) {
            if (mc.crosshairPickEntity instanceof Player кто && кто != mc.player) {
                PacketDistributor.sendToServer(new PlagueNetwork.Look(кто.getId()));
            } else {
                HealthScreen.открытьСвой();
            }
        }
```

`crosshairPickEntity` — то, на что игрок смотрит прямо сейчас; дальность
подбора у ванильного клиента меньше восьми блоков, так что серверная
проверка расстояния почти никогда не сработает и стоит там только
против подменённого клиента.

- [ ] **Шаг 6: чужой режим экрана**

В `HealthScreen` добавить поля, конструктор и переопределения:

```java
    /** Осматриваем соседа, а не себя. */
    protected boolean чужой;

    /** Кого осматриваем и какое о нём впечатление. */
    protected Player ктоЧужой;
    protected int чужаяСтупень;

    protected HealthScreen(Player кто, int ступень) {
        this();
        this.чужой = true;
        this.ктоЧужой = кто;
        this.чужаяСтупень = ступень;
    }

    /** Открыть осмотр соседа. */
    public static void открытьЧужой(Player кто, int ступень) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen(кто, ступень));
    }

    @Override
    protected LivingEntity цель() {
        return чужой ? ктоЧужой : Minecraft.getInstance().player;
    }
```

Конструктор без аргументов уже `protected` и уже только зовёт `super`,
трогать его не надо — новый просто делегирует ему через `this()`.

Вкладки «Ощущения» и «Память» у чужого не показываются: чужих ощущений
и чужой памяти знать неоткуда, а показать их — выдать механику. Заменить
перебор вкладок на общий список:

```java
    /** Какие вкладки доступны сейчас. У чужого — только две первые. */
    protected Вкладка[] вкладки() {
        return чужой
            ? new Вкладка[] { Вкладка.STATE, Вкладка.BODY }
            : Вкладка.values();
    }
```

и во всех трёх местах (`вкладкаX`, `нарисоватьВкладки`, `вкладкаПод`)
пользоваться `вкладки()` вместо `Вкладка.values()`, а в `вкладкаX`
заменить `4` на `вкладки().length`.

В `правая()` в ветках `STATE` и `BODY` брать чужой текст, когда
`чужой`:

```java
            case STATE -> {
                Component строка = чужой
                    ? Component.translatable(Wellbeing.чужоеОбщее(чужаяСтупень))
                    : HealthSense.общее();
                y = протянуть(графика, строка, y, ТЕКСТ) + 6;
                y = сердца(графика, y) + 4;
                if (!чужой) {
                    y = строкаСоЗначком(графика, HealthSense.голод(), y, true);
                    if (HealthSense.жажда() != null) {
                        строкаСоЗначком(графика, HealthSense.жажда(), y, false);
                    }
                }
            }
            case BODY -> {
                if (выбрана == null) {
                    протянуть(графика,
                        Component.translatable("plaguecore.health.hint.pick"), y, ТУСКЛЫЙ);
                } else {
                    Component строка = чужой
                        ? Component.translatable(Wellbeing.чужаяЧасть(выбрана, чужаяСтупень))
                        : HealthSense.часть(выбрана);
                    протянуть(графика, строка, y, ТЕКСТ);
                }
            }
```

Голод и жажда у чужого не показываются вовсе: снаружи их не видно.
Сердца видно — чужое здоровье клиент и так знает, его рисует ванильный
рендер над головой в некоторых модах.

- [ ] **Шаг 7: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 8: живая проверка вдвоём**

Запустить сервер (`./gradlew runServer`) и два клиента, либо проверить
на локальном мире с открытым доступом:
1. навести взгляд на второго игрока, нажать Y — открылся чужой осмотр
   с его скином;
2. вкладок только две: «Самочувствие» и «Тело»;
3. `/plague setstage 3` второму — впечатление меняется на тяжёлое;
4. отойти дальше восьми блоков и нажать Y — открывается **свой** экран
   (взгляд уже не цепляет цель), сервер ничего не отвечает;
5. нажать Y, не глядя ни на кого — свой экран.

- [ ] **Шаг 9: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore
git commit -m "Осмотр соседа: два пакета и чужой режим экрана"
```

---

### Задача 13: Клирик видит подробнее

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/core/Wellbeing.java`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/core/WellbeingTest.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/en_us.json`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`

**Интерфейсы:**
- Потребляет: `Wellbeing` (задачи 1–2), `HealthSense.клирик()` (задача 6).
- Отдаёт: `Wellbeing.клирикЧасть(Часть, int ступень, boolean чужой)` → String;
  `Wellbeing.клирикОбщее(int ступень)` → String (только для чужого осмотра).

Клирик получает **лучше замеченный симптом**, а не число. Ни стадии,
ни процентов, ни внутренних величин — разница только в подробности слов.
Работает и на себе, и на соседе.

- [ ] **Шаг 1: дописать падающие тесты**

В `WellbeingTest`:

```java
    @Test
    void уКлирикаСвоиКлючиИОниРазныеДляСебяИЧужого() {
        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
                String своё = Wellbeing.клирикЧасть(часть, с, false);
                String чужое = Wellbeing.клирикЧасть(часть, с, true);
                assertNotEquals(своё, чужое);
                assertNotEquals(своё, Wellbeing.часть(часть, с));
                assertNotEquals(чужое, Wellbeing.чужаяЧасть(часть, с));
                проверить(своё);
                проверить(чужое);
            }
        }
    }

    @Test
    void общееКлирикаОтличаетсяОтОбычногоЧужого() {
        for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
            assertNotEquals(Wellbeing.чужоеОбщее(с), Wellbeing.клирикОбщее(с));
            проверить(Wellbeing.клирикОбщее(с));
        }
    }
```

- [ ] **Шаг 2: запустить и убедиться, что падает**

Запустить: `./gradlew test --tests "*WellbeingTest*"`
Ожидается: провал компиляции, `cannot find symbol: method клирикЧасть`.

- [ ] **Шаг 3: реализация в `Wellbeing`**

```java
    /**
     * Что Клирик замечает сверх обычного взгляда. Это вторая строка
     * к описанию, а не замена ему.
     *
     * Никаких чисел: Клирик не диагност с прибором, он просто лучше
     * видит симптом. Стадию и заражённость ему не отдают так же, как
     * и всем остальным.
     */
    public static String клирикЧасть(Часть часть, int ступень, boolean чужой) {
        return КОРЕНЬ + "cleric." + (чужой ? "other." : "") + "body."
            + имя(часть) + "." + ступень(ступень);
    }

    /** Что Клирик замечает в человеке целиком. Только при чужом осмотре. */
    public static String клирикОбщее(int ступень) {
        return КОРЕНЬ + "cleric.other.overall." + ступень(ступень);
    }
```

- [ ] **Шаг 4: тексты**

Дописать в оба языковых файла 45 ключей: `cleric.body.<part>.0..4`
(20), `cleric.other.body.<part>.0..4` (20) и `cleric.other.overall.0..4`
(5). Русские образцы задают тон — остальные пишутся так же:

```json
  "plaguecore.health.cleric.body.arms.2": "Слабость идёт от плеча, а не от кисти. Так бывает, когда тело тратит силы на другое.",
  "plaguecore.health.cleric.body.arms.3": "Руки не держат совсем. Это уже не усталость.",
  "plaguecore.health.cleric.other.body.arms.2": "В руках у него заметная слабость. Он почти не пользуется ими как обычно.",
  "plaguecore.health.cleric.other.overall.2": "Дыхание тяжёлое, кожа сухая. Кажется, ему становится хуже."
```

Добавить эти ключи в `LangCoverageTest.всеКлючи()` перебором по частям
и ступеням.

- [ ] **Шаг 5: показать вторую строку на экране**

В `HealthScreen.правая()`, в ветке `BODY`, после основной строки:

```java
                    y = протянуть(графика, строка, y, ТЕКСТ);
                    if (HealthSense.клирик()) {
                        протянуть(графика, Component.translatable(
                            Wellbeing.клирикЧасть(выбрана, ступеньДляТекста(), чужой)),
                            y + 4, ТУСКЛЫЙ);
                    }
```

В ветке `STATE`, после общей строки, — только при чужом осмотре:

```java
                if (чужой && HealthSense.клирик()) {
                    y = протянуть(графика, Component.translatable(
                        Wellbeing.клирикОбщее(чужаяСтупень)), y, ТУСКЛЫЙ) + 4;
                }
```

И вспомогательный метод рядом с `цель()`:

```java
    /** Ступень, по которой подбирается текст: своя или чужая. */
    protected int ступеньДляТекста() {
        return чужой ? чужаяСтупень : HealthSense.ступень();
    }
```

- [ ] **Шаг 6: прогнать тесты и собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL, `WellbeingTest` и `LangCoverageTest` зелёные.

- [ ] **Шаг 7: живая проверка**

`./gradlew runClient`:
1. без класса Клирика вторых строк нет;
2. стать Клириком через алтарь или `/class set cleric` — при осмотре
   части тела появляется вторая, более тусклая строка;
3. при осмотре соседа Клирик видит добавочную строку и в
   «Самочувствии», и в «Теле»;
4. **нигде не появилось ни числа, ни слова «стадия»**;
5. запуск без `lmpc_classes` — вторых строк нет, падения нет.

- [ ] **Шаг 8: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore \
        plaguecore/src/main/resources/assets/plaguecore/lang \
        plaguecore/src/test/java/dev/denthe/plaguecore
git commit -m "Клирик видит подробнее — вторая строка к описанию"
```

---

### Задача 14: искажение при одержимости

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/en_us.json`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`

**Интерфейсы:**
- Потребляет: `PossessionClient.ведут()`, всё из задач 8–13.
- Отдаёт: ничего наружу.

Ломается экран **только пока телом правят**, по решению владельца.
На четвёртой ступени без одержимости экран остаётся мрачным, но
читаемым: редкое событие обязано оставаться редким, иначе через два
открытия оно перестанет пугать и начнёт мешать.

Не глитч и не sci-fi: мрачный пиксельный физический ужас. Чужие фразы
должны читаться как вторжение, а не как подпись интерфейса — другой
цвет, другое место, появляются рывком.

- [ ] **Шаг 1: гниль по краям панели на тяжёлых ступенях**

Дописать в `HealthScreen`:

```java
    /**
     * Гниль по краям панели. Растёт со ступенью, считается по
     * постоянному зерну — пятна не должны плясать каждый кадр.
     */
    protected void гниль(GuiGraphics графика) {
        int ступень = ступеньДляТекста();
        if (ступень < 2) return;

        java.util.Random зерно = new java.util.Random(0x9A17L + ступень);
        int пятен = ступень * 14;
        int тон = new int[] {0, 0, 0xFF1A1F16, 0xFF20220F, 0xFF241C10}[ступень];

        for (int i = 0; i < пятен; i++) {
            boolean поГоризонтали = зерно.nextBoolean();
            int размер = 1 + зерно.nextInt(ступень);
            int x, y;
            if (поГоризонтали) {
                x = левый + зерно.nextInt(ШИРИНА - размер);
                y = зерно.nextBoolean() ? верхний + зерно.nextInt(6)
                                        : верхний + ВЫСОТА - 6 + зерно.nextInt(6);
            } else {
                x = зерно.nextBoolean() ? левый + зерно.nextInt(6)
                                        : левый + ШИРИНА - 6 + зерно.nextInt(6);
                y = верхний + зерно.nextInt(ВЫСОТА - размер);
            }
            графика.fill(x, y, x + размер, y + размер, тон);
        }
    }
```

Звать в `render` сразу после `панель(графика)`.

- [ ] **Шаг 2: дрожь и сползание при одержимости**

```java
    /** Насколько панель уезжает от рывка, пикселей. */
    private static final int РЫВОК = 3;

    private final java.util.Random тряска = new java.util.Random();

    /** Сдвиг всего экрана в этом кадре. Ноль — телом никто не правит. */
    protected int дрожьX, дрожьY;

    private void посчитатьДрожь() {
        if (!PossessionClient.ведут()) {
            дрожьX = 0;
            дрожьY = 0;
            return;
        }
        // Рывками, а не ровным синусом: ровное дрожание читается как
        // анимация, а рывок — как то, что кто-то трогает тебя изнутри.
        if (тряска.nextInt(6) == 0) {
            дрожьX = тряска.nextInt(РЫВОК * 2 + 1) - РЫВОК;
            дрожьY = тряска.nextInt(РЫВОК_ВЕРТИКАЛЬ * 2 + 1) - РЫВОК_ВЕРТИКАЛЬ;
        }
    }

    /** По вертикали трясёт слабее: иначе текст становится нечитаемым. */
    private static final int РЫВОК_ВЕРТИКАЛЬ = 2;
```

В `render`, в самом начале, после `renderBackground`:

```java
        посчитатьДрожь();
        графика.pose().pushPose();
        графика.pose().translate(дрожьX, дрожьY, 0f);
```

и `графика.pose().popPose();` — перед `super.render(...)`.

- [ ] **Шаг 3: чужие фразы**

```java
    /** Сколько разных чужих фраз заведено в языковом файле. */
    private static final int ВТОРЖЕНИЙ = 6;

    /** Цвет чужой речи: не цвет интерфейса, и это намеренно. */
    private static final int ЧУЖОЕ = 0xFFB03030;

    private int чужаяФраза = -1;
    private int чужаяДо;
    private int чужаяX, чужаяY;

    /**
     * Чужая речь поверх интерфейса. Появляется рывком, не там, где
     * обычный текст, и держится недолго: это вторжение, а не подпись.
     */
    protected void вторжение(GuiGraphics графика) {
        if (!PossessionClient.ведут()) {
            чужаяФраза = -1;
            return;
        }

        int такт = (int) (Minecraft.getInstance().level == null
            ? 0 : Minecraft.getInstance().level.getGameTime());

        if (такт > чужаяДо) {
            if (тряска.nextInt(40) == 0) {
                чужаяФраза = тряска.nextInt(ВТОРЖЕНИЙ);
                чужаяДо = такт + 25 + тряска.nextInt(20);
                чужаяX = левый + 10 + тряска.nextInt(Math.max(1, ШИРИНА - 140));
                чужаяY = верхний + 30 + тряска.nextInt(Math.max(1, ВЫСОТА - 70));
            } else {
                чужаяФраза = -1;
            }
        }

        if (чужаяФраза >= 0) {
            Component строка = Component.translatable(
                "plaguecore.health.intrusion." + чужаяФраза);
            графика.fill(чужаяX - 2, чужаяY - 2,
                чужаяX + font.width(строка) + 2, чужаяY + font.lineHeight + 1, 0xC0000000);
            графика.drawString(font, строка, чужаяX, чужаяY, ЧУЖОЕ, false);
        }
    }
```

Звать в `render` последним, перед `popPose()`.

- [ ] **Шаг 4: модель дёргается**

В `нарисоватьМодель` при одержимости смещать окно фигуры рывком:

```java
        int рывокX = 0, рывокY = 0;
        if (PossessionClient.ведут() && тряска.nextInt(5) == 0) {
            рывокX = тряска.nextInt(5) - 2;
            рывокY = тряска.nextInt(3) - 1;
        }
```

и прибавить их к четырём координатам окна. Прямоугольники частей тела
при этом **не двигаются**: попадание мышью должно оставаться возможным,
иначе экран на четвёртой стадии станет просто сломанным, а не страшным.

- [ ] **Шаг 5: тексты вторжения**

В оба языковых файла:

```json
  "plaguecore.health.intrusion.0": "Я ЧУВСТВУЮ — ВЫХОДА НЕТ.",
  "plaguecore.health.intrusion.1": "ПРИЧИНИ ИМ ВРЕД",
  "plaguecore.health.intrusion.2": "ЭТО УЖЕ НЕ ТВОИ РУКИ",
  "plaguecore.health.intrusion.3": "ПОДОЙДИ БЛИЖЕ К НИМ",
  "plaguecore.health.intrusion.4": "ТЫ ЗДЕСЬ НЕ ОДИН",
  "plaguecore.health.intrusion.5": "НЕ СОПРОТИВЛЯЙСЯ"
```

Добавить ключи в `LangCoverageTest.всеКлючи()` циклом по шести номерам.

- [ ] **Шаг 6: собрать**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 7: живая проверка**

`./gradlew runClient`:
1. `/plague setstage 3` — по краям панели появилась гниль, экран
   читается;
2. `/plague setstage 4` — гнили больше, но текст всё ещё читается,
   дрожи нет;
3. отдать тело чуме или взять его панелью мастера игры — экран начинает
   дёргаться, модель рвётся, изредка выскакивают чужие фразы;
4. **по частям тела всё ещё можно попасть** во время одержимости;
5. одержимость кончилась — экран пришёл в себя.

- [ ] **Шаг 8: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java \
        plaguecore/src/main/resources/assets/plaguecore/lang \
        plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java
git commit -m "Экран ломается, пока телом правят чужие руки"
```

---

### Задача 15: сборка целиком, заметка и передача клавиши

**Файлы:**
- Создать: `docs/superpowers/notes/2026-09-14-sostoyanie-zdorovya-sdelano.md`
- Изменить: `CLAUDE.md` (одна короткая вставка в подсистему 2)

**Интерфейсы:**
- Потребляет: всё сделанное.
- Отдаёт: заметку, по которой соседняя сессия и второй участник
  поймут, что произошло и что осталось.

- [ ] **Шаг 1: полная сборка и все тесты**

Запустить: `./gradlew build`
Ожидается: BUILD SUCCESSFUL, ни один прежний тест не сломан.

- [ ] **Шаг 2: проверка выделенного сервера**

Запустить: `./gradlew runServer`
Ожидается: сервер поднимается, в логе нет `NoClassDefFoundError` и нет
упоминаний клиентских классов интерфейса. Зайти клиентом, открыть экран,
осмотреть себя.

- [ ] **Шаг 3: проверка без соседних модов**

Убрать `lmpc_classes` и мод жажды из `run/client/mods`, запустить
`./gradlew runClient`:
1. экран открывается;
2. строки жажды нет;
3. вторых строк Клирика нет;
4. в логе нет исключений от мостов.

- [ ] **Шаг 4: написать заметку**

`docs/superpowers/notes/2026-09-14-sostoyanie-zdorovya-sdelano.md`.
Что в ней обязано быть:

1. **Что сделано** — экран, HUD, клавиша, память, осмотр соседа,
   Клирик, искажение. Одним абзацем, без пересказа кода.
2. **Где что лежит** — список файлов из раздела 5 спека.
3. **Ключевое решение** — для осмотра себя новой сети не понадобилось:
   стадия, здоровье, голод, эффекты, одержимость и класс уже на клиенте.
   Новых пакетов два, оба только под осмотр соседа, версия протокола
   поднята с `5` до `6`.
4. **Долг второму участнику** — строка
   `key_key.plaguecore.health:key.keyboard.y` в
   `launcher/pack-config/config/lmpc-default-options.txt` и запись
   в `KEY_MIGRATIONS`, иначе клавиша не доедет до тех, кто уже играл
   (разбор — заметка `2026-09-12-posle-pervogo-testa-klavishi-i-drop`).
   Папка `launcher/` чужая, поэтому правка передаётся заметкой.
5. **Что не проверено живьём** — то, что по итогам шагов 1–3 осталось
   непроверенным, честным списком.
6. **Открытые вопросы** — из раздела 16 спека, с отметкой, какие
   закрылись при живой проверке.

- [ ] **Шаг 5: короткая вставка в `CLAUDE.md`**

В блок подсистемы 2 «Игрок» дописать одну строку, не переформатируя
соседние абзацы (общий файл правят двое):

```
                      Самочувствие видно интерфейсом «Состояние
                      здоровья» на клавише Y — заметка
                      2026-09-14-sostoyanie-zdorovya-sdelano
```

- [ ] **Шаг 6: коммит и пуш**

```bash
git add docs/superpowers/notes/2026-09-14-sostoyanie-zdorovya-sdelano.md CLAUDE.md
git commit -m "Интерфейс состояния здоровья сделан — заметка и передача"
git pull --rebase origin main
git push origin main
```

---

## Самопроверка плана

Пройдено по спеку раздел за разделом:

| Раздел спека | Задача |
|---|---|
| 4. Откуда берутся данные | 4, 6 |
| 5. Слои | 1–14, целиком |
| 6. Система текстов | 1, 2, 3 |
| 7. Экран | 7, 8, 9 |
| 8. HUD | 11 |
| 9. Осмотр соседа | 12 |
| 10. Клирик | 13 |
| 11. Память | 10 |
| 12. Клавиша | 7, 15 (передача в лаунчер) |
| 13. Искажение | 14 |
| 14. Производительность | 6 (кэш), 9 (готовые Component) |
| 15. Проверки | 7, 8, 9, 10, 11, 12, 13, 14, 15 |
| 16. Открытые вопросы | 8 (доли частей тела), 11 (угол HUD), 15 (итог) |

Имена, на которые ссылаются поздние задачи, объявлены в ранних:
`Wellbeing.Часть` (1), `ступень` (1), `клирикЧасть` (13),
`HealthSense.ступень/клирик/часть` (6), `HealthScreen.цель/прямоугольники/
частьПод/правая/протянуть/ступеньДляТекста` (7, 8, 9, 12, 13),
`PlagueNetwork.Look/Impression` (12), `HealthMemory.записать` (10).
