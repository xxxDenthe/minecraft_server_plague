# Пометки Мастера игры в экране здоровья — план реализации

> **Для исполнителей-агентов:** ОБЯЗАТЕЛЬНЫЙ ПОДНАВЫК — `superpowers:subagent-driven-development`
> (рекомендуется) или `superpowers:executing-plans`. Шаги отмечены
> чекбоксами (`- [ ]`), задачи идут строго по порядку.

**Цель.** ГМ правит экран здоровья другого игрока: добавляет строки,
заменяет автотекст, удаляет и одной кнопкой возвращает экран к тому,
что диктуют показатели игрока.

**Подход.** Сервер хранит на игроке список пометок (вложение без
`copyOnDeath`). Клиент получает его пакетом и рисует вместе с
автотекстом. Правки идут обычными командами `/plague health …` под
правом оператора; экран редактора наследует уже существующий
`HealthScreen`; панель ГМ добавляет кнопку в карточку игрока.

**Стек.** Minecraft 1.21.1, NeoForge 21.1.249, Java 21, JUnit 5,
Gradle (свой `gradlew` в каждом моде).

**Спек.** `docs/superpowers/specs/2026-09-16-pometki-gm-zdorovye-design.md` —
план спорит со спеком, читать оба.

## Общие ограничения

- Пометка — **только текст**. Никаких эффектов, урона, скорости,
  заражённости. Новых игровых механик не появляется.
- Ни одно число болезни наружу не выходит: ни стадии, ни заражённости,
  ни порогов — правило базового спека, оно сильнее удобства ГМ.
- В пакете `core` запрещены `import net.minecraft` и `import net.neoforged`
  (стережёт `CorePurityTest`).
- Ни одной видимой строки текста в Java: `core/Marks.java` отдаёт ключи
  локализации, тексты живут в `ru_ru.json` и `en_us.json`. Оба языка
  обязательны — `LangCoverageTest` падает, если ключа нет хоть в одном.
- Имена классов и методов латиницей, поля и локальные переменные —
  как в окружающих файлах (там русские идентификаторы, это норма проекта).
- Комментарии по-русски, в тоне соседних файлов: объясняют «почему»,
  а не пересказывают код.
- В кадре (`render`) ничего не создаётся: ни `Component`, ни списки,
  ни `Random`. Готовое лежит в полях и пересобирается при смене данных.
- Пределы (12 пометок, 120 знаков, чистка `§` и переводов строки)
  проверяются **на сервере**, а не в экране.
- Сообщения коммитов по-русски, с `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Тесты гоняются из папки мода: `cd plaguecore && ./gradlew test`.

---

## Карта файлов

| Файл | Ответственность |
|---|---|
| `plaguecore/src/main/java/dev/denthe/plaguecore/core/Marks.java` | Каталог заготовок, пометка как запись, слияние с автотекстом, чистка строки. Чистый, без Minecraft |
| `plaguecore/src/test/java/dev/denthe/plaguecore/core/MarksTest.java` | Тесты каталога, слияния и чистки |
| `plaguecore/src/main/resources/assets/plaguecore/lang/{ru_ru,en_us}.json` | Тексты заготовок и подписи редактора |
| `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java` | Дополняется ключами пометок |
| `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerHealthMarks.java` | Вложение-хранилище и операции над списком |
| `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java` | +пакет `MarkList`, отправка при осмотре |
| `plaguecore/src/main/java/dev/denthe/plaguecore/mc/HealthMarksCommands.java` | Подкоманды `/plague health` |
| `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java` | Подключение поддерева `health` |
| `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java` | Регистрация вложения |
| `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthMarksClient.java` | Свои и чужие пометки на клиенте, готовые `Component` |
| `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java` | Приём пакета |
| `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java` | Рисует пометки рядом с автотекстом |
| `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthEditScreen.java` | Экран ГМ: крестики, карандаши, «Вернуть как было» |
| `plaguecore/src/main/java/dev/denthe/plaguecore/client/MarkPickerScreen.java` | Выбор заготовки или ввод своей строки |
| `lmpc_gmtools/src/main/java/dev/denthe/gmtools/client/GmPanelScreen.java` | Кнопка «Здоровье…» в карточке игрока |

---

## Задача 1: ядро — каталог заготовок и слияние

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/core/Marks.java`
- Создать: `plaguecore/src/test/java/dev/denthe/plaguecore/core/MarksTest.java`

**Интерфейсы:**
- Потребляет: ничего.
- Отдаёт: `Marks.Место`, `Marks.Заготовка`, `Marks.Пометка`, `Marks.Вывод`,
  `Marks.ПРЕДЕЛ`, `Marks.ДЛИНА_СТРОКИ`, методы `заготовка(String)`,
  `место(String)`, `имя(Заготовка)`, `ключ(Заготовка, boolean)`,
  `ключКлирика(Заготовка)`, `чистить(String)`, `заменяет(Место, List)`,
  `строки(Место, String, List, boolean, boolean)`. Ими пользуются задачи 2–8.

- [ ] **Шаг 1: написать падающий тест**

Создать `MarksTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Пометки Мастера игры. Спек «Пометки Мастера игры в экране здоровья»,
 * разделы 6 и 7.
 *
 * Тест стережёт то же правило, что у Wellbeing: наружу уходят ключи
 * локализации, а своя строка ГМ — как есть, но очищенная.
 */
class MarksTest {

    private static Marks.Пометка заготовка(int id, Marks.Место место, boolean заменяет, Marks.Заготовка з) {
        return new Marks.Пометка(id, место, заменяет, з.идентификатор(), "");
    }

    private static Marks.Пометка своя(int id, Marks.Место место, boolean заменяет, String текст) {
        return new Marks.Пометка(id, место, заменяет, "", текст);
    }

    @Test
    void безПометокОстаётсяТолькоАвтотекст() {
        List<Marks.Вывод> вывод = Marks.строки(
            Marks.Место.ARMS, "авто.руки", List.of(), false, false);
        assertEquals(1, вывод.size());
        assertEquals("авто.руки", вывод.get(0).ключ());
    }

    @Test
    void добавкаИдётПослеАвтотекста() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.ARMS, false, Marks.Заготовка.FRACTURE)), false, false);
        assertEquals(2, вывод.size());
        assertEquals("авто.руки", вывод.get(0).ключ());
        assertEquals(Marks.ключ(Marks.Заготовка.FRACTURE, false), вывод.get(1).ключ());
    }

    @Test
    void заменаСъедаетАвтотекст() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.ARMS, true, Marks.Заготовка.BURN)), false, false);
        assertEquals(1, вывод.size());
        assertEquals(Marks.ключ(Marks.Заготовка.BURN, false), вывод.get(0).ключ());
    }

    @Test
    void чужойВзглядБерётДругойКлюч() {
        List<Marks.Вывод> своё = Marks.строки(Marks.Место.LEGS, null,
            List.of(заготовка(1, Marks.Место.LEGS, false, Marks.Заготовка.WOUND)), false, false);
        List<Marks.Вывод> чужое = Marks.строки(Marks.Место.LEGS, null,
            List.of(заготовка(1, Marks.Место.LEGS, false, Marks.Заготовка.WOUND)), true, false);
        assertNotEquals(своё.get(0).ключ(), чужое.get(0).ключ());
    }

    @Test
    void клирикВидитВторуюСтрокуТусклой() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.HEAD, null,
            List.of(заготовка(1, Marks.Место.HEAD, false, Marks.Заготовка.CONCUSSION)), true, true);
        assertEquals(2, вывод.size());
        assertFalse(вывод.get(0).тусклый());
        assertTrue(вывод.get(1).тусклый());
        assertEquals(Marks.ключКлирика(Marks.Заготовка.CONCUSSION), вывод.get(1).ключ());
    }

    @Test
    void свояСтрокаОтдаётсяТекстомИОдинаковаВсем() {
        var п = своя(1, Marks.Место.OVERALL, false, "Рука в лубке.");
        List<Marks.Вывод> своё = Marks.строки(Marks.Место.OVERALL, null, List.of(п), false, false);
        List<Marks.Вывод> чужое = Marks.строки(Marks.Место.OVERALL, null, List.of(п), true, true);
        assertNull(своё.get(0).ключ());
        assertEquals("Рука в лубке.", своё.get(0).текст());
        assertEquals(своё, чужое, "у своей строки нет ни чужого варианта, ни строки Клирика");
    }

    @Test
    void чужиеМестаНеПопадаютВВывод() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.ARMS, "авто.руки",
            List.of(заготовка(1, Marks.Место.LEGS, true, Marks.Заготовка.FRACTURE)), false, false);
        assertEquals(1, вывод.size(), "пометка ног не трогает руки — ни заменой, ни добавкой");
        assertEquals("авто.руки", вывод.get(0).ключ());
    }

    @Test
    void порядокДобавленияСохраняется() {
        List<Marks.Вывод> вывод = Marks.строки(Marks.Место.TORSO, null, List.of(
            заготовка(1, Marks.Место.TORSO, false, Marks.Заготовка.WOUND),
            заготовка(2, Marks.Место.TORSO, false, Marks.Заготовка.BLEEDING)), false, false);
        assertEquals(Marks.ключ(Marks.Заготовка.WOUND, false), вывод.get(0).ключ());
        assertEquals(Marks.ключ(Marks.Заготовка.BLEEDING, false), вывод.get(1).ключ());
    }

    @Test
    void укаждойЗаготовкиСвоиКлючи() {
        for (Marks.Заготовка а : Marks.Заготовка.values()) {
            for (Marks.Заготовка б : Marks.Заготовка.values()) {
                if (а == б) continue;
                assertNotEquals(Marks.ключ(а, false), Marks.ключ(б, false));
                assertNotEquals(Marks.имя(а), Marks.имя(б));
            }
            assertNotEquals(Marks.ключ(а, false), Marks.ключ(а, true));
            assertNotEquals(Marks.ключ(а, false), Marks.ключКлирика(а));
        }
    }

    @Test
    void неизвестноеИмяМолчит() {
        assertNull(Marks.заготовка("somemod:quantum_flux"));
        assertNull(Marks.заготовка(null));
        assertNull(Marks.место("ухо"));
    }

    @Test
    void разборИмёнНеЧувствителенКРегистру() {
        assertEquals(Marks.Заготовка.FEVER, Marks.заготовка("FeVeR"));
        assertEquals(Marks.Место.HEAD, Marks.место("Head"));
    }

    @Test
    void чисткаСрезаетФорматированиеИДлину() {
        assertEquals("Рука в лубке.", Marks.чистить("  Рука в лубке.  "));
        assertEquals("красное", Marks.чистить("§cкрасное"));
        assertEquals("две строки", Marks.чистить("две\nстроки").replace("  ", " "));
        assertEquals(Marks.ДЛИНА_СТРОКИ, Marks.чистить("я".repeat(500)).length());
        assertEquals("", Marks.чистить(null));
    }
}
```

