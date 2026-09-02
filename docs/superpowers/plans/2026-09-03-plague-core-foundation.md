# Ядро чумы: фундамент — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать работающий скелет ядра чумы: мод запускается, хранит карту заражения на 3 906 чанков, каждую ночь считает распространение по спеку, умеет сгенерировать стартовое состояние и показать статистику командой `/plague info`.

**Architecture:** Алгоритм распространения живёт в пакете `core`, куда **запрещено импортировать классы Minecraft**. Это плоские массивы байт и чистые функции над ними — они тестируются обычным JUnit без запуска игры. Пакет `mc` — тонкая обвязка: `SavedData`, хук наступления ночи, команды. Такое разделение — единственный способ отлаживать клеточный автомат за секунды, а не за минуты перезапуска сервера.

**Tech Stack:** Java 21, NeoForge 21.1.x (MC 1.21.1), ModDevGradle 2.x, JUnit 5.

## Global Constraints

- Minecraft **1.21.1**, загрузчик **NeoForge** (не Forge, не Fabric, не гибриды)
- JDK **21** (в системе стоит 21.0.9)
- `modId` = **`plaguecore`**, корневой пакет **`dev.denthe.plaguecore`**
- Проект мода лежит в подпапке **`plaguecore/`** репозитория, отдельно от `mods/` (клиентский пак) и `docs/`
- Пакет `dev.denthe.plaguecore.core` **не содержит ни одного импорта `net.minecraft.*` или `net.neoforged.*`**. Это проверяется тестом.
- Мир: **1000 × 1000 блоков**, то есть сетка **63 × 63 чанка** (3 969 ячеек, покрывает границу с запасом)
- Уровни заражения: **0–5**, где 5 выставляет только подсистема логова
- Значения фаз копируются из спека дословно (раздел 6.2):
  `base` = 0.04 / 0.07 / 0.11 / 0.16 / 0.24;
  бюджет = 25 / 50 / 95 / 150 / 240;
  границы фаз по ночам = 1–5 / 6–12 / 13–20 / 21–30 / 31+
- Множитель сна `SLEEP_BUDGET_MULTIPLIER` = **2.0**, дополнительный рост при сне = **+1**
- Длительность шрама `SCAR_NIGHTS` = **5**
- Множители местности: трава/лес 1.4, земля/песок 1.0, камень 0.6, вода 0.4, лава 0.1
- Все игровые числа выносятся в константы одного класса `PlagueConstants`, чтобы позже переехать в конфиг одним движением
- Коммиты — на русском, в конце каждого:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

**Спек:** `docs/superpowers/specs/2026-09-03-plague-core-design.md`

**Что этот план НЕ делает** (следующие планы): материализация блоков, подземелье, стадии игрока, штраф за смерть, очистители, курильница, фазовые события, победа и поражение.

---

## Структура файлов

```
plaguecore/
  settings.gradle
  build.gradle
  gradle.properties
  src/main/java/dev/denthe/plaguecore/
      PlagueCore.java                  точка входа мода, регистрация событий
      PlagueConstants.java             все игровые числа в одном месте
      core/
          PlagueGrid.java              4 сетки байт + доступ по чанковым координатам
          PhaseParams.java             record: параметры одной фазы
          PhaseTable.java              таблица фаз из спека 6.2
          SpreadEngine.java            ночной тик: рост, экспансия, шрамы
          StartGenerator.java          стартовое заражение до заданного процента
          PlagueGridCodec.java         сериализация сеток в byte[]
      mc/
          PlagueState.java             SavedData: сетка + ночь + фаза
          TerrainInitializer.java      заполнение сетки местности из biome source
          NightHook.java               определение наступления ночи и сна
          PlagueCommands.java          /plague ...
  src/main/resources/
      META-INF/neoforge.mods.toml
      pack.mcmeta
  src/test/java/dev/denthe/plaguecore/core/
      PlagueGridTest.java
      PhaseTableTest.java
      SpreadEngineTest.java
      StartGeneratorTest.java
      PlagueGridCodecTest.java
      CorePurityTest.java              запрещает импорты Minecraft в core
```

Разделение по ответственности, а не по слоям: `core` — математика, `mc` — интеграция. Файлы небольшие и держатся в голове целиком.

---

### Task 1: Каркас проекта и сборка

**Files:**
- Create: `plaguecore/settings.gradle`
- Create: `plaguecore/build.gradle`
- Create: `plaguecore/gradle.properties`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java`
- Create: `plaguecore/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `plaguecore/src/main/resources/pack.mcmeta`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/CorePurityTest.java`

**Interfaces:**
- Consumes: ничего
- Produces: `PlagueCore.MODID` (строковая константа `"plaguecore"`), рабочая команда `gradlew test`

- [ ] **Step 1: Узнать актуальную версию NeoForge для 1.21.1 и зафиксировать её**

Список версий лежит в maven-метаданных. Берём последнюю строку `21.1.*`:

```bash
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml \
  | grep -oE '21\.1\.[0-9]+' | sort -t. -k3 -n | tail -1
```

Ожидание: строка вида `21.1.2xx`. Модпак требует минимум `21.1.228` (это объявляет `create-aeronautics`, который мы убрали, но `mapwright` требует `21.1.219`) — если полученное число меньше 228, значит запрос ушёл не туда, повторить.

Записать результат в `gradle.properties` следующим шагом.

- [ ] **Step 2: Создать `plaguecore/gradle.properties`**

Подставить номер из шага 1 вместо `21.1.228`.

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=true
org.gradle.parallel=true

neoforge_version=21.1.228
minecraft_version=1.21.1
mod_id=plaguecore
mod_version=0.1.0
mod_group=dev.denthe.plaguecore
```

- [ ] **Step 3: Создать `plaguecore/settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'plaguecore'
```

- [ ] **Step 4: Создать `plaguecore/build.gradle`**

```groovy
plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.78'
}

version = project.mod_version
group = project.mod_group
base { archivesName = project.mod_id }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

neoForge {
    version = project.neoforge_version

    runs {
        client {
            client()
            gameDirectory = file('run/client')
        }
        server {
            server()
            gameDirectory = file('run/server')
            programArgument '--nogui'
        }
    }

    mods {
        "${project.mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging { events 'passed', 'skipped', 'failed' }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

processResources {
    def props = [
        mod_id           : project.mod_id,
        mod_version      : project.mod_version,
        neoforge_version : project.neoforge_version,
        minecraft_version: project.minecraft_version
    ]
    inputs.properties props
    filesMatching('META-INF/neoforge.mods.toml') {
        expand props
    }
}
```

- [ ] **Step 5: Создать `plaguecore/src/main/resources/META-INF/neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "[4,)"
license = "All Rights Reserved"

[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "Plague Core"
description = '''Ядро чумы: прогрессирующее заражение мира.'''

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "[${neoforge_version},)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "[${minecraft_version}]"
ordering = "NONE"
side = "BOTH"
```

- [ ] **Step 6: Создать `plaguecore/src/main/resources/pack.mcmeta`**

```json
{
  "pack": {
    "description": "Plague Core resources",
    "pack_format": 34
  }
}
```

- [ ] **Step 7: Создать точку входа `PlagueCore.java`**

```java
package dev.denthe.plaguecore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(PlagueCore.MODID)
public class PlagueCore {
    public static final String MODID = "plaguecore";
    public static final Logger LOG = LogUtils.getLogger();

    public PlagueCore(IEventBus modEventBus, ModContainer container) {
        LOG.info("Plague Core загружается");
    }
}
```

- [ ] **Step 8: Написать падающий тест чистоты ядра**

Этот тест — страж главного архитектурного решения. Он проверяет, что в
`core` не просочился Minecraft. Сейчас пакета `core` ещё нет, поэтому
тест упадёт — это правильно.

`plaguecore/src/test/java/dev/denthe/plaguecore/core/CorePurityTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CorePurityTest {

    @Test
    void coreНеЗависитОтMinecraft() throws IOException {
        Path coreDir = Paths.get("src/main/java/dev/denthe/plaguecore/core");
        assertTrue(Files.isDirectory(coreDir), "пакет core должен существовать: " + coreDir.toAbsolutePath());

        List<String> нарушения = new ArrayList<>();
        try (Stream<Path> files = Files.walk(coreDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        String t = line.trim();
                        if (t.startsWith("import net.minecraft") || t.startsWith("import net.neoforged")) {
                            нарушения.add(p.getFileName() + ": " + t);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(нарушения.isEmpty(),
            "в пакете core запрещены импорты Minecraft/NeoForge:\n" + String.join("\n", нарушения));
    }
}
```

- [ ] **Step 9: Запустить тест и убедиться, что он падает**

```bash
cd plaguecore && ./gradlew test --tests '*CorePurityTest*'
```

Ожидание: FAIL с сообщением «пакет core должен существовать».

- [ ] **Step 10: Создать пакет core с заглушкой, чтобы тест прошёл**

`plaguecore/src/main/java/dev/denthe/plaguecore/core/package-info.java`:

```java
/**
 * Чистая математика заражения. Ни одного импорта Minecraft — см. CorePurityTest.
 */
package dev.denthe.plaguecore.core;
```

- [ ] **Step 11: Запустить тест и убедиться, что он проходит**

```bash
cd plaguecore && ./gradlew test --tests '*CorePurityTest*'
```

Ожидание: PASS.

- [ ] **Step 12: Проверить, что мод собирается**

```bash
cd plaguecore && ./gradlew build
```

Ожидание: BUILD SUCCESSFUL, в `plaguecore/build/libs/` появился `plaguecore-0.1.0.jar`.

Первая сборка тянет NeoForge и Minecraft — несколько гигабайт и до 10 минут. Это нормально.

- [ ] **Step 13: Добавить в `.gitignore` артефакты сборки и закоммитить**

Дописать в корневой `.gitignore`:

```
plaguecore/build/
plaguecore/run/
plaguecore/.gradle/
```

```bash
git add .gitignore plaguecore/
git commit -m "Каркас мода plaguecore на NeoForge 1.21.1

Gradle-проект на ModDevGradle, точка входа, JUnit 5.
CorePurityTest запрещает импорты Minecraft в пакете core —
это страж главного архитектурного решения спека.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: PlagueGrid — четыре сетки заражения

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueConstants.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/PlagueGrid.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/PlagueGridTest.java`

