# lmpc_classes — система классов

Пятый мод репозитория (после `plaguecore`, лаунчера, `lmpc_gmtools`,
`lmpc_shade`). Отдельный Gradle-проект, свой `modId = lmpc_classes`.
Владелец этой половины — Kuragane (как `launcher/`, `lmpc_gmtools`,
`lmpc_shade`). `plaguecore/` — друга, не трогать.

## Что это

Собственная система классов вместо Origins — подсистема 3 дорожной
карты. Дизайн (роли Клирика, Кузнеца, Фермера, Летописца, механика
путёвок, интеграция с чумой) — `docs/superpowers/specs/
2026-09-04-klassy-design.md` в корне репозитория.

## Границы и платформа

- Отдельный Gradle-проект, свой `modId`, конфликтов при
  `git pull --rebase` с `plaguecore` не создаёт.
- **Minecraft 1.21.1 / NeoForge 21.1.249 / JDK 21**, ModDevGradle
  2.0.146, Gradle 8.14.5 через wrapper, пакет `dev.denthe.classes`.
- **Мод и клиентский, и серверный** (`side = "BOTH"`), как gmtools:
  предметы и вложение нужны на обеих сторонах, команды и логика смены
  класса — на сервере.
- **Мост к `plaguecore` — только мягкий, через рефлексию**, тем же
  приёмом, что `ShadeApi`/`ShadeAccess` между `lmpc_shade` и
  `lmpc_gmtools`: жёсткой Gradle-зависимости между джарами нет, нет
  мода-донора — заглушка/не сработало, но не краш. Понадобится, когда
  дойдёт до способностей (крючок `protection()` в `PlayerInfection`
  для пассивки Клирика, `PlagueApi.cure/grantImmunity` для его отвара).

## Устройство (0.1.0)

- `LmpcClasses.java` — точка входа, регистрирует всё ниже.
- `ClassSwitch.java` — чистая функция кулдауна смены класса, без
  импортов Minecraft, проверяется обычным JUnit (`ClassSwitchTest`).
- `PlayerClassData.java` — Data Attachment на игроке: текущий класс
  (`enum Класс`: NONE/CLERIC/SMITH/FARMER/CHRONICLER) и тик последней
  смены. `copyOnDeath` — смерть класс не сбрасывает.
- `ClassTokenItem.java` + `ClassItems.java` — четыре путёвки,
  право-клик меняет класс, если не идёт кулдаун. **Крафта нет** —
  донорский материал + `cleansing_agent` из `plaguecore` ещё не
  существуют в игре. Получить путёвку пока можно из творческого
  режима или команды.
- `ClassCommands.java` — `/lmpcclasses class <игрок> [класс]`, обход
  кулдауна и крафта для проверки на живом сервере, право 2.
- `ClassesConfig.java` — `config/lmpc_classes-common.toml`,
  `classSwitchCooldownMinutes` (умолчание 30).
- `ClassCreativeTab.java` — своя вкладка в творческом инвентаре.

**Текстур у путёвок нет** — модели ссылаются на
`lmpc_classes:item/class_token_<класс>`, картинки не заведены,
в игре предметы будут розово-чёрными до textures_src.

## Рабочий процесс (требования владельца, как у gmtools/shade)

- **Версия за каждое весомое изменение:** поднять `mod_version` в
  `gradle.properties` (`0.МИНОР.0` фичи, `0.МИНОР.ПАТЧ` фиксы).
- **Jar нужен и в `mods/` сервера**, версии клиента и сервера должны
  совпадать. `mods/*.jar` в `.gitignore`.
- Проверка: `./gradlew runServer` — поднять dev-сервер, убедиться, что
  регистрация проходит без ошибок, затем убить java-процесс. Живьём
  в игре механику проверяет владелец.
- Сборочное окружение: `GRADLE_USER_HOME=D:\dev-cache\gradle`, там в
  `gradle.properties` — `org.gradle.java.home=D:/JDK21`.
- Git — полная автономия.

## Что дальше

- Способности классов: пассивка Клирика (Curios-слот → `protection()`
  в `PlayerInfection`), его улучшенный отвар (`PlagueApi.cure/
  grantImmunity`), тиры Очистителя для Кузнеца, грядка `plague_bloom`
  и сам реагент `cleansing_agent` для Фермера, точный вывод Jade для
  Летописца. Все мосты к `plaguecore` — рефлексия, не Gradle-зависимость.
- Рецепт путёвок, когда появится `cleansing_agent`.
- Текстуры путёвок (владелец, `textures_src/`).

Связано: `docs/superpowers/specs/2026-09-04-klassy-design.md`,
lmpc-gmtools-sostoyanie, lmpc-shade-sostoyanie (память Kuragane).