- [ ] **Шаг 2: убедиться, что тест падает**

```
cd plaguecore && ./gradlew test --tests '*MarksTest*'
```

Ожидаем: ошибка компиляции, `Marks` не существует.

- [ ] **Шаг 3: написать `core/Marks.java`**

```java
package dev.denthe.plaguecore.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Пометки Мастера игры поверх экрана здоровья.
 * Спек «Пометки Мастера игры в экране здоровья», разделы 6 и 7.
 *
 * Класс не знает ни о Minecraft, ни о текстах: наружу уходят ключи
 * локализации, а своя строка ГМ — очищенной. Всё слияние автотекста
 * с пометками — чистая функция, и потому проверяется обычным JUnit,
 * без запуска игры, под CorePurityTest.
 *
 * Пометка ничего не делает в игре. Это текст, а не механика: «перелом»
 * ощущается потому, что человек его читает и отыгрывает, а не потому,
 * что мод отнял скорость.
 */
public final class Marks {
    private Marks() {}

    /** Сколько пометок держим на игроке. Больше — правая врезка перестаёт читаться. */
    public static final int ПРЕДЕЛ = 12;

    /** Длина своей строки ГМ. Длиннее не влезает во врезку. */
    public static final int ДЛИНА_СТРОКИ = 120;

    private static final String КОРЕНЬ = "plaguecore.health.mark.";

    /** Куда ложится пометка. Первые пять мест совпадают с разделами экрана. */
    public enum Место { OVERALL, HEAD, TORSO, ARMS, LEGS, FEEL, MEMORY }

    /**
     * Готовые состояния. Список нарочно короткий и бытовой: это то,
     * что ГМ называет словами на сессии, а не медицинский справочник.
     */
    public enum Заготовка {
        FRACTURE, SPRAIN, WOUND, BURN, FROSTBITE,
        FEVER, BLEEDING, EXHAUSTION, CONCUSSION, ROT;

        public String идентификатор() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * Одна пометка. Заполнено ровно одно из двух последних полей:
     * либо заготовка из каталога, либо своя строка ГМ.
     *
     * @param id       номер в пределах игрока, по нему удаляют
     * @param заменяет true — автотекст этого места не показывается вовсе
     */
    public record Пометка(int id, Место место, boolean заменяет, String заготовка, String текст) {
        public boolean своя() { return заготовка == null || заготовка.isEmpty(); }
    }

    /**
     * Готовая строка для экрана: либо ключ локализации, либо текст.
     * Второе поле всегда пустое, когда заполнено первое.
     */
    public record Вывод(String ключ, String текст, boolean тусклый) {}

    /** Название заготовки в списке у ГМ. Игрок его не видит. */
    public static String имя(Заготовка з) {
        return КОРЕНЬ + з.идентификатор() + ".name";
    }

    /** Что чувствует сам человек ({@code чужой = false}) и что видно со стороны. */
    public static String ключ(Заготовка з, boolean чужой) {
        return КОРЕНЬ + з.идентификатор() + (чужой ? ".other" : ".self");
    }

    /** Что замечает Клирик сверх обычного взгляда. Вторая строка, а не замена. */
    public static String ключКлирика(Заготовка з) {
        return КОРЕНЬ + з.идентификатор() + ".cleric";
    }

    /** Заготовка по имени. {@code null}, если такой нет: команда откажет внятно. */
    public static Заготовка заготовка(String идентификатор) {
        if (идентификатор == null) return null;
        for (Заготовка з : Заготовка.values()) {
            if (з.name().equalsIgnoreCase(идентификатор)) return з;
        }
        return null;
    }

    /** Место по имени. {@code null}, если такого нет. */
    public static Место место(String имя) {
        if (имя == null) return null;
        for (Место м : Место.values()) {
            if (м.name().equalsIgnoreCase(имя)) return м;
        }
        return null;
    }

    /**
     * Своя строка ГМ, приведённая в годный вид: без форматирования,
     * без переносов, не длиннее врезки.
     *
     * Чистим здесь, а не в экране: экран можно подменить, ядро — нет.
     */
    public static String чистить(String строка) {
        if (строка == null) return "";
        String чисто = строка.replace('\n', ' ').replace('\r', ' ').replace("§", "").trim();
        return чисто.length() > ДЛИНА_СТРОКИ ? чисто.substring(0, ДЛИНА_СТРОКИ) : чисто;
    }

    /** Есть ли у места пометка-замена. Экран по этому прячет автосписок. */
    public static boolean заменяет(Место место, List<Пометка> пометки) {
        for (Пометка п : пометки) {
            if (п.место() == место && п.заменяет()) return true;
        }
        return false;
    }

    /**
     * Автотекст и пометки места одним списком.
     *
     * @param автоКлюч ключ автотекста или {@code null}, если его нет
     *                 (у «Ощущений» и «Журнала» автосодержимое — список,
     *                  и экран мешает его сам)
     * @param чужой    смотрит сосед, а не сам человек
     * @param клирик   у смотрящего класс Клирика: к заготовкам добавляется
     *                 вторая, тусклая строка
     */
    public static List<Вывод> строки(Место место, String автоКлюч, List<Пометка> пометки,
                                     boolean чужой, boolean клирик) {
        List<Вывод> вывод = new ArrayList<>();
        if (автоКлюч != null && !заменяет(место, пометки)) {
            вывод.add(new Вывод(автоКлюч, null, false));
        }
        for (Пометка п : пометки) {
            if (п.место() != место) continue;
            if (п.своя()) {
                вывод.add(new Вывод(null, чистить(п.текст()), false));
                continue;
            }
            Заготовка з = заготовка(п.заготовка());
            if (з == null) continue;                 // незнакомая молчит, а не показывает id
            вывод.add(new Вывод(ключ(з, чужой), null, false));
            if (клирик) вывод.add(new Вывод(ключКлирика(з), null, true));
        }
        return List.copyOf(вывод);
    }
}
```