**Interfaces:**
- Consumes: ничего
- Produces:
  - `PlagueConstants.MAX_LEVEL = 5`, `SCAR_NIGHTS = 5`, `SLEEP_BUDGET_MULTIPLIER = 2.0f`, `SLEEP_EXTRA_GROWTH = 1`
  - `new PlagueGrid(int size, int originChunkX, int originChunkZ)`
  - `int size()`, `int originX()`, `int originZ()`, `int cellCount()`
  - `boolean contains(int cx, int cz)`
  - `int getLevel(int cx, int cz)` / `void setLevel(int cx, int cz, int v)`
  - `float getResistance(int cx, int cz)` / `void setResistance(int cx, int cz, float v)`
  - `int getScar(int cx, int cz)` / `void setScar(int cx, int cz, int v)`
  - `float getTerrain(int cx, int cz)` / `void setTerrain(int cx, int cz, float v)`
  - `int countInfected()`, `float infectedFraction()`
  - `byte[] levelsCopy()`
  - Доступ по плоскому индексу — нужен движку распространения, чтобы не пересчитывать координаты в горячем цикле:
    `int index(int cx, int cz)` (−1 вне сетки), `int chunkXOf(int index)`, `int chunkZOf(int index)`,
    `int getLevelAt(int i)`, `void setLevelAt(int i, int v)`, `float getResistanceAt(int i)`,
    `int getScarAt(int i)`, `void setScarAt(int i, int v)`, `float getTerrainAt(int i)`
  - Пакетно-приватные для кодека: `byte[] rawLevels()`, `rawResistance()`, `rawScar()`, `rawTerrain()`,
    и конструктор `PlagueGrid(int size, int ox, int oz, byte[] level, byte[] resistance, byte[] scar, byte[] terrain)`

- [ ] **Step 1: Написать падающие тесты**