- [ ] **Шаг 4: убедиться, что тесты проходят**

```
cd plaguecore && ./gradlew test --tests '*MarksTest*' --tests '*CorePurityTest*'
```

Ожидаем: BUILD SUCCESSFUL, оба класса зелёные.

- [ ] **Шаг 5: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/core/Marks.java \
        plaguecore/src/test/java/dev/denthe/plaguecore/core/MarksTest.java
git commit -m "Ядро пометок: каталог заготовок и слияние с автотекстом"
```

---

## Задача 2: тексты заготовок и подписи редактора

**Файлы:**
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/ru_ru.json`
- Изменить: `plaguecore/src/main/resources/assets/plaguecore/lang/en_us.json`
- Изменить: `plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java`

**Интерфейсы:**
- Потребляет: `Marks.имя`, `Marks.ключ`, `Marks.ключКлирика` из задачи 1.
- Отдаёт: ключи `plaguecore.health.mark.*` и `plaguecore.health.edit.*`
  для задач 5, 7 и 8.

- [ ] **Шаг 1: расширить `LangCoverageTest` (тест падает первым)**

В метод `всеКлючи()` дописать перед `return`:

```java
        for (Marks.Заготовка з : Marks.Заготовка.values()) {
            ключи.add(Marks.имя(з));
            ключи.add(Marks.ключ(з, false));
            ключи.add(Marks.ключ(з, true));
            ключи.add(Marks.ключКлирика(з));
        }
        ключи.add("plaguecore.health.edit.title");
        ключи.add("plaguecore.health.edit.add");
        ключи.add("plaguecore.health.edit.reset");
        ключи.add("plaguecore.health.edit.reset.confirm");
        ключи.add("plaguecore.health.edit.own");
        ключи.add("plaguecore.health.edit.replace");
        ключи.add("plaguecore.health.edit.append");
        ключи.add("plaguecore.health.edit.empty");
        ключи.add("plaguecore.health.edit.hidden");
        ключи.add("plaguecore.health.edit.place");
```

Импорт в шапке файла: `import dev.denthe.plaguecore.core.Marks;`

- [ ] **Шаг 2: убедиться, что тест падает**

```
cd plaguecore && ./gradlew test --tests '*LangCoverageTest*'
```

Ожидаем: FAIL со списком «нет перевода: ru: plaguecore.health.mark.fracture.name, …».

- [ ] **Шаг 3: дописать `ru_ru.json`**

Вставить в алфавитном порядке рядом с остальными `plaguecore.health.*`.
Тон — ощущение, а не диагноз; в `.other` — наблюдение со стороны;
в `.cleric` — подробность, которую замечает знающий человек.

```json
  "plaguecore.health.mark.fracture.name": "Перелом",
  "plaguecore.health.mark.fracture.self": "Кость не держит. Каждое движение отдаёт болью до самого плеча.",
  "plaguecore.health.mark.fracture.other": "Он бережёт конечность и старается её не нагружать.",
  "plaguecore.health.mark.fracture.cleric": "Кость сломана. Без лубка и покоя срастётся криво.",

  "plaguecore.health.mark.sprain.name": "Вывих",
  "plaguecore.health.mark.sprain.self": "Сустав сидит не на месте, и повернуть его я не решаюсь.",
  "plaguecore.health.mark.sprain.other": "Сустав у него вывернут неправильно.",
  "plaguecore.health.mark.sprain.cleric": "Сустав вышел из гнезда. Вправить — и станет легче сразу.",

  "plaguecore.health.mark.wound.name": "Рана",
  "plaguecore.health.mark.wound.self": "Под повязкой тянет и дёргает. Рана свежая.",
  "plaguecore.health.mark.wound.other": "Сквозь его тряпьё проступает тёмное пятно.",
  "plaguecore.health.mark.wound.cleric": "Рана открыта и грязна. Промыть, пока не загноилась.",

  "plaguecore.health.mark.burn.name": "Ожог",
  "plaguecore.health.mark.burn.self": "Кожа горит и стягивается. Дотронуться до неё невозможно.",
  "plaguecore.health.mark.burn.other": "Кожа у него стянута и блестит от ожога.",
  "plaguecore.health.mark.burn.cleric": "Ожог глубокий. Кожа сойдёт, и сойдёт скоро.",

  "plaguecore.health.mark.frostbite.name": "Обморожение",
  "plaguecore.health.mark.frostbite.self": "Я не чувствую ни холода, ни прикосновений. Только мёртвую тяжесть.",
  "plaguecore.health.mark.frostbite.other": "Кожа у него восковая и странно белая.",
  "plaguecore.health.mark.frostbite.cleric": "Плоть отморожена. Отогревать медленно, иначе будет хуже.",

  "plaguecore.health.mark.fever.name": "Лихорадка",
  "plaguecore.health.mark.fever.self": "Меня трясёт, и мысли путаются от жара.",
  "plaguecore.health.mark.fever.other": "Его бьёт озноб, на лбу испарина.",
  "plaguecore.health.mark.fever.cleric": "Жар высокий. К ночи будет бредить.",

  "plaguecore.health.mark.bleeding.name": "Кровопотеря",
  "plaguecore.health.mark.bleeding.self": "В глазах темнеет, и меня ведёт в сторону.",
  "plaguecore.health.mark.bleeding.other": "Он бледен до серости и держится за стену.",
  "plaguecore.health.mark.bleeding.cleric": "Крови потеряно много. Ему нельзя вставать.",

  "plaguecore.health.mark.exhaustion.name": "Истощение",
  "plaguecore.health.mark.exhaustion.self": "Сил нет совсем. Даже стоять — работа.",
  "plaguecore.health.mark.exhaustion.other": "Он держится на одном упрямстве.",
  "plaguecore.health.mark.exhaustion.cleric": "Тело съело само себя. Нужна еда и несколько дней покоя.",

  "plaguecore.health.mark.concussion.name": "Сотрясение",
  "plaguecore.health.mark.concussion.self": "Всё плывёт, и звуки доходят с опозданием.",
  "plaguecore.health.mark.concussion.other": "Взгляд у него плавает и ни на чём не держится.",
  "plaguecore.health.mark.concussion.cleric": "Голова отбита. Ему нельзя ни света, ни шума.",

  "plaguecore.health.mark.rot.name": "Гниль",
  "plaguecore.health.mark.rot.self": "От меня пахнет сладкой гнилью, и я не могу перестать это чувствовать.",
  "plaguecore.health.mark.rot.other": "От него тянет сладковатым запахом порчи.",
  "plaguecore.health.mark.rot.cleric": "Плоть гниёт заживо. Вырезать — или потерять всё.",

  "plaguecore.health.edit.title": "Правка состояния",
  "plaguecore.health.edit.add": "+ добавить",
  "plaguecore.health.edit.reset": "Вернуть как было",
  "plaguecore.health.edit.reset.confirm": "Точно вернуть?",
  "plaguecore.health.edit.own": "Своя строка",
  "plaguecore.health.edit.replace": "Заменить автотекст",
  "plaguecore.health.edit.append": "Дописать ниже",
  "plaguecore.health.edit.empty": "Пометок нет.",
  "plaguecore.health.edit.hidden": "Своё игрок видит сам — сюда это не приходит.",
  "plaguecore.health.edit.place": "Куда",
```

- [ ] **Шаг 4: дописать `en_us.json` теми же ключами**

Английский — тот же смысл и тот же тон, не подстрочник:

```json
  "plaguecore.health.mark.fracture.name": "Fracture",
  "plaguecore.health.mark.fracture.self": "The bone gives under me. Every movement shoots pain up to the shoulder.",
  "plaguecore.health.mark.fracture.other": "He favours the limb and keeps the weight off it.",
  "plaguecore.health.mark.fracture.cleric": "The bone is broken. Without a splint it will set crooked.",

  "plaguecore.health.mark.sprain.name": "Dislocation",
  "plaguecore.health.mark.sprain.self": "The joint sits wrong, and I dare not turn it.",
  "plaguecore.health.mark.sprain.other": "The joint is twisted out of place.",
  "plaguecore.health.mark.sprain.cleric": "The joint is out of its socket. Set it and the pain will ease at once.",

  "plaguecore.health.mark.wound.name": "Wound",
  "plaguecore.health.mark.wound.self": "It pulls and throbs under the bandage. The wound is fresh.",
  "plaguecore.health.mark.wound.other": "A dark stain is spreading through his rags.",
  "plaguecore.health.mark.wound.cleric": "The wound is open and filthy. Wash it before it festers.",

  "plaguecore.health.mark.burn.name": "Burn",
  "plaguecore.health.mark.burn.self": "The skin burns and tightens. I cannot bear to touch it.",
  "plaguecore.health.mark.burn.other": "His skin is drawn tight and shines where it burned.",
  "plaguecore.health.mark.burn.cleric": "A deep burn. The skin will come away, and soon.",

  "plaguecore.health.mark.frostbite.name": "Frostbite",
  "plaguecore.health.mark.frostbite.self": "I feel neither cold nor touch there. Only dead weight.",
  "plaguecore.health.mark.frostbite.other": "His skin is waxy and strangely white.",
  "plaguecore.health.mark.frostbite.cleric": "The flesh is frozen. Warm it slowly, or it will be worse.",

  "plaguecore.health.mark.fever.name": "Fever",
  "plaguecore.health.mark.fever.self": "I am shaking, and the heat tangles my thoughts.",
  "plaguecore.health.mark.fever.other": "Chills run through him, and his brow is damp.",
  "plaguecore.health.mark.fever.cleric": "The fever is high. He will be raving by nightfall.",

  "plaguecore.health.mark.bleeding.name": "Blood loss",
  "plaguecore.health.mark.bleeding.self": "My sight darkens and the ground tilts under me.",
  "plaguecore.health.mark.bleeding.other": "He is grey in the face and leans on the wall.",
  "plaguecore.health.mark.bleeding.cleric": "He has lost too much blood. He must not stand.",

  "plaguecore.health.mark.exhaustion.name": "Exhaustion",
  "plaguecore.health.mark.exhaustion.self": "There is nothing left in me. Even standing is work.",
  "plaguecore.health.mark.exhaustion.other": "He is held up by stubbornness alone.",
  "plaguecore.health.mark.exhaustion.cleric": "The body has eaten itself. It needs food and days of rest.",

  "plaguecore.health.mark.concussion.name": "Concussion",
  "plaguecore.health.mark.concussion.self": "Everything swims, and sounds reach me late.",
  "plaguecore.health.mark.concussion.other": "His gaze drifts and settles on nothing.",
  "plaguecore.health.mark.concussion.cleric": "The head is struck badly. No light for him, and no noise.",

  "plaguecore.health.mark.rot.name": "Rot",
  "plaguecore.health.mark.rot.self": "I smell of sweet rot, and I cannot stop noticing it.",
  "plaguecore.health.mark.rot.other": "A sweetish smell of spoilage comes off him.",
  "plaguecore.health.mark.rot.cleric": "The flesh is rotting while he lives. Cut it away or lose all of it.",

  "plaguecore.health.edit.title": "Editing condition",
  "plaguecore.health.edit.add": "+ add",
  "plaguecore.health.edit.reset": "Restore as it was",
  "plaguecore.health.edit.reset.confirm": "Restore for certain?",
  "plaguecore.health.edit.own": "Own line",
  "plaguecore.health.edit.replace": "Replace the automatic text",
  "plaguecore.health.edit.append": "Add a line below",
  "plaguecore.health.edit.empty": "No marks.",
  "plaguecore.health.edit.hidden": "The player sees their own here — it does not reach you.",
  "plaguecore.health.edit.place": "Where",
```

- [ ] **Шаг 5: убедиться, что тесты проходят**

```
cd plaguecore && ./gradlew test
```

Ожидаем: BUILD SUCCESSFUL, весь набор зелёный (JSON валиден — падение теста
с ошибкой чтения означает лишнюю запятую).

- [ ] **Шаг 6: коммит**

```bash
git add plaguecore/src/main/resources/assets/plaguecore/lang \
        plaguecore/src/test/java/dev/denthe/plaguecore/LangCoverageTest.java
git commit -m "Тексты заготовок пометок и подписи редактора, оба языка"
```

---

## Задача 3: хранилище на игроке

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerHealthMarks.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java` (рядом со строкой `PlayerPlagueData.register(modEventBus);`)

**Интерфейсы:**
- Потребляет: `Marks.Пометка`, `Marks.Место`, `Marks.ПРЕДЕЛ`, `Marks.чистить`.
- Отдаёт: `PlayerHealthMarks.список(Player)`, `добавить(...)`, `удалить(...)`,
  `очистить(...)`, `ПОМЕТКИ` (тип вложения), `КОДЕК_ПОМЕТКИ` (для пакета
  в задаче 4).

- [ ] **Шаг 1: написать `PlayerHealthMarks.java`**

```java
package dev.denthe.plaguecore.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Marks;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Пометки Мастера игры на отдельно взятом игроке.
 * Спек «Пометки Мастера игры в экране здоровья», раздел 6.3.
 *
 * Хранится ванильным Data Attachment — тем же способом, что
 * {@link PlayerPlagueData}: NeoForge сам кладёт это в файл игрока
 * и сам достаёт при входе, своего кода сохранения не пишем.
 *
 * copyOnDeath здесь намеренно НЕ стоит, в отличие от чумы. Решение
 * владельца: пометка живёт до снятия ГМ или до смерти. Вложение без
 * этого флага не переживает смерть само собой, поэтому кода очистки
 * не нужно вовсе.
 */