`PlagueGridTest.java`:

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlagueGridTest {

    private PlagueGrid сетка() {
        return new PlagueGrid(63, -31, -31);
    }

    @Test
    void размерыИКоличествоЯчеек() {
        PlagueGrid g = сетка();
        assertEquals(63, g.size());
        assertEquals(-31, g.originX());
        assertEquals(-31, g.originZ());
        assertEquals(63 * 63, g.cellCount());
    }

    @Test
    void границыСеткиОпределяютсяВерно() {
        PlagueGrid g = сетка();
        assertTrue(g.contains(-31, -31));
        assertTrue(g.contains(31, 31));
        assertTrue(g.contains(0, 0));
        assertFalse(g.contains(-32, 0));
        assertFalse(g.contains(32, 0));
        assertFalse(g.contains(0, 32));
    }

    @Test
    void уровеньСохраняетсяИЧитается() {
        PlagueGrid g = сетка();
        g.setLevel(5, -7, 3);
        assertEquals(3, g.getLevel(5, -7));
        assertEquals(0, g.getLevel(6, -7));
    }

    @Test
    void уровеньОграниченДиапазоном() {
        PlagueGrid g = сетка();
        g.setLevel(0, 0, 99);
        assertEquals(PlagueConstants.MAX_LEVEL, g.getLevel(0, 0));
        g.setLevel(0, 0, -5);
        assertEquals(0, g.getLevel(0, 0));
    }

    @Test
    void чтениеЗаГраницейВозвращаетНольБезИсключения() {
        PlagueGrid g = сетка();
        assertEquals(0, g.getLevel(999, 999));
        assertEquals(0f, g.getResistance(999, 999));
        assertEquals(0, g.getScar(999, 999));
    }

    @Test
    void записьЗаГраницейИгнорируется() {
        PlagueGrid g = сетка();
        assertDoesNotThrow(() -> g.setLevel(999, 999, 4));
        assertEquals(0, g.getLevel(999, 999));
    }

    @Test
    void сопротивлениеХранитсяСТочностьюДоСотой() {
        PlagueGrid g = сетка();
        g.setResistance(2, 2, 0.5f);
        assertEquals(0.5f, g.getResistance(2, 2), 0.01f);
        g.setResistance(2, 2, 1.0f);
        assertEquals(1.0f, g.getResistance(2, 2), 0.01f);
        g.setResistance(2, 2, 5.0f);
        assertEquals(1.0f, g.getResistance(2, 2), 0.01f, "значение выше единицы обрезается");
    }

    @Test
    void местностьХранитсяСТочностьюДоДесятой() {
        PlagueGrid g = сетка();
        g.setTerrain(1, 1, 1.4f);
        assertEquals(1.4f, g.getTerrain(1, 1), 0.05f);
        g.setTerrain(1, 2, 0.1f);
        assertEquals(0.1f, g.getTerrain(1, 2), 0.05f);
    }

    @Test
    void местностьПоУмолчаниюЕдиница() {
        PlagueGrid g = сетка();
        assertEquals(1.0f, g.getTerrain(0, 0), 0.05f);
    }

    @Test
    void подсчётЗаражённыхЯчеек() {
        PlagueGrid g = сетка();
        assertEquals(0, g.countInfected());
        g.setLevel(0, 0, 1);
        g.setLevel(1, 0, 4);
        g.setLevel(2, 0, 0);
        assertEquals(2, g.countInfected());
        assertEquals(2f / (63 * 63), g.infectedFraction(), 1e-6);
    }

    @Test
    void копияУровнейНеСвязанаСОригиналом() {
        PlagueGrid g = сетка();
        g.setLevel(0, 0, 3);
        byte[] copy = g.levelsCopy();
        g.setLevel(0, 0, 1);
        int idx = (0 - g.originZ()) * g.size() + (0 - g.originX());
        assertEquals(3, copy[idx]);
        assertEquals(1, g.getLevel(0, 0));
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

```bash
cd plaguecore && ./gradlew test --tests '*PlagueGridTest*'
```

Ожидание: ошибка компиляции — `PlagueGrid` и `PlagueConstants` не найдены.

- [ ] **Step 3: Создать `PlagueConstants.java`**

```java
package dev.denthe.plaguecore;

/**
 * Все игровые числа в одном месте. Позже переедут в конфиг.
 */
public final class PlagueConstants {
    private PlagueConstants() {}

    /** Максимальный уровень заражения чанка. 5 выставляет только подсистема логова. */
    public static final int MAX_LEVEL = 5;

    /** Потолок, до которого поднимается обычное распространение. */
    public static final int MAX_NATURAL_LEVEL = 4;

    /** Сколько ночей держится шрам после полной очистки. */
    public static final int SCAR_NIGHTS = 5;

    /** Во сколько раз растёт бюджет ночи, если игроки спали. */
    public static final float SLEEP_BUDGET_MULTIPLIER = 2.0f;

    /** Дополнительный рост уровня на месте при сне. */
    public static final int SLEEP_EXTRA_GROWTH = 1;

    /** Сторона мира в блоках. */
    public static final int WORLD_SIZE_BLOCKS = 1000;

    /** Сторона сетки в чанках. 63 × 16 = 1008 — покрывает границу с запасом. */
    public static final int GRID_SIZE_CHUNKS = 63;

    /** Доля мира, заражённая на старте сессии. */
    public static final float START_INFECTION_PERCENT = 0.10f;
}
```

- [ ] **Step 4: Создать `PlagueGrid.java`**

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

import java.util.Arrays;

/**
 * Четыре плоские сетки байт, по одной ячейке на чанк.
 *
 * Хранение:
 *   level      0..5  уровень заражения
 *   resistance 0..100 → 0.0..1.0  сопротивление от очистителей
 *   scar       0..7   сколько ночей ещё держится шрам
 *   terrain    0..255 → делённое на 100  множитель местности
 *
 * Четыре массива по 3 969 байт — около 16 КБ на весь мир. Влезает в кэш
 * процессора целиком, поэтому ночной проход стоит доли миллисекунды.
 */
public final class PlagueGrid {

    private final int size;
    private final int originX;
    private final int originZ;

    private final byte[] level;
    private final byte[] resistance;
    private final byte[] scar;
    private final byte[] terrain;

    public PlagueGrid(int size, int originChunkX, int originChunkZ) {
        if (size <= 0) throw new IllegalArgumentException("size должен быть положительным");
        this.size = size;
        this.originX = originChunkX;
        this.originZ = originChunkZ;
        int n = size * size;
        this.level = new byte[n];
        this.resistance = new byte[n];
        this.scar = new byte[n];
        this.terrain = new byte[n];
        Arrays.fill(this.terrain, (byte) 100); // множитель 1.0 по умолчанию
    }

    /** Конструктор для кодека: массивы принимаются как есть, без копирования. */
    PlagueGrid(int size, int originChunkX, int originChunkZ,
               byte[] level, byte[] resistance, byte[] scar, byte[] terrain) {
        this.size = size;
        this.originX = originChunkX;
        this.originZ = originChunkZ;
        this.level = level;
        this.resistance = resistance;
        this.scar = scar;
        this.terrain = terrain;
    }

    public int size() { return size; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int cellCount() { return size * size; }

    public boolean contains(int cx, int cz) {
        int dx = cx - originX;
        int dz = cz - originZ;
        return dx >= 0 && dx < size && dz >= 0 && dz < size;
    }

    /** Индекс ячейки или -1, если координата вне сетки. */
    public int index(int cx, int cz) {
        int dx = cx - originX;
        int dz = cz - originZ;
        if (dx < 0 || dx >= size || dz < 0 || dz >= size) return -1;
        return dz * size + dx;
    }

    public int chunkXOf(int index) { return originX + (index % size); }
    public int chunkZOf(int index) { return originZ + (index / size); }

    public int getLevel(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : level[i];
    }

    public void setLevel(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        level[i] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public int getLevelAt(int index) { return level[index]; }

    public void setLevelAt(int index, int value) {
        level[index] = (byte) clamp(value, 0, PlagueConstants.MAX_LEVEL);
    }

    public float getResistance(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0f : (resistance[i] & 0xFF) / 100f;
    }

    public void setResistance(int cx, int cz, float value) {
        int i = index(cx, cz);
        if (i < 0) return;
        resistance[i] = (byte) Math.round(clampF(value, 0f, 1f) * 100f);
    }

    public float getResistanceAt(int index) { return (resistance[index] & 0xFF) / 100f; }

    public int getScar(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0 : scar[i];
    }

    public void setScar(int cx, int cz, int value) {
        int i = index(cx, cz);
        if (i < 0) return;
        scar[i] = (byte) clamp(value, 0, 7);
    }

    public int getScarAt(int index) { return scar[index]; }

    public void setScarAt(int index, int value) { scar[index] = (byte) clamp(value, 0, 7); }

    public float getTerrain(int cx, int cz) {
        int i = index(cx, cz);
        return i < 0 ? 0f : (terrain[i] & 0xFF) / 100f;
    }

    public void setTerrain(int cx, int cz, float value) {
        int i = index(cx, cz);
        if (i < 0) return;
        terrain[i] = (byte) Math.round(clampF(value, 0f, 2.55f) * 100f);
    }

    public float getTerrainAt(int index) { return (terrain[index] & 0xFF) / 100f; }

    public int countInfected() {
        int n = 0;
        for (byte b : level) if (b > 0) n++;
        return n;
    }

    public float infectedFraction() {
        return (float) countInfected() / cellCount();
    }

    public byte[] levelsCopy() { return level.clone(); }

    byte[] rawLevels() { return level; }
    byte[] rawResistance() { return resistance; }
    byte[] rawScar() { return scar; }
    byte[] rawTerrain() { return terrain; }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static float clampF(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

```bash
cd plaguecore && ./gradlew test --tests '*PlagueGridTest*' --tests '*CorePurityTest*'
```

Ожидание: PASS, 11 тестов в `PlagueGridTest`.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src
git commit -m "PlagueGrid: четыре сетки заражения на 3969 чанков

level, resistance, scar, terrain — плоские массивы байт, около 16 КБ
на весь мир. Чтение за границей возвращает ноль, запись игнорируется:
алгоритму распространения не нужно проверять края.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: PhaseTable — параметры фаз эпидемии

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/PhaseParams.java`
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/PhaseTable.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/PhaseTableTest.java`

**Interfaces:**
- Consumes: ничего
- Produces:
  - `record PhaseParams(float base, int budget, int growthEveryNights, int growthAmount)`
  - `PhaseTable.phaseForNight(int night)` → `int` 0..4
  - `PhaseTable.paramsFor(int phase)` → `PhaseParams`
  - `PhaseTable.PHASE_COUNT = 5`

- [ ] **Step 1: Написать падающие тесты**

`PhaseTableTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTableTest {

    @Test
    void границыФазСоответствуютСпеку() {
        assertEquals(0, PhaseTable.phaseForNight(1));
        assertEquals(0, PhaseTable.phaseForNight(5));
        assertEquals(1, PhaseTable.phaseForNight(6));
        assertEquals(1, PhaseTable.phaseForNight(12));
        assertEquals(2, PhaseTable.phaseForNight(13));
        assertEquals(2, PhaseTable.phaseForNight(20));
        assertEquals(3, PhaseTable.phaseForNight(21));
        assertEquals(3, PhaseTable.phaseForNight(30));
        assertEquals(4, PhaseTable.phaseForNight(31));
        assertEquals(4, PhaseTable.phaseForNight(999));
    }

    @Test
    void ночьНольИОтрицательныеСчитаютсяПервойФазой() {
        assertEquals(0, PhaseTable.phaseForNight(0));
        assertEquals(0, PhaseTable.phaseForNight(-3));
    }

    @Test
    void бюджетыСоответствуютСпеку() {
        assertEquals(25, PhaseTable.paramsFor(0).budget());
        assertEquals(50, PhaseTable.paramsFor(1).budget());
        assertEquals(95, PhaseTable.paramsFor(2).budget());
        assertEquals(150, PhaseTable.paramsFor(3).budget());
        assertEquals(240, PhaseTable.paramsFor(4).budget());
    }

    @Test
    void базоваяВероятностьСоответствуетСпеку() {
        assertEquals(0.04f, PhaseTable.paramsFor(0).base(), 1e-6);
        assertEquals(0.07f, PhaseTable.paramsFor(1).base(), 1e-6);
        assertEquals(0.11f, PhaseTable.paramsFor(2).base(), 1e-6);
        assertEquals(0.16f, PhaseTable.paramsFor(3).base(), 1e-6);
        assertEquals(0.24f, PhaseTable.paramsFor(4).base(), 1e-6);
    }

    @Test
    void ритмРостаНаМестеСоответствуетСпеку() {
        assertEquals(3, PhaseTable.paramsFor(0).growthEveryNights());
        assertEquals(2, PhaseTable.paramsFor(1).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(2).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(3).growthEveryNights());
        assertEquals(1, PhaseTable.paramsFor(4).growthEveryNights());

        assertEquals(1, PhaseTable.paramsFor(0).growthAmount());
        assertEquals(2, PhaseTable.paramsFor(4).growthAmount());
    }

    @Test
    void бюджетыРастутМонотонно() {
        for (int p = 1; p < PhaseTable.PHASE_COUNT; p++) {
            assertTrue(PhaseTable.paramsFor(p).budget() > PhaseTable.paramsFor(p - 1).budget(),
                "бюджет фазы " + p + " должен быть больше предыдущей");
            assertTrue(PhaseTable.paramsFor(p).base() > PhaseTable.paramsFor(p - 1).base(),
                "base фазы " + p + " должен быть больше предыдущей");
        }
    }

    @Test
    void несуществующаяФазаОбрезаетсяКГраницам() {
        assertEquals(PhaseTable.paramsFor(0), PhaseTable.paramsFor(-1));
        assertEquals(PhaseTable.paramsFor(4), PhaseTable.paramsFor(17));
    }

    @Test
    void кривойХватаетНаВосемьдесятПроцентовКТридцатойНочи() {
        // Проверка расчёта из спека 6.2: суммарный бюджет за 30 ночей
        int сумма = 0;
        for (int night = 1; night <= 30; night++) {
            сумма += PhaseTable.paramsFor(PhaseTable.phaseForNight(night)).budget();
        }
        assertEquals(2735, сумма, "суммарный бюджет за 30 ночей по спеку");

        int стартовые = Math.round(3969 * 0.10f);
        float доля = (стартовые + сумма) / 3969f;
        assertTrue(доля > 0.75f && доля < 0.85f,
            "к ночи 30 должно быть 75-85% мира, получилось " + доля);
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

```bash
cd plaguecore && ./gradlew test --tests '*PhaseTableTest*'
```

Ожидание: ошибка компиляции — `PhaseTable` не найден.

- [ ] **Step 3: Создать `PhaseParams.java`**

```java
package dev.denthe.plaguecore.core;

/**
 * Параметры одной фазы эпидемии.
 *
 * @param base              базовая вероятность заражения соседнего чанка
 * @param budget            потолок новых заражённых чанков за ночь
 * @param growthEveryNights раз во сколько ночей уровень растёт на месте
 * @param growthAmount      на сколько растёт уровень за раз
 */
public record PhaseParams(float base, int budget, int growthEveryNights, int growthAmount) {}
```

- [ ] **Step 4: Создать `PhaseTable.java`**

```java
package dev.denthe.plaguecore.core;

/**
 * Таблица фаз из спека, раздел 6.2. Числа рассчитаны под мир 1000×1000.
 *
 * Кривая: старт 10% (≈397 чанков), к ночи 30 суммарный бюджет 2735,
 * итого около 80% мира.
 */
public final class PhaseTable {
    private PhaseTable() {}

    public static final int PHASE_COUNT = 5;

    /** Последняя ночь каждой фазы. Фаза 4 бессрочная. */
    private static final int[] PHASE_END_NIGHT = { 5, 12, 20, 30, Integer.MAX_VALUE };

    private static final PhaseParams[] PARAMS = {
        //               base   budget  каждые N ночей  на сколько
        new PhaseParams(0.04f,   25,      3,             1),
        new PhaseParams(0.07f,   50,      2,             1),
        new PhaseParams(0.11f,   95,      1,             1),
        new PhaseParams(0.16f,  150,      1,             1),
        new PhaseParams(0.24f,  240,      1,             2)
    };

    public static int phaseForNight(int night) {
        if (night <= 0) return 0;
        for (int p = 0; p < PHASE_COUNT; p++) {
            if (night <= PHASE_END_NIGHT[p]) return p;
        }
        return PHASE_COUNT - 1;
    }

    public static PhaseParams paramsFor(int phase) {
        int p = phase < 0 ? 0 : Math.min(phase, PHASE_COUNT - 1);
        return PARAMS[p];
    }
}
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

```bash
cd plaguecore && ./gradlew test --tests '*PhaseTableTest*'
```

Ожидание: PASS, 8 тестов.

Последний тест — прямая проверка арифметики из спека. Если кто-то
поменяет бюджеты, не подумав о кривой, тест это поймает.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src
git commit -m "PhaseTable: параметры пяти фаз эпидемии

Числа из спека 6.2 под мир 1000x1000. Тест проверяет не только
отдельные значения, но и итоговую кривую: 75-85% мира к ночи 30.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: SpreadEngine — ночной тик

Сердце подсистемы. Чистая функция над сеткой.

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/SpreadEngine.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/SpreadEngineTest.java`

**Interfaces:**
- Consumes: `PlagueGrid`, `PhaseTable`, `PhaseParams`, `PlagueConstants`
- Produces:
  - `record NightResult(int newlyInfected, int grown, int scarsHealed, int phase)`
  - `SpreadEngine.runNight(PlagueGrid grid, int night, boolean slept, RandomGenerator rng)` → `NightResult`
  - `SpreadEngine.runNightWith(PlagueGrid grid, int night, PhaseParams params, float budgetMultiplier, int extraGrowth, RandomGenerator rng)` → `NightResult` (используется генератором старта)

- [ ] **Step 1: Написать падающие тесты**

`SpreadEngineTest.java`:

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class SpreadEngineTest {

    private static RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }

    private static PlagueGrid пустая() {
        return new PlagueGrid(63, -31, -31);
    }

    private static PlagueGrid сОчагомВЦентре(int level) {
        PlagueGrid g = пустая();
        g.setLevel(0, 0, level);
        return g;
    }

    @Test
    void бюджетНочиНеПревышается() {
        PlagueGrid g = пустая();
        // забиваем половину сетки, чтобы источников было заведомо больше бюджета
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 0; cz++) g.setLevel(cx, cz, 4);
        }
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(42));
        assertTrue(r.newlyInfected() <= 25,
            "фаза 0 разрешает 25 новых чанков, получено " + r.newlyInfected());
    }

    @Test
    void сонУдваиваетБюджет() {
        PlagueGrid обычная = пустая();
        PlagueGrid сонная = пустая();
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 0; cz++) {
                обычная.setLevel(cx, cz, 4);
                сонная.setLevel(cx, cz, 4);
            }
        }
        SpreadEngine.NightResult без = SpreadEngine.runNight(обычная, 1, false, rng(7));
        SpreadEngine.NightResult со = SpreadEngine.runNight(сонная, 1, true, rng(7));

        assertTrue(со.newlyInfected() <= 50, "потолок при сне — 50");
        assertTrue(со.newlyInfected() > без.newlyInfected(),
            "сон должен ускорять: без сна " + без.newlyInfected() + ", со сном " + со.newlyInfected());
    }

    /**
     * Параметры «гарантированного» заражения: base 1.0 при источнике
     * уровня 4 даёт вероятность ровно 1.0, то есть результат детерминирован
     * и тест не может моргать.
     */
    private static PhaseParams гарантированные() {
        return new PhaseParams(1.0f, 1000, 99, 1);
    }

    /** Быстрые параметры для тестов, где важна форма распространения, а не темп. */
    private static PhaseParams быстрые() {
        return new PhaseParams(0.20f, 500, 1, 1);
    }

    @Test
    void заражениеРаспространяетсяИзОчага() {
        PlagueGrid g = сОчагомВЦентре(4);
        SpreadEngine.runNightWith(g, 1, гарантированные(), 1f, 0, rng(1));
        assertEquals(9, g.countInfected(),
            "при вероятности 1.0 должны заразиться очаг и все 8 соседей");
    }

    @Test
    void заражениеСимметричноПоЧетырёмСторонам() {
        PlagueGrid g = сОчагомВЦентре(4);
        for (int night = 1; night <= 20; night++) {
            SpreadEngine.runNightWith(g, night, быстрые(), 1f, 0, rng(1000 + night));
        }
        int север = 0, юг = 0, запад = 0, восток = 0;
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                if (g.getLevel(cx, cz) == 0) continue;
                if (cz < 0) север++;
                if (cz > 0) юг++;
                if (cx < 0) запад++;
                if (cx > 0) восток++;
            }
        }
        int[] стороны = { север, юг, запад, восток };
        int сумма = север + юг + запад + восток;
        assertTrue(сумма > 100, "за 20 ночей должно заразиться заметное число чанков, вышло " + сумма);
        float среднее = сумма / 4f;
        for (int с : стороны) {
            assertTrue(Math.abs(с - среднее) < среднее * 0.45f,
                "распространение перекошено: " + север + "/" + юг + "/" + запад + "/" + восток);
        }
    }

    @Test
    void местностьЗамедляетЗаражение() {
        PlagueGrid быстрая = сОчагомВЦентре(4);
        PlagueGrid медленная = сОчагомВЦентре(4);
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                быстрая.setTerrain(cx, cz, 1.4f);
                медленная.setTerrain(cx, cz, 0.1f);
            }
        }
        for (int night = 1; night <= 10; night++) {
            SpreadEngine.runNightWith(быстрая, night, быстрые(), 1f, 0, rng(500 + night));
            SpreadEngine.runNightWith(медленная, night, быстрые(), 1f, 0, rng(500 + night));
        }
        assertTrue(быстрая.countInfected() > медленная.countInfected() * 2,
            "лава должна тормозить сильно: трава " + быстрая.countInfected()
                + ", лава " + медленная.countInfected());
    }

    @Test
    void сопротивлениеСнижаетВероятностьЗаражения() {
        PlagueGrid защищённая = сОчагомВЦентре(4);
        PlagueGrid открытая = сОчагомВЦентре(4);
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                защищённая.setResistance(cx, cz, 1.0f);
            }
        }
        for (int night = 1; night <= 10; night++) {
            SpreadEngine.runNightWith(защищённая, night, быстрые(), 1f, 0, rng(900 + night));
            SpreadEngine.runNightWith(открытая, night, быстрые(), 1f, 0, rng(900 + night));
        }
        assertEquals(1, защищённая.countInfected(),
            "при сопротивлении 1.0 заражение не должно распространяться вообще");
        assertTrue(открытая.countInfected() > 1);
    }

    @Test
    void уровеньРастётНаМестеПоРитмуФазы() {
        PlagueGrid g = сОчагомВЦентре(1);
        // фаза 0: рост раз в 3 ночи
        SpreadEngine.runNight(g, 1, false, rng(1));
        SpreadEngine.runNight(g, 2, false, rng(2));
        assertEquals(1, g.getLevel(0, 0), "на ночах 1 и 2 роста быть не должно");
        SpreadEngine.runNight(g, 3, false, rng(3));
        assertEquals(2, g.getLevel(0, 0), "на ночи 3 уровень должен подрасти");
    }

    @Test
    void уровеньНеПревышаетЧетыре() {
        PlagueGrid g = сОчагомВЦентре(4);
        for (int night = 1; night <= 60; night++) {
            SpreadEngine.runNight(g, night, false, rng(night));
        }
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                assertTrue(g.getLevel(cx, cz) <= PlagueConstants.MAX_NATURAL_LEVEL,
                    "естественное распространение не должно давать уровень выше 4");
            }
        }
    }

    @Test
    void шрамыТаютПоОднойНочи() {
        PlagueGrid g = пустая();
        g.setScar(3, 3, 5);
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(4, g.getScar(3, 3));
        assertEquals(0, r.scarsHealed(), "шрам ещё не зажил полностью");

        for (int i = 0; i < 4; i++) SpreadEngine.runNight(g, 2 + i, false, rng(i));
        assertEquals(0, g.getScar(3, 3), "через 5 ночей шрам должен исчезнуть");
    }

    @Test
    void заживлениеШрамаСообщаетсяВРезультате() {
        PlagueGrid g = пустая();
        g.setScar(3, 3, 1);
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(1, r.scarsHealed());
    }

    @Test
    void заражениеСбрасываетШрам() {
        PlagueGrid g = сОчагомВЦентре(4);
        g.setScar(1, 0, 5);
        // при вероятности 1.0 сосед заражается гарантированно за одну ночь
        SpreadEngine.runNightWith(g, 1, гарантированные(), 1f, 0, rng(1));
        assertTrue(g.getLevel(1, 0) > 0, "сосед должен был заразиться");
        assertEquals(0, g.getScar(1, 0), "заражение должно обнулить шрам");
    }

    @Test
    void шрамНеТаетНаЗаражённомЧанке() {
        PlagueGrid g = пустая();
        g.setLevel(3, 3, 2);
        g.setScar(3, 3, 5);
        SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(5, g.getScar(3, 3), "шрам считается только на чистых чанках");
    }

    @Test
    void одинаковыйСидДаётОдинаковыйРезультат() {
        PlagueGrid a = сОчагомВЦентре(3);
        PlagueGrid b = сОчагомВЦентре(3);
        for (int night = 1; night <= 15; night++) {
            SpreadEngine.runNight(a, night, false, rng(night));
            SpreadEngine.runNight(b, night, false, rng(night));
        }
        assertArrayEquals(a.levelsCopy(), b.levelsCopy(), "симуляция должна быть детерминированной");
    }

    @Test
    void пустаяСеткаОстаётсяПустой() {
        PlagueGrid g = пустая();
        SpreadEngine.NightResult r = SpreadEngine.runNight(g, 1, false, rng(1));
        assertEquals(0, r.newlyInfected());
        assertEquals(0, g.countInfected());
    }

    @Test
    void фазаВозвращаетсяВРезультате() {
        PlagueGrid g = пустая();
        assertEquals(0, SpreadEngine.runNight(g, 3, false, rng(1)).phase());
        assertEquals(3, SpreadEngine.runNight(g, 25, false, rng(1)).phase());
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

```bash
cd plaguecore && ./gradlew test --tests '*SpreadEngineTest*'
```

Ожидание: ошибка компиляции — `SpreadEngine` не найден.

- [ ] **Step 3: Создать `SpreadEngine.java`**

```java
package dev.denthe.plaguecore.core;

import dev.denthe.plaguecore.PlagueConstants;

import java.util.random.RandomGenerator;

/**
 * Ночной тик распространения. Спек, раздел 6.
 *
 * Порядок внутри ночи важен:
 *   1. снимок уровней — чтобы рост этой ночи не каскадировал сам в себя
 *   2. рост на месте
 *   3. экспансия по снимку, в перемешанном порядке источников
 *   4. таяние шрамов
 *
 * Перемешивание источников нужно, чтобы бюджет не доставался всегда
 * чанкам с начала массива: без него заражение systematically ползло бы
 * на север-запад.
 */
public final class SpreadEngine {
    private SpreadEngine() {}

    public record NightResult(int newlyInfected, int grown, int scarsHealed, int phase) {}

    private static final int[] СМЕЩЕНИЯ_X = { -1, 0, 1, -1, 1, -1, 0, 1 };
    private static final int[] СМЕЩЕНИЯ_Z = { -1, -1, -1, 0, 0, 1, 1, 1 };

    /** Ночь по расписанию фаз. */
    public static NightResult runNight(PlagueGrid grid, int night, boolean slept, RandomGenerator rng) {
        int phase = PhaseTable.phaseForNight(night);
        PhaseParams params = PhaseTable.paramsFor(phase);
        float budgetMultiplier = slept ? PlagueConstants.SLEEP_BUDGET_MULTIPLIER : 1.0f;
        int extraGrowth = slept ? PlagueConstants.SLEEP_EXTRA_GROWTH : 0;
        NightResult r = runNightWith(grid, night, params, budgetMultiplier, extraGrowth, rng);
        return new NightResult(r.newlyInfected(), r.grown(), r.scarsHealed(), phase);
    }

    /** Ночь с явно заданными параметрами. Используется генератором старта. */
    public static NightResult runNightWith(PlagueGrid grid, int night, PhaseParams params,
                                           float budgetMultiplier, int extraGrowth,
                                           RandomGenerator rng) {
        final byte[] снимок = grid.levelsCopy();
        final int cells = grid.cellCount();

        // ── 1. рост на месте ───────────────────────────────────────────
        int выросло = 0;
        boolean растимСегодня = params.growthEveryNights() <= 1
            || night % params.growthEveryNights() == 0;
        if (растимСегодня) {
            int прирост = params.growthAmount() + extraGrowth;
            for (int i = 0; i < cells; i++) {
                int было = снимок[i];
                if (было > 0 && было < PlagueConstants.MAX_NATURAL_LEVEL) {
                    grid.setLevelAt(i, Math.min(PlagueConstants.MAX_NATURAL_LEVEL, было + прирост));
                    выросло++;
                }
            }
        }

        // ── 2. экспансия ───────────────────────────────────────────────
        int[] источники = собратьИсточники(снимок, cells);
        перемешать(источники, rng);

        int бюджет = Math.round(params.budget() * budgetMultiplier);
        int заражено = 0;

        for (int idx : источники) {
            if (бюджет <= 0) break;
            int cx = grid.chunkXOf(idx);
            int cz = grid.chunkZOf(idx);
            float силаИсточника = снимок[idx] / (float) PlagueConstants.MAX_NATURAL_LEVEL;

            for (int d = 0; d < 8 && бюджет > 0; d++) {
                int nx = cx + СМЕЩЕНИЯ_X[d];
                int nz = cz + СМЕЩЕНИЯ_Z[d];
                int ni = grid.index(nx, nz);
                if (ni < 0 || grid.getLevelAt(ni) != 0) continue;

                float p = params.base()
                    * силаИсточника
                    * grid.getTerrainAt(ni)
                    * (1f - grid.getResistanceAt(ni));

                if (rng.nextFloat() < p) {
                    grid.setLevelAt(ni, 1);
                    grid.setScarAt(ni, 0);
                    бюджет--;
                    заражено++;
                }
            }
        }

        // ── 3. таяние шрамов ───────────────────────────────────────────
        int зажило = 0;
        for (int i = 0; i < cells; i++) {
            if (grid.getLevelAt(i) != 0) continue;
            int шрам = grid.getScarAt(i);
            if (шрам > 0) {
                grid.setScarAt(i, шрам - 1);
                if (шрам - 1 == 0) зажило++;
            }
        }

        return new NightResult(заражено, выросло, зажило, PhaseTable.phaseForNight(night));
    }

    private static int[] собратьИсточники(byte[] снимок, int cells) {
        int n = 0;
        for (int i = 0; i < cells; i++) if (снимок[i] > 0) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < cells; i++) if (снимок[i] > 0) out[k++] = i;
        return out;
    }

    private static void перемешать(int[] a, RandomGenerator rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

```bash
cd plaguecore && ./gradlew test --tests '*SpreadEngineTest*'
```

Ожидание: PASS, 15 тестов.

Если тест симметрии падает — скорее всего забыто перемешивание
источников. Если падает тест «сон удваивает бюджет» — проверить, что
множитель применяется к `budget`, а не к вероятности.

- [ ] **Step 5: Прогнать весь набор тестов**

```bash
cd plaguecore && ./gradlew test
```

Ожидание: PASS, все тесты четырёх классов.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src
git commit -m "SpreadEngine: ночной тик распространения чумы

Клеточный автомат по спеку 6.1-6.3: рост на месте, экспансия по
снимку в перемешанном порядке, таяние шрамов. Перемешивание источников
убирает перекос распространения на северо-запад.

15 тестов: бюджет, сон, симметрия, местность, сопротивление,
детерминизм, потолок уровня, шрамы.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: StartGenerator — стартовое заражение

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/StartGenerator.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/StartGeneratorTest.java`

**Interfaces:**
- Consumes: `PlagueGrid`, `SpreadEngine`, `PhaseParams`
- Produces:
  - `record GenerationResult(int nightsSimulated, float achievedFraction, int epicenterCount)`
  - `StartGenerator.generate(PlagueGrid grid, float targetFraction, long[] epicenters, RandomGenerator rng)` → `GenerationResult`
  - `StartGenerator.packChunk(int cx, int cz)` → `long`, `unpackX(long)`, `unpackZ(long)`

- [ ] **Step 1: Написать падающие тесты**

`StartGeneratorTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class StartGeneratorTest {

    private static RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }

    private static PlagueGrid пустая() {
        return new PlagueGrid(63, -31, -31);
    }

    @Test
    void упаковкаКоординатЧанкаОбратима() {
        long p = StartGenerator.packChunk(-17, 42);
        assertEquals(-17, StartGenerator.unpackX(p));
        assertEquals(42, StartGenerator.unpackZ(p));
    }

    @Test
    void генераторДостигаетЗаданнойДолиСТочностьюДвухПроцентов() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.10f, очаги, rng(123));

        assertEquals(0.10f, r.achievedFraction(), 0.02f,
            "должно получиться 10% ±2%, вышло " + r.achievedFraction());
        assertEquals(0.10f, g.infectedFraction(), 0.02f);
    }

    @Test
    void генераторРаботаетДляРазныхДолей() {
        for (float цель : new float[] { 0.05f, 0.10f, 0.25f, 0.50f }) {
            PlagueGrid g = пустая();
            long[] очаги = { StartGenerator.packChunk(0, 0), StartGenerator.packChunk(-15, 12) };
            StartGenerator.generate(g, цель, очаги, rng(7));
            assertEquals(цель, g.infectedFraction(), 0.02f,
                "цель " + цель + ", получено " + g.infectedFraction());
        }
    }

    @Test
    void одинаковыйСидДаётОдинаковыйМир() {
        long[] очаги = { StartGenerator.packChunk(3, -4) };
        PlagueGrid a = пустая();
        PlagueGrid b = пустая();
        StartGenerator.generate(a, 0.15f, очаги, rng(999));
        StartGenerator.generate(b, 0.15f, очаги, rng(999));
        assertArrayEquals(a.levelsCopy(), b.levelsCopy());
    }

    @Test
    void разныеСидыДаютРазныйМир() {
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        PlagueGrid a = пустая();
        PlagueGrid b = пустая();
        StartGenerator.generate(a, 0.15f, очаги, rng(1));
        StartGenerator.generate(b, 0.15f, очаги, rng(2));
        assertFalse(java.util.Arrays.equals(a.levelsCopy(), b.levelsCopy()));
    }

    @Test
    void очагиСтановятсяЗаражённымиСразу() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(10, 10), StartGenerator.packChunk(-10, -10) };
        StartGenerator.generate(g, 0.05f, очаги, rng(5));
        assertTrue(g.getLevel(10, 10) > 0);
        assertTrue(g.getLevel(-10, -10) > 0);
    }

    @Test
    void заражениеРастётВокругОчага() {
        PlagueGrid g = пустая();
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.generate(g, 0.10f, очаги, rng(3));

        int рядом = 0;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                if (g.getLevel(cx, cz) > 0) рядом++;
            }
        }
        assertTrue(рядом > 50, "вокруг очага должно быть плотно, вышло " + рядом);
    }

    @Test
    void безОчаговНичегоНеПроисходит() {
        PlagueGrid g = пустая();
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.10f, new long[0], rng(1));
        assertEquals(0, g.countInfected());
        assertEquals(0, r.nightsSimulated());
    }

    @Test
    void генераторНеЗависаетПриНедостижимойЦели() {
        PlagueGrid g = пустая();
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) g.setTerrain(cx, cz, 0f);
        }
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.GenerationResult r = StartGenerator.generate(g, 0.90f, очаги, rng(1));
        assertTrue(r.nightsSimulated() <= 2000, "должен упереться в предохранитель");
    }

    @Test
    void местностьВлияетНаФормуОчага() {
        PlagueGrid g = пустая();
        // западная половина — лава, восточная — трава
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                g.setTerrain(cx, cz, cx < 0 ? 0.1f : 1.4f);
            }
        }
        long[] очаги = { StartGenerator.packChunk(0, 0) };
        StartGenerator.generate(g, 0.10f, очаги, rng(11));

        int запад = 0, восток = 0;
        for (int cx = -31; cx <= 31; cx++) {
            for (int cz = -31; cz <= 31; cz++) {
                if (g.getLevel(cx, cz) == 0) continue;
                if (cx < 0) запад++; else if (cx > 0) восток++;
            }
        }
        assertTrue(восток > запад * 2,
            "по траве должно уйти заметно дальше: запад " + запад + ", восток " + восток);
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