public class PlayerHealthMarks {

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PlagueCore.MODID);

    /** Пометки в порядке добавления. */
    public List<Marks.Пометка> пометки = new ArrayList<>();

    /** Следующий свободный номер. Хранится, иначе после перезахода номера повторятся. */
    public int следующийId = 1;

    public PlayerHealthMarks() {}

    public PlayerHealthMarks(List<Marks.Пометка> пометки, int следующийId) {
        this.пометки = new ArrayList<>(пометки);
        this.следующийId = следующийId;
    }

    /** Кодек одной пометки. Им же пользуется пакет {@code MarkList}. */
    public static final Codec<Marks.Пометка> КОДЕК_ПОМЕТКИ = RecordCodecBuilder.create(и -> и.group(
        Codec.INT.fieldOf("id").forGetter(Marks.Пометка::id),
        Codec.STRING.fieldOf("place").forGetter(п -> п.место().name()),
        Codec.BOOL.fieldOf("replaces").forGetter(Marks.Пометка::заменяет),
        Codec.STRING.optionalFieldOf("preset", "").forGetter(Marks.Пометка::заготовка),
        Codec.STRING.optionalFieldOf("text", "").forGetter(Marks.Пометка::текст)
    ).apply(и, (id, место, заменяет, заготовка, текст) -> {
        Marks.Место м = Marks.место(место);
        return new Marks.Пометка(id, м == null ? Marks.Место.OVERALL : м,
            заменяет, заготовка, текст);
    }));

    public static final Codec<PlayerHealthMarks> CODEC = RecordCodecBuilder.create(и -> и.group(
        КОДЕК_ПОМЕТКИ.listOf().fieldOf("marks").forGetter(д -> д.пометки),
        Codec.INT.fieldOf("nextId").forGetter(д -> д.следующийId)
    ).apply(и, PlayerHealthMarks::new));

    public static final Supplier<AttachmentType<PlayerHealthMarks>> ПОМЕТКИ =
        ВЛОЖЕНИЯ.register("player_health_marks", () -> AttachmentType
            .builder(PlayerHealthMarks::new)
            .serialize(CODEC)
            .build());

    public static PlayerHealthMarks данные(Player игрок) {
        return игрок.getData(ПОМЕТКИ.get());
    }

    /** Пометки игрока, только для чтения. */
    public static List<Marks.Пометка> список(Player игрок) {
        return List.copyOf(данные(игрок).пометки);
    }

    /**
     * Добавить пометку. Пределы проверяются здесь, а не в экране: экран
     * можно подменить, сервер — нет.
     *
     * @return номер новой пометки или −1, если предел уже выбран
     *         или обе строки пусты
     */
    public static int добавить(Player игрок, Marks.Место место, boolean заменяет,
                               String заготовка, String текст) {
        PlayerHealthMarks д = данные(игрок);
        if (д.пометки.size() >= Marks.ПРЕДЕЛ) return -1;

        String чистыйТекст = Marks.чистить(текст);
        boolean естьЗаготовка = заготовка != null && !заготовка.isEmpty();
        if (!естьЗаготовка && чистыйТекст.isEmpty()) return -1;

        int id = д.следующийId++;
        д.пометки.add(new Marks.Пометка(id, место, заменяет,
            естьЗаготовка ? заготовка.toLowerCase(java.util.Locale.ROOT) : "",
            естьЗаготовка ? "" : чистыйТекст));
        игрок.setData(ПОМЕТКИ.get(), д);
        return id;
    }

    /** @return {@code true}, если такая пометка была */
    public static boolean удалить(Player игрок, int id) {
        PlayerHealthMarks д = данные(игрок);
        boolean было = д.пометки.removeIf(п -> п.id() == id);
        if (было) игрок.setData(ПОМЕТКИ.get(), д);
        return было;
    }

    /** Вернуть экран к тому, что диктуют показатели: снять все пометки. */
    public static void очистить(Player игрок) {
        PlayerHealthMarks д = данные(игрок);
        д.пометки.clear();
        игрок.setData(ПОМЕТКИ.get(), д);
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
```

- [ ] **Шаг 2: зарегистрировать вложение**

В `PlagueCore.java` после строки `dev.denthe.plaguecore.mc.PlayerPlagueData.register(modEventBus);`
добавить:

```java
        dev.denthe.plaguecore.mc.PlayerHealthMarks.register(modEventBus);
```

- [ ] **Шаг 3: собрать**

```
cd plaguecore && ./gradlew build
```

Ожидаем: BUILD SUCCESSFUL. Живьём вложение проверяется в задаче 9,
пункты про смерть и перезапуск.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlayerHealthMarks.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java
git commit -m "Хранилище пометок на игроке: вложение без copyOnDeath"
```

---

## Задача 4: пакет и клиентское хранилище

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueNetwork.java`
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthMarksClient.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/PlagueClientAccess.java`

**Интерфейсы:**
- Потребляет: `PlayerHealthMarks.список`, `Marks.Пометка`.
- Отдаёт: `PlagueNetwork.MarkList(int сущность, byte режим, byte ступень, List<Marks.Пометка> пометки)`,
  `PlagueNetwork.отправитьПометки(ServerPlayer кому, Player чьи, byte режим)`,
  `HealthMarksClient.свои()`, `.чужие(int)`, `.версия()`, `.принять(...)`, `.забыть()`.
  Режимы: `0` — показ, `1` — редактор ГМ. Поле `ступень` заполняется только
  для режима 1: свою ступень клиент и так знает пакетом `Stage`, а чужую
  при осмотре приносит `Impression`.

- [ ] **Шаг 1: добавить пакет в `PlagueNetwork`**

Рядом с записью `Impression`:

```java
    /**
     * Пометки Мастера игры. Один пакет на три случая, потому что данные
     * во всех трёх одни и те же:
     *   режим 0 — показ: свои пометки или пометки осматриваемого соседа;
     *   режим 1 — редактор: пакет адресован ГМ, экран открывается
     *             или обновляется, если уже открыт.
     *
     * Поле «ступень» заполняется только для режима 1: ГМ — оператор,
     * и без ступени его редактор не смог бы показать автотекст болезни,
     * то есть ровно то, что видит игрок. Обычному игроку ступень тут
     * не отдаётся: свою он знает пакетом Stage, чужую — Impression.
     */
    public record MarkList(int сущность, byte режим, byte ступень, List<Marks.Пометка> пометки)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<MarkList> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "marks"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MarkList> CODEC =
            StreamCodec.of(
                (buf, м) -> {
                    buf.writeVarInt(м.сущность);
                    buf.writeByte(м.режим);
                    buf.writeByte(м.ступень);
                    buf.writeVarInt(м.пометки.size());
                    for (Marks.Пометка п : м.пометки) {
                        buf.writeVarInt(п.id());
                        buf.writeVarInt(п.место().ordinal());
                        buf.writeBoolean(п.заменяет());
                        buf.writeUtf(п.заготовка(), 32);
                        buf.writeUtf(п.текст(), Marks.ДЛИНА_СТРОКИ);
                    }
                },
                buf -> {
                    int сущность = buf.readVarInt();
                    byte режим = buf.readByte();
                    byte ступень = buf.readByte();
                    int n = buf.readVarInt();
                    if (n < 0 || n > Marks.ПРЕДЕЛ) {
                        throw new IllegalArgumentException("подозрительное число пометок: " + n);
                    }
                    List<Marks.Пометка> список = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        int id = buf.readVarInt();
                        int место = buf.readVarInt();
                        Marks.Место[] места = Marks.Место.values();
                        список.add(new Marks.Пометка(id,
                            места[Mth.clamp(место, 0, места.length - 1)],
                            buf.readBoolean(),
                            buf.readUtf(32),
                            buf.readUtf(Marks.ДЛИНА_СТРОКИ)));
                    }
                    return new MarkList(сущность, режим, ступень, List.copyOf(список));
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
```

Импорты в шапке файла, если их ещё нет: `dev.denthe.plaguecore.core.Marks`,
`java.util.ArrayList`, `net.minecraft.util.Mth`.

- [ ] **Шаг 2: зарегистрировать пакет и отправку**

В методе регистрации, рядом с `registrar.playToClient(Impression.TYPE, …)`:

```java
        registrar.playToClient(MarkList.TYPE, MarkList.CODEC,
            (пакет, контекст) -> контекст.enqueueWork(
                () -> PlagueClientAccess.принятьПометки(пакет)));
```

Там же, рядом с `отправитьГолос`:

```java
    /**
     * Послать пометки игрока получателю. Режим 0 — показ, 1 — редактор.
     * Одна точка отправки на все случаи: и вход в мир, и осмотр соседа,
     * и правка ГМ ходят сюда.
     */
    public static void отправитьПометки(ServerPlayer кому, Player чьи, byte режим) {
        byte ступень = режим == 1 ? (byte) PlayerPlagueData.данные(чьи).стадия : 0;
        PacketDistributor.sendToPlayer(кому,
            new MarkList(чьи.getId(), режим, ступень, PlayerHealthMarks.список(чьи)));
    }
```

В `осмотреть(...)` — **перед** отправкой `Impression`:

```java
        // Пометки уходят раньше впечатления: Impression открывает экран,
        // и к этому моменту список уже обязан лежать на клиенте.
        отправитьПометки(кто, цель, (byte) 0);
```

- [ ] **Шаг 3: отправлять свои пометки при входе в мир**

В `PlayerHealthMarks` добавить обработчик (класс помечается
`@EventBusSubscriber(modid = PlagueCore.MODID)`):

```java
    /** При входе игрок получает свои пометки: иначе экран будет пуст до первой правки. */
    @SubscribeEvent
    public static void приВходе(PlayerEvent.PlayerLoggedInEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) {
            PlagueNetwork.отправитьПометки(игрок, игрок, (byte) 0);
        }
    }
```

- [ ] **Шаг 4: написать `HealthMarksClient.java`**

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Marks;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Пометки Мастера игры на клиенте: свои и тех, кого осматриваем.
 * Спек «Пометки Мастера игры», раздел 8.
 *
 * Версия растёт при каждом приёме пакета. По ней экран понимает, что
 * готовые {@code Component} пора пересобрать, — собирать их в кадре
 * нельзя, это то же правило, что в разделе 14 базового спека.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthMarksClient {
    private HealthMarksClient() {}

    private static List<Marks.Пометка> свои = List.of();
    private static final Map<Integer, List<Marks.Пометка>> чужие = new HashMap<>();
    private static int версия;

    public static List<Marks.Пометка> свои() { return свои; }

    /** Пометки соседа. Пусто, если сервер про него ничего не присылал. */
    public static List<Marks.Пометка> чужие(int сущность) {
        return чужие.getOrDefault(сущность, List.of());
    }

    /** Растёт при каждом изменении. Экран держит её у своего кэша. */
    public static int версия() { return версия; }

    public static void принять(PlagueNetwork.MarkList пакет) {
        Minecraft mc = Minecraft.getInstance();
        boolean моё = mc.player != null && mc.player.getId() == пакет.сущность();
        if (моё) свои = пакет.пометки();
        else чужие.put(пакет.сущность(), пакет.пометки());
        версия++;

        if (пакет.режим() == 1) HealthEditScreen.принять(пакет);
    }

    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        забыть();
    }

    public static void забыть() {
        свои = List.of();
        чужие.clear();
        версия++;
    }
}
```

- [ ] **Шаг 5: приём в `PlagueClientAccess`**

```java
    /** Пометки Мастера игры. Разбирает {@link HealthMarksClient}. */
    public static void принятьПометки(PlagueNetwork.MarkList пакет) {
        HealthMarksClient.принять(пакет);
    }