```bash
cd plaguecore && ./gradlew test --tests '*StartGeneratorTest*'
```

Ожидание: ошибка компиляции — `StartGenerator` не найден.

- [ ] **Step 3: Создать `StartGenerator.java`**

```java
package dev.denthe.plaguecore.core;

import java.util.random.RandomGenerator;

/**
 * Генератор стартового состояния. Спек, раздел 12.3.
 *
 * Сажает очаги и прогоняет ускоренную симуляцию, пока доля заражённых
 * чанков не достигнет цели. Работает только на карте чанков —
 * материализация блоков не запускается, поэтому весь мир генерируется
 * за секунды.
 *
 * Симуляция использует собственные параметры, не связанные с расписанием
 * фаз: генерация — это не игровое время, а подготовка стартового расклада.
 */
public final class StartGenerator {
    private StartGenerator() {}

    public record GenerationResult(int nightsSimulated, float achievedFraction, int epicenterCount) {}

    /** Вероятность заражения при генерации: агрессивнее любой игровой фазы. */
    private static final float GEN_BASE = 0.12f;

    /** Потолок новых чанков за одну ночь генерации. */
    private static final int GEN_BUDGET = 400;

    /** Раз во сколько ночей растёт уровень при генерации. */
    private static final int GEN_GROWTH_EVERY = 2;

    /** Уровень, с которого начинают очаги. */
    private static final int EPICENTER_LEVEL = 3;

    /** Предохранитель от зацикливания, если цель недостижима. */
    private static final int MAX_NIGHTS = 2000;

    public static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) { return (int) (packed >> 32); }

    public static int unpackZ(long packed) { return (int) packed; }

    public static GenerationResult generate(PlagueGrid grid, float targetFraction,
                                            long[] epicenters, RandomGenerator rng) {
        if (epicenters.length == 0) {
            return new GenerationResult(0, grid.infectedFraction(), 0);
        }

        for (long p : epicenters) {
            grid.setLevel(unpackX(p), unpackZ(p), EPICENTER_LEVEL);
        }

        float цель = Math.min(Math.max(targetFraction, 0f), 1f);
        int целевыхЯчеек = Math.round(цель * grid.cellCount());

        int ночей = 0;
        int безПрогресса = 0;
        int прошлоеКоличество = grid.countInfected();

        while (прошлоеКоличество < целевыхЯчеек && ночей < MAX_NIGHTS) {
            // Бюджет ночи ограничен остатком до цели: без этого одна щедрая
            // ночь может перепрыгнуть цель на сотни чанков.
            int осталось = Math.max(1, целевыхЯчеек - прошлоеКоличество);
            PhaseParams params = new PhaseParams(
                GEN_BASE, Math.min(GEN_BUDGET, осталось), GEN_GROWTH_EVERY, 1);

            SpreadEngine.runNightWith(grid, ночей + 1, params, 1.0f, 0, rng);
            ночей++;

            int сейчас = grid.countInfected();
            безПрогресса = (сейчас == прошлоеКоличество) ? безПрогресса + 1 : 0;
            прошлоеКоличество = сейчас;

            // если 50 ночей подряд ничего не меняется — дальше некуда расти
            if (безПрогресса >= 50) break;
        }

        return new GenerationResult(ночей, grid.infectedFraction(), epicenters.length);
    }
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

```bash
cd plaguecore && ./gradlew test --tests '*StartGeneratorTest*'
```

Ожидание: PASS, 10 тестов.

Точность обеспечивается ограничением бюджета остатком до цели: последняя
ночь генерации не может добавить больше, чем нужно. Если тест всё же
падает с перелётом — проверить, что `Math.min(GEN_BUDGET, осталось)`
не потерялся при копировании.

- [ ] **Step 5: Коммит**

```bash
git add plaguecore/src
git commit -m "StartGenerator: стартовое заражение мира до заданной доли

Сажает очаги и прогоняет ускоренную симуляцию по карте чанков,
без материализации. Детерминирован по сиду — расклад можно
перегенерировать, пока не понравится. Есть предохранитель от
зацикливания при недостижимой цели.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: PlagueGridCodec — сериализация сеток

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/core/PlagueGridCodec.java`
- Test: `plaguecore/src/test/java/dev/denthe/plaguecore/core/PlagueGridCodecTest.java`

**Interfaces:**
- Consumes: `PlagueGrid`
- Produces:
  - `PlagueGridCodec.encode(PlagueGrid)` → `byte[]`
  - `PlagueGridCodec.decode(byte[])` → `PlagueGrid`
  - `PlagueGridCodec.FORMAT_VERSION = 1`

- [ ] **Step 1: Написать падающие тесты**

`PlagueGridCodecTest.java`:

```java
package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlagueGridCodecTest {

    private PlagueGrid заполненная() {
        PlagueGrid g = new PlagueGrid(63, -31, -31);
        g.setLevel(0, 0, 4);
        g.setLevel(-31, -31, 1);
        g.setLevel(31, 31, 5);
        g.setResistance(5, 5, 0.75f);
        g.setScar(-7, 3, 4);
        g.setTerrain(2, 2, 1.4f);
        g.setTerrain(3, 3, 0.1f);
        return g;
    }

    @Test
    void кодированиеИДекодированиеСохраняютВсёСодержимое() {
        PlagueGrid оригинал = заполненная();
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(оригинал));

        assertEquals(оригинал.size(), копия.size());
        assertEquals(оригинал.originX(), копия.originX());
        assertEquals(оригинал.originZ(), копия.originZ());

        assertEquals(4, копия.getLevel(0, 0));
        assertEquals(1, копия.getLevel(-31, -31));
        assertEquals(5, копия.getLevel(31, 31));
        assertEquals(0.75f, копия.getResistance(5, 5), 0.01f);
        assertEquals(4, копия.getScar(-7, 3));
        assertEquals(1.4f, копия.getTerrain(2, 2), 0.05f);
        assertEquals(0.1f, копия.getTerrain(3, 3), 0.05f);
    }

    @Test
    void всеЯчейкиСовпадаютПослеКруга() {
        PlagueGrid оригинал = заполненная();
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(оригинал));
        assertArrayEquals(оригинал.levelsCopy(), копия.levelsCopy());
    }

    @Test
    void размерБлобаПредсказуем() {
        PlagueGrid g = заполненная();
        byte[] blob = PlagueGridCodec.encode(g);
        int ожидаемо = 1 + 4 + 4 + 4 + 4 * (63 * 63);
        assertEquals(ожидаемо, blob.length,
            "версия + размер + originX + originZ + четыре массива");
        assertTrue(blob.length < 20_000, "весь мир должен весить меньше 20 КБ");
    }

    @Test
    void перваяЯчейкаЭтоВерсияФормата() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        assertEquals(PlagueGridCodec.FORMAT_VERSION, blob[0]);
    }

    @Test
    void неизвестнаяВерсияОтвергается() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        blob[0] = 99;
        assertThrows(IllegalArgumentException.class, () -> PlagueGridCodec.decode(blob));
    }

    @Test
    void обрезанныйБлобОтвергается() {
        byte[] blob = PlagueGridCodec.encode(заполненная());
        byte[] обрезанный = java.util.Arrays.copyOf(blob, blob.length / 2);
        assertThrows(IllegalArgumentException.class, () -> PlagueGridCodec.decode(обрезанный));
    }

    @Test
    void пустаяСеткаПереживаетКруг() {
        PlagueGrid пустая = new PlagueGrid(63, -31, -31);
        PlagueGrid копия = PlagueGridCodec.decode(PlagueGridCodec.encode(пустая));
        assertEquals(0, копия.countInfected());
        assertEquals(1.0f, копия.getTerrain(0, 0), 0.05f);
    }
}
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