```

- [ ] **Шаг 6: временная заглушка `HealthEditScreen`**

Чтобы задача собиралась до задачи 7, создать файл
`client/HealthEditScreen.java` с одним методом:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.mc.PlagueNetwork;

/** Экран правки ГМ. Полностью пишется в задаче 7 плана. */
public final class HealthEditScreen {
    private HealthEditScreen() {}

    public static void принять(PlagueNetwork.MarkList пакет) {
        // задача 7: открыть или обновить экран
    }
}
```

- [ ] **Шаг 7: собрать**

```
cd plaguecore && ./gradlew build
```

Ожидаем: BUILD SUCCESSFUL.

- [ ] **Шаг 8: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore
git commit -m "Пакет пометок и клиентское хранилище"
```

---

## Задача 5: пометки в экране здоровья

**Файлы:**
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java`

**Интерфейсы:**
- Потребляет: `HealthMarksClient.свои/чужие/версия`, `Marks.строки`, `Marks.заменяет`.
- Отдаёт: защищённые методы `пометки()`, `строкиМеста(Marks.Место, String)`,
  `нарисоватьСтроки(...)` — ими пользуется `HealthEditScreen` в задаче 7.

- [ ] **Шаг 1: добавить в `HealthScreen` кэш и сборку строк**

```java
    /** Пометки того, кого осматриваем: свои или соседа. */
    protected List<Marks.Пометка> пометки() {
        return чужой ? HealthMarksClient.чужие(ктоЧужой == null ? -1 : ктоЧужой.getId())
                     : HealthMarksClient.свои();
    }

    /**
     * Кэш готовых строк места: пересобирается, когда меняется место,
     * ступень или версия пометок, но не в кадре. Собирать Component
     * на каждый кадр — тот же дефект, что уже чинили в базовом спеке.
     */
    private Marks.Место кэшМесто;
    private int кэшСтупень = Integer.MIN_VALUE;
    private int кэшВерсия = -1;
    private boolean кэшКлирик;
    private List<Component> кэшСтроки = List.of();
    private List<Boolean> кэшТусклые = List.of();

    /** Готовые строки места: автотекст и пометки вперемешку, в порядке вывода. */
    protected List<Component> строкиМеста(Marks.Место место, String автоКлюч) {
        boolean клирик = HealthSense.клирик();
        int ступень = ступеньДляТекста();
        if (место != кэшМесто || ступень != кэшСтупень
                || HealthMarksClient.версия() != кэшВерсия || клирик != кэшКлирик) {
            кэшМесто = место;
            кэшСтупень = ступень;
            кэшВерсия = HealthMarksClient.версия();
            кэшКлирик = клирик;

            List<Component> строки = new ArrayList<>();
            List<Boolean> тусклые = new ArrayList<>();
            for (Marks.Вывод в : Marks.строки(место, автоКлюч, пометки(), чужой, клирик)) {
                строки.add(в.ключ() != null
                    ? Component.translatable(в.ключ())
                    : Component.literal(в.текст()));
                тусклые.add(в.тусклый());
            }
            кэшСтроки = List.copyOf(строки);
            кэшТусклые = List.copyOf(тусклые);
        }
        return кэшСтроки;
    }

    /** Тускло ли печатается строка с этим номером. Идёт парой к {@link #строкиМеста}. */
    protected boolean тусклая(int номер) {
        return номер < кэшТусклые.size() && кэшТусклые.get(номер);
    }
```

Импорты: `dev.denthe.plaguecore.core.Marks`, `java.util.ArrayList`.

- [ ] **Шаг 2: печатать эти строки на вкладках STATE и BODY**

Заменить тело `case STATE ->` на:

```java
            case STATE -> {
                String автоКлюч = чужой ? Wellbeing.чужоеОбщее(чужаяСтупень)
                                        : Wellbeing.общее(HealthSense.ступень());
                List<Component> строки = строкиМеста(Marks.Место.OVERALL, автоКлюч);
                for (int i = 0; i < строки.size(); i++) {
                    y = протянуть(графика, строки.get(i), y, тусклая(i) ? ТУСКЛЫЙ : ТЕКСТ) + 3;
                }
                if (чужой && HealthSense.клирик()) {
                    протянуть(графика, клирикОбщее(чужаяСтупень), y, ТУСКЛЫЙ);
                }
                подвал(графика);
            }
```

и тело `case BODY ->` на:

```java
            case BODY -> {
                if (выбрана == null) {
                    протянуть(графика, ПОДСКАЗКА_ВЫБОР, y, ТУСКЛЫЙ);
                } else {
                    String автоКлюч = чужой
                        ? Wellbeing.чужаяЧасть(выбрана, чужаяСтупень)
                        : Wellbeing.часть(выбрана, HealthSense.ступень());
                    List<Component> строки = строкиМеста(место(выбрана), автоКлюч);
                    for (int i = 0; i < строки.size(); i++) {
                        y = протянуть(графика, строки.get(i), y, тусклая(i) ? ТУСКЛЫЙ : ТЕКСТ) + 3;
                    }
                    if (HealthSense.клирик()) {
                        протянуть(графика, клирикЧасть(выбрана), y + 1, ТУСКЛЫЙ);
                    }
                }
            }
```

И рядом — перевод части тела в место пометки:

```java
    /** Часть тела как место пометки: перечисления разные, смысл один. */
    protected static Marks.Место место(Wellbeing.Часть часть) {
        return switch (часть) {
            case HEAD -> Marks.Место.HEAD;
            case TORSO -> Marks.Место.TORSO;
            case ARMS -> Marks.Место.ARMS;
            case LEGS -> Marks.Место.LEGS;
        };
    }
```

- [ ] **Шаг 3: дописать пометки к «Ощущениям» и «Журналу»**

В `case FEEL ->` перед выводом автосписка:

```java
            case FEEL -> {
                boolean прячем = Marks.заменяет(Marks.Место.FEEL, пометки());
                List<Component> метки = строкиМеста(Marks.Место.FEEL, null);
                List<Component> что = прячем ? List.of() : HealthSense.ощущения();
                if (что.isEmpty() && метки.isEmpty()) {
                    протянуть(графика, ОЩУЩЕНИЙ_НЕТ, y, ТУСКЛЫЙ);
                } else {
                    for (Component строка : что) y = протянуть(графика, строка, y, ТЕКСТ) + 2;
                    for (Component строка : метки) y = протянуть(графика, строка, y, ТЕКСТ) + 2;
                }
            }
```

В `case MEMORY ->` — после журнала, тем же способом: сначала записи
журнала (как сейчас), затем строки `Marks.Место.MEMORY`. Замена
(`Marks.заменяет`) скрывает автоматический журнал целиком.

- [ ] **Шаг 4: проверить в дев-клиенте**

```
cd plaguecore && ./gradlew runClient -PquickPlay="New World"
```