```bash
cd plaguecore && ./gradlew test --tests '*PlagueGridCodecTest*'
```

Ожидание: ошибка компиляции — `PlagueGridCodec` не найден.

- [ ] **Step 3: Создать `PlagueGridCodec.java`**

```java
package dev.denthe.plaguecore.core;

import java.nio.ByteBuffer;

/**
 * Сериализация сеток в плоский массив байт.
 *
 * Формат:
 *   [0]      версия формата
 *   [1..4]   размер стороны
 *   [5..8]   originX
 *   [9..12]  originZ
 *   далее    level, resistance, scar, terrain — по size*size байт каждый
 *
 * Отдельно от Minecraft, чтобы тестировать без запуска игры.
 * PlagueState просто кладёт результат в ByteArrayTag.
 */
public final class PlagueGridCodec {
    private PlagueGridCodec() {}

    public static final byte FORMAT_VERSION = 1;

    private static final int HEADER_BYTES = 1 + 4 + 4 + 4;

    public static byte[] encode(PlagueGrid grid) {
        int cells = grid.cellCount();
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + cells * 4);
        buf.put(FORMAT_VERSION);
        buf.putInt(grid.size());
        buf.putInt(grid.originX());
        buf.putInt(grid.originZ());
        buf.put(grid.rawLevels());
        buf.put(grid.rawResistance());
        buf.put(grid.rawScar());
        buf.put(grid.rawTerrain());
        return buf.array();
    }

    public static PlagueGrid decode(byte[] data) {
        if (data == null || data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("блоб слишком короткий: "
                + (data == null ? "null" : data.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte version = buf.get();
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("неизвестная версия формата сетки: " + version);
        }
        int size = buf.getInt();
        int originX = buf.getInt();
        int originZ = buf.getInt();

        if (size <= 0 || size > 4096) {
            throw new IllegalArgumentException("некорректный размер сетки: " + size);
        }
        int cells = size * size;
        if (data.length != HEADER_BYTES + cells * 4) {
            throw new IllegalArgumentException("длина блоба не соответствует размеру сетки: ожидалось "
                + (HEADER_BYTES + cells * 4) + ", получено " + data.length);
        }

        byte[] level = new byte[cells];
        byte[] resistance = new byte[cells];
        byte[] scar = new byte[cells];
        byte[] terrain = new byte[cells];
        buf.get(level);
        buf.get(resistance);
        buf.get(scar);
        buf.get(terrain);

        return new PlagueGrid(size, originX, originZ, level, resistance, scar, terrain);
    }
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

```bash
cd plaguecore && ./gradlew test --tests '*PlagueGridCodecTest*'
```

Ожидание: PASS, 7 тестов.

- [ ] **Step 5: Прогнать весь набор**

```bash
cd plaguecore && ./gradlew test
```

Ожидание: PASS. На этом вся математика ядра готова и покрыта тестами,
ни один из которых не запускает Minecraft.

- [ ] **Step 6: Коммит**

```bash
git add plaguecore/src
git commit -m "PlagueGridCodec: сериализация сеток в byte[]

Плоский формат с версией и проверкой длины. Весь мир — меньше 16 КБ.
Отдельно от Minecraft, чтобы круг кодирование-декодирование
тестировался без запуска игры.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: PlagueState — хранение состояния в мире

Первая задача, которая касается Minecraft.

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueState.java`

**Interfaces:**
- Consumes: `PlagueGrid`, `PlagueGridCodec`, `PlagueConstants`, `StartGenerator`
- Produces:
  - `PlagueState.get(ServerLevel)` → `PlagueState`
  - `grid()` → `PlagueGrid`, `night()` → `int`, `phase()` → `int`
  - `advanceNight()`, `setNight(int)`, `isPaused()`, `setPaused(boolean)`
  - `epicenters()` → `LongList`, `addEpicenter(int,int)`, `removeEpicenter(int,int)`
  - `boolean isTerrainInitialized()`, `void markTerrainInitialized()`

- [ ] **Step 1: Создать `PlagueState.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.PlagueGridCodec;
import dev.denthe.plaguecore.core.PhaseTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Состояние эпидемии в мире. Источник истины — сетки внутри PlagueGrid.
 *
 * Хранится через ванильный SavedData, то есть переживает перезапуск
 * сервера и лежит рядом с остальными данными мира.
 */
public class PlagueState extends SavedData {

    private static final String DATA_NAME = "plaguecore_state";

    private static final String KEY_GRID = "Grid";
    private static final String KEY_NIGHT = "Night";
    private static final String KEY_PAUSED = "Paused";
    private static final String KEY_TERRAIN_READY = "TerrainReady";
    private static final String KEY_EPICENTERS = "Epicenters";

    private PlagueGrid grid;
    private int night;
    private boolean paused;
    private boolean terrainInitialized;
    private final List<Long> epicenters = new ArrayList<>();

    /** Флаг «ночь этих суток уже обработана», в NBT не пишется. */
    private long lastProcessedDay = -1;

    public PlagueState() {
        int size = PlagueConstants.GRID_SIZE_CHUNKS;
        this.grid = new PlagueGrid(size, -(size / 2), -(size / 2));
        this.night = 0;
        this.paused = false;
        this.terrainInitialized = false;
    }

    public static PlagueState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PlagueState::new, PlagueState::load),
            DATA_NAME
        );
    }

    private static PlagueState load(CompoundTag tag, HolderLookup.Provider lookup) {
        PlagueState st = new PlagueState();
        if (tag.contains(KEY_GRID)) {
            st.grid = PlagueGridCodec.decode(tag.getByteArray(KEY_GRID));
        }
        st.night = tag.getInt(KEY_NIGHT);
        st.paused = tag.getBoolean(KEY_PAUSED);
        st.terrainInitialized = tag.getBoolean(KEY_TERRAIN_READY);
        st.epicenters.clear();
        for (long p : tag.getLongArray(KEY_EPICENTERS)) st.epicenters.add(p);
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putByteArray(KEY_GRID, PlagueGridCodec.encode(grid));
        tag.putInt(KEY_NIGHT, night);
        tag.putBoolean(KEY_PAUSED, paused);
        tag.putBoolean(KEY_TERRAIN_READY, terrainInitialized);
        long[] eps = new long[epicenters.size()];
        for (int i = 0; i < eps.length; i++) eps[i] = epicenters.get(i);
        tag.putLongArray(KEY_EPICENTERS, eps);
        return tag;
    }

    public PlagueGrid grid() { return grid; }

    public int night() { return night; }

    public void setNight(int value) { this.night = Math.max(0, value); setDirty(); }

    public void advanceNight() { this.night++; setDirty(); }

    public int phase() { return PhaseTable.phaseForNight(night); }

    public boolean isPaused() { return paused; }

    public void setPaused(boolean value) { this.paused = value; setDirty(); }

    public boolean isTerrainInitialized() { return terrainInitialized; }

    public void markTerrainInitialized() { this.terrainInitialized = true; setDirty(); }

    public List<Long> epicenters() { return List.copyOf(epicenters); }

    public long[] epicentersArray() {
        long[] out = new long[epicenters.size()];
        for (int i = 0; i < out.length; i++) out[i] = epicenters.get(i);
        return out;
    }

    public void addEpicenter(long packed) {
        if (!epicenters.contains(packed)) {
            epicenters.add(packed);
            setDirty();
        }
    }

    public void removeEpicenter(long packed) {
        if (epicenters.remove(packed)) setDirty();
    }

    public long lastProcessedDay() { return lastProcessedDay; }

    public void setLastProcessedDay(long day) { this.lastProcessedDay = day; }
}
```

- [ ] **Step 2: Убедиться, что проект компилируется**

```bash
cd plaguecore && ./gradlew compileJava
```

Ожидание: BUILD SUCCESSFUL.

Если `SavedData.Factory` не находится — проверить, что версия NeoForge
в `gradle.properties` действительно 21.1.x: в 1.20.x был другой API.

- [ ] **Step 3: Убедиться, что тест чистоты ядра всё ещё проходит**

`PlagueState` лежит в `mc`, а не в `core`, поэтому импорты Minecraft
в нём разрешены. Тест должен это подтвердить.

```bash
cd plaguecore && ./gradlew test --tests '*CorePurityTest*'
```

Ожидание: PASS.

- [ ] **Step 4: Коммит**

```bash
git add plaguecore/src
git commit -m "PlagueState: хранение состояния эпидемии через SavedData

Сетки, номер ночи, пауза и очаги переживают перезапуск сервера.
Сериализация делегирована PlagueGridCodec, который тестируется
отдельно и без Minecraft.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: TerrainInitializer — заполнение сетки местности

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/TerrainInitializer.java`

**Interfaces:**
- Consumes: `PlagueGrid`, `PlagueState`
- Produces: `TerrainInitializer.initialize(ServerLevel level, PlagueState state)` → `int` (число заполненных ячеек)

**Почему так:** множитель местности нужен для **всех** чанков, включая
незагруженные. Читать блоки нельзя — это заставило бы сгенерировать весь
мир. Вместо этого спрашиваем биом у генератора: `BiomeSource.getNoiseBiome`
отвечает по шуму, не трогая чанки. Считаем один раз при первом запуске
и складываем в сетку — дальше ночной тик работает с готовыми числами.

- [ ] **Step 1: Создать `TerrainInitializer.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Однократное заполнение сетки множителей местности. Спек, раздел 6.4.
 *
 * Множитель берётся из биома, а не из блоков: биом можно спросить у
 * генератора без загрузки чанка, а блоки — нельзя. Для наших целей
 * этого достаточно: нас интересует «живая местность или мёртвая»,
 * а не конкретный блок.
 */
public final class TerrainInitializer {
    private TerrainInitializer() {}

    private static final float ЖИВАЯ = 1.4f;   // лес, джунгли, равнины
    private static final float ОБЫЧНАЯ = 1.0f; // земля, песок
    private static final float КАМЕНЬ = 0.6f;  // горы, скалы
    private static final float ВОДА = 0.4f;    // океаны, реки
    private static final float МЁРТВАЯ = 0.1f; // лава, незер-подобное

    public static int initialize(ServerLevel level, PlagueState state) {
        PlagueGrid grid = state.grid();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();

        // getNoiseBiome работает в «квартах» — блок, делённый на 4
        final int quartY = level.getSeaLevel() >> 2;

        int заполнено = 0;
        for (int cz = grid.originZ(); cz < grid.originZ() + grid.size(); cz++) {
            for (int cx = grid.originX(); cx < grid.originX() + grid.size(); cx++) {
                int quartX = (cx << 4) >> 2;
                int quartZ = (cz << 4) >> 2;
                Holder<Biome> biome = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
                grid.setTerrain(cx, cz, множительДля(biome));
                заполнено++;
            }
        }
        state.markTerrainInitialized();
        state.setDirty();
        return заполнено;
    }

    private static float множительДля(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)
            || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return ВОДА;
        }
        if (biome.is(BiomeTags.IS_NETHER)) {
            return МЁРТВАЯ;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_END)) {
            return КАМЕНЬ;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)
            || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return ЖИВАЯ;
        }
        return ОБЫЧНАЯ;
    }
}
```

- [ ] **Step 2: Убедиться, что проект компилируется**

```bash
cd plaguecore && ./gradlew compileJava
```

Ожидание: BUILD SUCCESSFUL.

Если какого-то тега `BiomeTags` нет в 1.21.1 — убрать его из условия;
список тегов между версиями слегка меняется, а нам важны только
крупные категории.

- [ ] **Step 3: Коммит**

```bash
git add plaguecore/src
git commit -m "TerrainInitializer: множители местности из биомов

Заполняет сетку terrain один раз при первом запуске. Биом берётся
через BiomeSource.getNoiseBiome — это не загружает и не генерирует
чанки, поэтому весь мир размечается за один проход.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: NightHook — наступление ночи и сон

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/NightHook.java`
- Modify: `plaguecore/src/main/java/dev/denthe/plaguecore/PlagueCore.java`

**Interfaces:**
- Consumes: `PlagueState`, `TerrainInitializer`, `SpreadEngine`
- Produces:
  - `NightHook.runNight(ServerLevel, PlagueState, boolean slept)` → `SpreadEngine.NightResult`
  - Подписки на `ServerTickEvent.Post`, `SleepFinishedTimeEvent`, `ServerStartedEvent`

**Как решается стык сна и заката.** Ночь обрабатывается один раз за
игровые сутки, в момент заката. Если игроки после этого лягут спать,
`SleepFinishedTimeEvent` доложит об этом — и мы догоняем недостающую
половину: ещё один бюджет и ещё один прирост. Суммарно получается
ровно то, что описано в спеке 6.3: двойной бюджет и рост +1.

- [ ] **Step 1: Создать `NightHook.java`**

```java
package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PhaseParams;
import dev.denthe.plaguecore.core.PhaseTable;
import dev.denthe.plaguecore.core.SpreadEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Определяет наступление ночи и запускает ночной тик. Спек, разделы 6.1 и 6.3.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class NightHook {
    private NightHook() {}

    /** Время суток, с которого считаем, что наступила ночь. */
    private static final long ЗАКАТ = 13000L;

    private static final long СУТКИ = 24000L;

    /** Проверяем время не каждый тик — раз в секунду более чем достаточно. */
    private static final int ИНТЕРВАЛ_ПРОВЕРКИ = 20;

    private static int счётчик = 0;

    @SubscribeEvent
    public static void приСтартеСервера(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        PlagueState state = PlagueState.get(overworld);
        if (!state.isTerrainInitialized()) {
            long t0 = System.currentTimeMillis();
            int ячеек = TerrainInitializer.initialize(overworld, state);
            PlagueCore.LOG.info("Сетка местности заполнена: {} ячеек за {} мс",
                ячеек, System.currentTimeMillis() - t0);
        }
    }

    @SubscribeEvent
    public static void приТикеСервера(ServerTickEvent.Post event) {
        if (++счётчик < ИНТЕРВАЛ_ПРОВЕРКИ) return;
        счётчик = 0;

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        PlagueState state = PlagueState.get(overworld);
        if (state.isPaused()) return;

        long время = overworld.getDayTime();
        long сутки = время / СУТКИ;
        long вСутках = время % СУТКИ;

        if (вСутках >= ЗАКАТ && state.lastProcessedDay() != сутки) {
            state.setLastProcessedDay(сутки);
            state.advanceNight();
            SpreadEngine.NightResult r = runNight(overworld, state, false);
            PlagueCore.LOG.info("Ночь {} (фаза {}): заражено {}, выросло {}, зажило шрамов {}",
                state.night(), r.phase(), r.newlyInfected(), r.grown(), r.scarsHealed());
        }
    }

    @SubscribeEvent
    public static void приПробуждении(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        PlagueState state = PlagueState.get(level);
        if (state.isPaused()) return;

        SpreadEngine.NightResult r = догнатьЗаСон(level, state);
        PlagueCore.LOG.info("Игроки спали — чума ускорилась: дополнительно заражено {}",
            r.newlyInfected());
    }

    /** Обычный ночной тик. */
    public static SpreadEngine.NightResult runNight(ServerLevel level, PlagueState state, boolean slept) {
        RandomGenerator rng = генератор(level, state.night());
        return SpreadEngine.runNight(state.grid(), state.night(), slept, rng);
    }

    /**
     * Доначисление за сон. Ночь уже была обработана на закате с обычным
     * бюджетом, поэтому добавляем разницу: ещё один бюджет и ещё один
     * прирост. В сумме выходит ровно двойная ночь из спека 6.3.
     */
    private static SpreadEngine.NightResult догнатьЗаСон(ServerLevel level, PlagueState state) {
        int phase = PhaseTable.phaseForNight(state.night());
        PhaseParams params = PhaseTable.paramsFor(phase);
        float добавка = PlagueConstants.SLEEP_BUDGET_MULTIPLIER - 1.0f;
        RandomGenerator rng = генератор(level, state.night() * 31L + 7L);
        return SpreadEngine.runNightWith(
            state.grid(), state.night(), params,
            добавка, PlagueConstants.SLEEP_EXTRA_GROWTH, rng);
    }

    /** Детерминированный генератор: одна и та же ночь в одном мире даёт один результат. */
    private static RandomGenerator генератор(ServerLevel level, long salt) {
        long seed = level.getSeed() ^ (salt * 0x9E3779B97F4A7C15L);
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }
}
```

- [ ] **Step 2: Убедиться, что проект компилируется**

```bash
cd plaguecore && ./gradlew compileJava
```

Ожидание: BUILD SUCCESSFUL.

Если `ServerTickEvent.Post` не находится — в некоторых версиях NeoForge
класс лежит в `net.neoforged.neoforge.event.tick.ServerTickEvent`;
проверить импорт по автодополнению.

- [ ] **Step 3: Запустить сервер и убедиться, что заполнилась местность**

```bash
cd plaguecore && ./gradlew runServer
```

Ожидание: в логе строка вида
`Сетка местности заполнена: 3969 ячеек за NN мс`.

Число ячеек должно быть ровно 3969 (63 × 63). Время — десятки
миллисекунд; если счёт идёт на минуты, значит вместо `getNoiseBiome`
где-то дёргается загрузка чанков.

Остановить сервер командой `stop`.

- [ ] **Step 4: Коммит**

```bash
git add plaguecore/src
git commit -m "NightHook: ночной тик и ускорение при сне