В игре: `/plague health add @s arms append fracture` — по клавише `Y`,
вкладка «Тело», рука: под автотекстом появилась строка про перелом.
Затем `/plague health clear @s` — строка исчезла. (Команды появятся
в задаче 6; если она ещё не сделана, проверку переносим туда.)

- [ ] **Шаг 5: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthScreen.java
git commit -m "Экран здоровья печатает пометки Мастера рядом с автотекстом"
```

---

## Задача 6: команды `/plague health`

**Файлы:**
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/HealthMarksCommands.java`
- Изменить: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java`

**Интерфейсы:**
- Потребляет: `PlayerHealthMarks.*`, `PlagueNetwork.отправитьПометки`, `Marks.*`.
- Отдаёт: поддерево `/plague health` и метод
  `HealthMarksCommands.разослать(ServerPlayer чьи, ServerPlayer ГМ)`.

- [ ] **Шаг 1: написать `HealthMarksCommands.java`**

```java
package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.denthe.plaguecore.core.Marks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Подкоманды `/plague health` — правка пометок Мастера игры.
 * Спек «Пометки Мастера игры», раздел 9.
 *
 * Правки идут командами, а не своим пакетом «клиент → сервер»:
 * право проверяет уже само дерево /plague (оператор, уровень 2),
 * и второй путь пришлось бы защищать отдельно. Экран редактора
 * просто печатает эти же команды.
 */
public final class HealthMarksCommands {
    private HealthMarksCommands() {}

    public static void подключить(LiteralArgumentBuilder<CommandSourceStack> корень) {
        корень.then(Commands.literal("health")
            .then(Commands.literal("edit")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::редактор)))
            .then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("place", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .then(Commands.argument("preset", StringArgumentType.word())
                                .executes(c -> добавить(c, true)))))))
            .then(Commands.literal("text")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("place", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .then(Commands.argument("line", StringArgumentType.greedyString())
                                .executes(c -> добавить(c, false)))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(HealthMarksCommands::удалить))))
            .then(Commands.literal("clear")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::очистить)))
            .then(Commands.literal("list")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(HealthMarksCommands::список))));
    }

    private static int редактор(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        if (!(c.getSource().getEntity() instanceof ServerPlayer гм)) {
            c.getSource().sendFailure(Component.literal("Только от лица игрока"));
            return 0;
        }
        PlagueNetwork.отправитьПометки(гм, цель, (byte) 1);
        return 1;
    }

    private static int добавить(CommandContext<CommandSourceStack> c, boolean заготовкой)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");

        Marks.Место место = Marks.место(StringArgumentType.getString(c, "place"));
        if (место == null) {
            c.getSource().sendFailure(Component.literal("Нет такого места. Есть: "
                + перечислить(Marks.Место.values())));
            return 0;
        }

        String режим = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
        if (!режим.equals("replace") && !режим.equals("append")) {
            c.getSource().sendFailure(Component.literal("Режим — replace или append"));
            return 0;
        }

        String заготовка = "", текст = "";
        if (заготовкой) {
            String имя = StringArgumentType.getString(c, "preset");
            Marks.Заготовка з = Marks.заготовка(имя);
            if (з == null) {
                c.getSource().sendFailure(Component.literal("Нет такой заготовки. Есть: "
                    + перечислить(Marks.Заготовка.values())));
                return 0;
            }
            заготовка = з.идентификатор();
        } else {
            текст = StringArgumentType.getString(c, "line");
        }

        int id = PlayerHealthMarks.добавить(цель, место, режим.equals("replace"), заготовка, текст);
        if (id < 0) {
            c.getSource().sendFailure(Component.literal(
                "Не вышло: пусто или уже " + Marks.ПРЕДЕЛ + " пометок"));
            return 0;
        }

        разослать(цель, c.getSource());
        int готово = id;
        c.getSource().sendSuccess(() -> Component.literal(
            "Пометка " + готово + " поставлена: " + цель.getGameProfile().getName()), false);
        return 1;
    }

    private static int удалить(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        int id = IntegerArgumentType.getInteger(c, "id");
        if (!PlayerHealthMarks.удалить(цель, id)) {
            c.getSource().sendFailure(Component.literal("Нет пометки " + id));
            return 0;
        }
        разослать(цель, c.getSource());
        c.getSource().sendSuccess(() -> Component.literal("Пометка " + id + " снята"), false);
        return 1;
    }

    private static int очистить(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        PlayerHealthMarks.очистить(цель);
        разослать(цель, c.getSource());
        c.getSource().sendSuccess(() -> Component.literal(
            "Всё снято, экран снова считается по показателям"), false);
        return 1;
    }

    private static int список(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer цель = EntityArgument.getPlayer(c, "target");
        var пометки = PlayerHealthMarks.список(цель);
        if (пометки.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Пометок нет"), false);
            return 1;
        }
        for (Marks.Пометка п : пометки) {
            String строка = п.id() + ": " + п.место().name().toLowerCase(Locale.ROOT)
                + (п.заменяет() ? " [замена] " : " [добавка] ")
                + (п.своя() ? "«" + п.текст() + "»" : п.заготовка());
            c.getSource().sendSuccess(() -> Component.literal(строка), false);
        }
        return 1;
    }

    /**
     * Разослать новый список: игроку — чтобы экран обновился сразу,
     * ГМ — чтобы обновился его редактор, если он открыт.
     */
    public static void разослать(ServerPlayer цель, CommandSourceStack источник) {
        PlagueNetwork.отправитьПометки(цель, цель, (byte) 0);
        if (источник.getEntity() instanceof ServerPlayer гм && гм != цель) {
            PlagueNetwork.отправитьПометки(гм, цель, (byte) 1);
        }
    }

    private static String перечислить(Enum<?>[] значения) {
        StringBuilder сб = new StringBuilder();
        for (Enum<?> з : значения) {
            if (сб.length() > 0) сб.append(", ");
            сб.append(з.name().toLowerCase(Locale.ROOT));
        }
        return сб.toString();
    }
}
```

- [ ] **Шаг 2: подключить поддерево**

В `PlagueCommands.зарегистрировать(...)`, рядом с остальными `корень.then(...)`:

```java
        HealthMarksCommands.подключить(корень);
```

- [ ] **Шаг 3: собрать и проверить живьём**

```
cd plaguecore && ./gradlew runClient -PquickPlay="New World"
```

В игре по очереди:

```
/plague health add @s arms append fracture
/plague health text @s overall append Рука в лубке, работать не может.
/plague health list @s
/plague health remove @s 1
/plague health clear @s
```

Ожидаем: экран по `Y` показывает пометки и снова чистеет после `clear`;
`add` с чужим словом вместо места отвечает списком допустимых.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/mc/HealthMarksCommands.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java
git commit -m "Команды /plague health: добавить, своя строка, снять, вернуть как было"
```

---

## Задача 7: экран редактора

**Файлы:**
- Заменить целиком: `plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthEditScreen.java` (заглушка из задачи 4)
- Создать: `plaguecore/src/main/java/dev/denthe/plaguecore/client/MarkPickerScreen.java`

**Интерфейсы:**
- Потребляет: `HealthScreen` (защищённые `левый`, `верхний`, `вкладка`,
  `выбрана`, `правыйX()`, `праваяШирина()`, `протянуть(...)`, `строкиМеста(...)`,
  `место(Часть)`), `HealthMarksClient`, `Marks.*`.
- Отдаёт: `HealthEditScreen.принять(PlagueNetwork.MarkList)` — её зовёт
  `HealthMarksClient` из задачи 4.

- [ ] **Шаг 1: написать `HealthEditScreen`**

Ключевые решения, которые нельзя потерять:

```java
package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.core.Marks;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Экран правки состояния: тот же планшет, что видит игрок, но с
 * крестиками, карандашами и кнопкой «Вернуть как было».
 * Спек «Пометки Мастера игры», раздел 10.
 *
 * Наследуется от {@link HealthScreen} не ради экономии, а чтобы ГМ
 * видел ровно то же, что игрок: геометрия планшета, фигура, части
 * тела и вкладки уже выверены там, и второй их экземпляр разъедется
 * с оригиналом на первой же правке.
 *
 * Открывается только у ГМ и только по его команде. Игрок не получает
 * ни звука, ни сообщения: он узнает о пометке, открыв свой экран.
 */
public class HealthEditScreen extends HealthScreen {

    private final int сущность;
    private final Component имяЦели;
    private List<Marks.Пометка> список;

    /** Крестик и карандаш у строки, и куда по ним попадать мышью. */
    private static final int ЗНАЧОК = 8, ОТСТУП = 2;

    /** Кнопка «Вернуть как было» просит второй клик, как в панели ГМ. */
    private long взведено;

    private HealthEditScreen(Player цель, int ступень, List<Marks.Пометка> список) {
        super(цель, ступень);
        this.сущность = цель.getId();
        this.имяЦели = цель.getName();
        this.список = список;
    }
    ...
}
```

Дальше в классе:

- `public static void принять(PlagueNetwork.MarkList пакет)` — если открыт
  `HealthEditScreen` с тем же `сущность`, обновить поле `список` (и ступень);
  иначе найти сущность в мире (`mc.level.getEntity`), проверить, что это
  `Player`, и открыть новый экран со ступенью из пакета. Ступень нужна,
  чтобы редактор печатал автотекст болезни — то есть ровно то, что видит
  игрок; в пакете она заполнена только для режима 1 (задача 4).
- `вкладки()` — все четыре: ГМ ходит по всем разделам. На «Ощущениях»
  и «Журнале» автосодержимое серверу неизвестно, поэтому там видны только
  пометки, а под списком идёт подпись `plaguecore.health.edit.hidden`.
- `правая(GuiGraphics)` — переопределение: заголовок раздела, затем строки
  пометок этого места; у каждой справа `×` и карандаш; под списком
  «+ добавить»; если пометок нет — `plaguecore.health.edit.empty`.
- `mouseClicked` — попадание по `×` шлёт
  `plague health remove <ник> <id>`; по карандашу — открывает
  `MarkPickerScreen` в режиме правки (она пошлёт `remove`, затем `add`
  или `text`); по «+ добавить» — `MarkPickerScreen` в режиме добавления;
  по «Вернуть как было» — первый клик взводит, второй в течение трёх
  секунд шлёт `plague health clear <ник>`.
- Отправка команды — тем же способом, что в панели ГМ:

```java
    private void команда(String строка) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(строка);
    }
```

- [ ] **Шаг 2: написать `MarkPickerScreen`**

Отдельный экран поверх редактора:

- список заготовок: `Marks.Заготовка.values()`, подпись — `Marks.имя(з)`;
- последним пунктом «Своя строка» (`plaguecore.health.edit.own`) с `EditBox`
  на `Marks.ДЛИНА_СТРОКИ` знаков;
- переключатель «Заменить автотекст» / «Дописать ниже»
  (`plaguecore.health.edit.replace` / `.append`);
- выбор места (`plaguecore.health.edit.place`), если экран открыт не
  с выбранной частью тела;
- по подтверждению шлёт
  `plague health add <ник> <место> <режим> <заготовка>` либо
  `plague health text <ник> <место> <режим> <строка>` и возвращает
  `HealthEditScreen` через `minecraft.setScreen(родитель)`.

Оформление — тёмная панель, как у `MarkerDialogScreen` в `lmpc_gmtools`:
это служебное окно ГМ, а не часть кожаного планшета.

- [ ] **Шаг 3: собрать и проверить живьём**

```
cd plaguecore && ./gradlew runClient -PquickPlay="New World"
```

В игре: `/plague health edit <свой ник>` — открылся редактор. Добавить
заготовку на руки, увидеть строку, удалить крестиком, вернуть кнопкой.

- [ ] **Шаг 4: коммит**

```bash
git add plaguecore/src/main/java/dev/denthe/plaguecore/client/HealthEditScreen.java \
        plaguecore/src/main/java/dev/denthe/plaguecore/client/MarkPickerScreen.java
git commit -m "Экран правки состояния для Мастера игры"
```

---

## Задача 8: кнопка в панели Мастера игры

**Файлы:**
- Изменить: `lmpc_gmtools/src/main/java/dev/denthe/gmtools/client/GmPanelScreen.java` (метод `initPlayers`, ряд кнопок состояния рядом с «Лечить»)

**Интерфейсы:**
- Потребляет: команду `/plague health edit <ник>` из задачи 6.
- Отдаёт: ничего.

- [ ] **Шаг 1: добавить кнопку**

В `initPlayers()`, сразу после ряда «Лечить / Кормить / Оба / Снять эфф.»:

```java
        y += BTN_H + 3;
        addRenderableWidget(Button.builder(Component.literal("Состояние здоровья…"),
            b -> { run("plague health edit " + n); onClose(); })
            .bounds(x, y, w, BTN_H).build());
```

Панель закрывается: редактор — полноэкранный планшет, держать под ним
панель незачем.

- [ ] **Шаг 2: собрать**

```
cd lmpc_gmtools && ./gradlew build
```

Ожидаем: BUILD SUCCESSFUL.

- [ ] **Шаг 3: коммит**

```bash
git add lmpc_gmtools/src/main/java/dev/denthe/gmtools/client/GmPanelScreen.java
git commit -m "Панель ГМ: кнопка «Состояние здоровья» в карточке игрока"
```

---

## Задача 9: сборка, раскладка и запись итогов

**Файлы:**
- Изменить: `plaguecore/gradle.properties` (`mod_version=0.4.2` → `0.5.0`)
- Изменить: `lmpc_gmtools/gradle.properties` (версия → `0.20.0`)
- Изменить: `CLAUDE.md` (подсистема «Игрок» — строка про правку ГМ)
- Изменить: `docs/superpowers/specs/2026-09-16-pometki-gm-zdorovye-design.md` (раздел «Итог»)
- Джарники: `mods/` репозитория, профиль Modrinth `LMPCCHUMA`, `server/mods`

- [ ] **Шаг 1: поднять версии обоих модов**

- [ ] **Шаг 2: полная сборка и тесты**

```
cd plaguecore && ./gradlew test build
cd ../lmpc_gmtools && ./gradlew build
```

Ожидаем: BUILD SUCCESSFUL в обоих, все тесты зелёные.

- [ ] **Шаг 3: живая проверка на копии сервера**

Пройти все восемь пунктов раздела 12 спека, включая смерть
(пометки исчезают) и перезапуск сервера (пометки на месте).
Рискованное — только на копии, не на боевой папке.

- [ ] **Шаг 4: разложить джарники**

Новые `plaguecore-0.5.0.jar` и `lmpc_gmtools-0.20.0.jar` — в три места:
`mods/` репозитория, профиль Modrinth `LMPCCHUMA`, `server/mods`.
Прошлые версии наших же модов при обновлении убираются, чужие моды
в профиле не трогаются.

- [ ] **Шаг 5: записать итог в спек**

В конец спека — раздел «Итог реализации»: что сделано, где лежит,
что проверено живьём, что осталось. Это читает вторая сессия.

- [ ] **Шаг 6: коммит**

```bash
git add -A
git commit -m "Пометки ГМ: сборка 0.5.0 / 0.20.0, итог в спеке"
```

---

## Самопроверка плана

**Покрытие спека.** Раздел 6 (данные) → задачи 1 и 3; раздел 7 (каталог
и слияние) → задачи 1 и 2; раздел 8 (сеть) → задача 4; раздел 9
(команды) → задача 6; раздел 10 (экран редактора) → задача 7;
раздел 11 (кнопка панели) → задача 8; раздел 12 (проверка) → тесты
в задачах 1–2 и живой прогон в задаче 9.

**Незакрытое, о чём должен знать исполнитель.** Действующие эффекты
и журнал живут на клиенте игрока, серверу они неизвестны, поэтому
в редакторе ГМ на вкладках «Ощущения» и «Журнал» видны только пометки,
а не то, что там у игрока на самом деле. Подделывать этот показ было бы
враньём, и мы этого не делаем — вместо этого под списком стоит подпись
`plaguecore.health.edit.hidden`. Ступень игрока ГМ получает в пакете
(режим 1) и автотекст болезни печатает как есть: ГМ — оператор, запрет
спека на раздачу стадии касается игроков.