Ночь обрабатывается один раз за игровые сутки на закате. Если игроки
после этого поспят, SleepFinishedTimeEvent доначисляет недостающую
половину — в сумме двойной бюджет и рост +1, как в спеке 6.3.

Генератор случайных чисел детерминирован от сида мира и номера ночи:
одна и та же ночь всегда даёт один результат.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: Команды /plague

**Files:**
- Create: `plaguecore/src/main/java/dev/denthe/plaguecore/mc/PlagueCommands.java`

**Interfaces:**
- Consumes: `PlagueState`, `SpreadEngine`, `StartGenerator`, `NightHook`
- Produces: команды `/plague info | night | fastforward | setphase | pause | resume | generate | seed | remove`

- [ ] **Step 1: Создать `PlagueCommands.java`**

```java
package dev.denthe.plaguecore.mc;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.SpreadEngine;
import dev.denthe.plaguecore.core.StartGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Админские команды. Спек, раздел 12.1.
 * В этом плане реализована часть, не требующая материализации блоков.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueCommands {
    private PlagueCommands() {}

    @SubscribeEvent
    public static void зарегистрировать(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> корень = Commands.literal("plague")
            .requires(s -> s.hasPermission(2));

        корень.then(Commands.literal("info").executes(PlagueCommands::info));

        корень.then(Commands.literal("night").executes(PlagueCommands::ночь));

        корень.then(Commands.literal("fastforward")
            .then(Commands.argument("nights", IntegerArgumentType.integer(1, 500))
                .executes(PlagueCommands::прогнать)));

        корень.then(Commands.literal("setphase")
            .then(Commands.argument("phase", IntegerArgumentType.integer(0, 4))
                .executes(PlagueCommands::установитьФазу)));

        корень.then(Commands.literal("pause").executes(c -> пауза(c.getSource(), true)));
        корень.then(Commands.literal("resume").executes(c -> пауза(c.getSource(), false)));

        корень.then(Commands.literal("generate")
            .then(Commands.argument("percent", FloatArgumentType.floatArg(0.01f, 1.0f))
                .executes(PlagueCommands::сгенерировать)));

        корень.then(Commands.literal("seed")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, true))));

        корень.then(Commands.literal("remove")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(c -> очаг(c, false))));

        event.getDispatcher().register(корень);
    }

    private static ServerLevel мир(CommandSourceStack src) {
        return src.getServer().getLevel(Level.OVERWORLD);
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        PlagueGrid g = st.grid();

        int[] поУровням = new int[6];
        for (int cz = g.originZ(); cz < g.originZ() + g.size(); cz++) {
            for (int cx = g.originX(); cx < g.originX() + g.size(); cx++) {
                поУровням[g.getLevel(cx, cz)]++;
            }
        }

        CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> Component.literal("=== Состояние чумы ==="), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Ночь %d, фаза %d%s", st.night(), st.phase(),
                st.isPaused() ? " (ПАУЗА)" : "")), false);
        s.sendSuccess(() -> Component.literal(
            String.format("Заражено: %.1f%% (%d из %d чанков)",
                g.infectedFraction() * 100f, g.countInfected(), g.cellCount())), false);
        for (int lvl = 1; lvl <= 5; lvl++) {
            final int l = lvl, n = поУровням[lvl];
            if (n > 0) s.sendSuccess(() -> Component.literal("  уровень " + l + ": " + n), false);
        }
        s.sendSuccess(() -> Component.literal("Очагов: " + st.epicenters().size()), false);
        s.sendSuccess(() -> Component.literal(
            "Местность размечена: " + (st.isTerrainInitialized() ? "да" : "нет")), false);
        return 1;
    }

    private static int ночь(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);
        st.advanceNight();
        SpreadEngine.NightResult r = NightHook.runNight(level, st, false);
        st.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Ночь %d: заражено %d, выросло %d, зажило %d",
                st.night(), r.newlyInfected(), r.grown(), r.scarsHealed())), true);
        return 1;
    }

    private static int прогнать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int ночей = IntegerArgumentType.getInteger(ctx, "nights");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        long t0 = System.nanoTime();
        int всего = 0;
        for (int i = 0; i < ночей; i++) {
            st.advanceNight();
            всего += NightHook.runNight(level, st, false).newlyInfected();
        }
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        final int итог = всего;
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Прогнано %d ночей за %d мс. Заражено %d чанков, теперь %.1f%%",
                ночей, мс, итог, st.grid().infectedFraction() * 100f)), true);
        return 1;
    }

    private static int установитьФазу(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int фаза = IntegerArgumentType.getInteger(ctx, "phase");
        PlagueState st = PlagueState.get(мир(ctx.getSource()));
        int[] перваяНочьФазы = { 1, 6, 13, 21, 31 };
        st.setNight(перваяНочьФазы[фаза]);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Фаза " + фаза + ", ночь " + st.night()), true);
        return 1;
    }

    private static int пауза(CommandSourceStack src, boolean значение) {
        PlagueState st = PlagueState.get(src.getServer().getLevel(Level.OVERWORLD));
        st.setPaused(значение);
        src.sendSuccess(() -> Component.literal(
            значение ? "Чума поставлена на паузу" : "Чума продолжается"), true);
        return 1;
    }

    private static int сгенерировать(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        float доля = FloatArgumentType.getFloat(ctx, "percent");
        ServerLevel level = мир(ctx.getSource());
        PlagueState st = PlagueState.get(level);

        long[] очаги = st.epicentersArray();
        if (очаги.length == 0) {
            ctx.getSource().sendFailure(Component.literal(
                "Сначала посадите хотя бы один очаг: /plague seed <x> <z>"));
            return 0;
        }

        RandomGenerator rng = RandomGeneratorFactory.of("Xoshiro256PlusPlus")
            .create(level.getSeed());

        long t0 = System.nanoTime();
        StartGenerator.GenerationResult r =
            StartGenerator.generate(st.grid(), доля, очаги, rng);
        st.setDirty();
        long мс = (System.nanoTime() - t0) / 1_000_000;

        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Сгенерировано за %d мс: %.1f%% мира, %d ночей симуляции, очагов %d",
                мс, r.achievedFraction() * 100f, r.nightsSimulated(), r.epicenterCount())), true);
        return 1;
    }

    private static int очаг(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                            boolean добавить) {
        var pos = ColumnPosArgument.getColumnPos(ctx, "pos");
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        PlagueState st = PlagueState.get(мир(ctx.getSource()));

        if (!st.grid().contains(cx, cz)) {
            ctx.getSource().sendFailure(Component.literal(
                "Чанк " + cx + ", " + cz + " вне сетки мира"));
            return 0;
        }

        long packed = StartGenerator.packChunk(cx, cz);
        if (добавить) {
            st.addEpicenter(packed);
            st.grid().setLevel(cx, cz, 3);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг посажен в чанке " + cx + ", " + cz), true);
        } else {
            st.removeEpicenter(packed);
            st.grid().setLevel(cx, cz, 0);
            st.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Очаг убран из чанка " + cx + ", " + cz), true);
        }
        return 1;
    }
}
```

- [ ] **Step 2: Убедиться, что проект компилируется**

```bash
cd plaguecore && ./gradlew compileJava
```

Ожидание: BUILD SUCCESSFUL.

- [ ] **Step 3: Проверить команды на живом сервере**

```bash
cd plaguecore && ./gradlew runServer
```

В консоли сервера выполнить по порядку и сверить вывод:

```
plague info
```
Ожидание: ночь 0, фаза 0, заражено 0.0% (0 из 3969), очагов 0,
местность размечена: да.

```
plague seed 0 0
```
Ожидание: «Очаг посажен в чанке 0, 0».

```
plague generate 0.1
```
Ожидание: «Сгенерировано за NN мс: 10.x% мира, N ночей симуляции».
Время должно быть в пределах десятков миллисекунд.

```
plague info
```
Ожидание: заражено примерно 10%, распределение по уровням 1–3.

```
plague fastforward 30
```
Ожидание: «Прогнано 30 ночей за NN мс», заражено 75–85%.

Это прямая проверка кривой из спека 6.2 на реальных данных. Если доля
вышла заметно за пределы 75–85% — расхождение между расчётом и
реальностью, разбираться до перехода к следующему плану.

```
plague pause
plague night
```
Ожидание: команда `night` работает и на паузе (пауза останавливает
только автоматический тик).

Остановить сервер командой `stop`, затем запустить снова и выполнить
`plague info` — состояние должно сохраниться.

- [ ] **Step 4: Прогнать полный набор тестов**

```bash
cd plaguecore && ./gradlew build
```

Ожидание: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 5: Коммит**

```bash
git add plaguecore/src
git commit -m "Команды /plague: info, night, fastforward, setphase, pause, generate, seed

Админский инструментарий по спеку 12.1 в части, не требующей
материализации блоков. Команда fastforward 30 — прямая проверка
кривой распространения из спека 6.2 на живых данных.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Готовность после этого плана

Работает:

- Мод собирается и грузится на NeoForge 1.21.1
- Сетка 63 × 63 чанка размечена по биомам и хранится в сохранении мира
- Каждую ночь считается распространение по спеку, сон удваивает бюджет
- Шрамы тают, хотя пока их некому ставить
- Стартовое заражение генерируется командой за десятки миллисекунд
- `/plague info` показывает состояние, `/plague fastforward` проверяет кривую
- 45+ юнит-тестов, ни один не запускает Minecraft

Не работает и ждёт следующих планов:

- Заражение не видно в мире — блоки не меняются (материализация)
- Игроки не заражаются
- Очистителей и курильниц нет
- Победы и поражения нет

Следующий план — **материализация и подземелье**: очередь, семь своих
блоков, таблицы трансформаций, ленивое применение при загрузке чанка.
После него чуму станет видно глазами.
