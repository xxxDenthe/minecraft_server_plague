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
  мода-донора — заглушка/не сработало, но не краш. Работает в обе
  стороны (0.3.0): `PlagueBridge.java` здесь читает и правит
  заражённость игрока (`PlayerPlagueData`/`PlayerInfection.задать`
  в `plaguecore`), `ClassesApi.java` здесь — то, что `plaguecore`
  дёргает обратно (`ClassBridge.java` там) ради защиты от кулона.
  Правка `PlayerInfection.защита()` в `plaguecore` сделана с явного
  разрешения владельца, заметка — `docs/superpowers/notes/
  2026-09-04-most-classes-plaguecore.md`.
- **Curios — тоже мягкая зависимость.** `compileOnly` на джарник из
  `../mods/`, `optional` в `neoforge.mods.toml`. Кулон Клирика без
  Curios просто не появится в игре (регистрация в `FMLCommonSetupEvent`
  ссылается на Curios напрямую — без него сам класс `ClassItems` не
  загрузится, но это принятый риск: пак ставит Curios всегда, как
  `plaguecore` уже мирится с тем же для Jade).

## Устройство (0.3.0)

**Клирик — первый класс с реальными способностями.**

- `ClericsPendantItem.java` — кулон, Curios-предмет (слот `necklace`,
  тег `data/curios/tags/item/necklace.json`), сам ничего не делает —
  `ICurioItem` без единого переопределения. Носить может кто угодно,
  защита реальная только у Клирика (`ClassesApi.protectionBonus`,
  раздел ниже). Крафт: золотой слиток + нить + `plaguecore:spore_sac`.
- `ClericsBrewItem.java` — улучшенный отвар. В отличие от обычного
  `plaguecore:plague_brew` работает на любой стадии, но гейт —
  **на использовании, не на крафте**: рецепт (`plague_brew` +
  `spore_sac` + золотая морковка) доступен всем, у не-Клирика бутылка
  просто пропадает без эффекта. Свой кулдаун на игрока
  (`PlayerClassData.отварГотовТик`, `clericBrewCooldownMinutes`).
- `PlagueBridge.java` — мост К `plaguecore`: `cure(amount)` дёргает
  `PlayerInfection.задать`, `grantImmunity(ticks)` пишет
  `PlayerPlagueData.иммунитетДо` напрямую (сеттера для него в
  `plaguecore` нет, только публичное поле).
- `ClassesApi.java` — мост ОТ `plaguecore`: `protectionBonus(Player)`,
  которую `plaguecore` зовёт рефлексией из `ClassBridge`. Полная
  защита (`clericPendantProtection`, 0.15 по умолчанию) — Клирику
  с кулоном, половина (`clericPendantOtherClassFraction`) — всем
  прочим классам с тем же кулоном, ноль — без кулона.

## Устройство (0.2.0)

- `LmpcClasses.java` — точка входа, регистрирует всё ниже.
- `ClassSwitch.java` — чистая функция кулдауна смены класса, без
  импортов Minecraft, проверяется обычным JUnit (`ClassSwitchTest`).
- `PlayerClassData.java` — Data Attachment на игроке: текущий класс
  (`enum Класс`: NONE/CLERIC/SMITH/FARMER/CHRONICLER), тик последней
  смены и `мастерство` (раздел 2.1 спека). `сменитьКласс(...)` — смена
  со срезом мастерства старого класса до `masteryKeepFraction`, а не
  обнулением. `copyOnDeath` — смерть класс и мастерство не сбрасывает.
- `ClassAltarBlock.java` + `ClassBlocks.java` — единственный блок
  мода, «Алтарь призвания». Правый клик без предмета в руке
  (`useWithoutItem`) открывает клиентский экран, никакого меню-
  контейнера нет — путёвок-предметов больше не существует (крафт
  под них требовал `cleansing_agent` из `plaguecore`, которого ещё
  нет; блок эту зависимость снял целиком). Дроп — сам блок
  (`data/lmpc_classes/loot_table/blocks/class_altar.json`), в
  творческой вкладке «Функциональные блоки».
- `client/ClassAltarScreen.java` — пять кнопок (без класса + четыре
  класса), каждая шлёт `/lmpcclasses choose <класс>`
  (`Minecraft.getConnection().sendCommand`) тем же приёмом, что панель
  `lmpc_gmtools`. Экран ничего не решает — сервер сам проверяет
  кулдаун и режет мастерство.
- `ClassCommands.java` — `/lmpcclasses choose <класс>` (себе, право 0,
  настоящий путь смены) и `/lmpcclasses class <игрок> [класс]`
  (админский обход кулдауна и среза мастерства, право 2, для проверки
  на живом сервере).
- `ClassesConfig.java` — `config/lmpc_classes-common.toml`,
  `classSwitchCooldownMinutes` (умолчание 30), `masteryKeepFraction`
  (умолчание 0.3).

**Текстуры блока нет** — модель ссылается на ванильный
`minecraft:block/chiseled_stone_bricks` как временную заглушку,
не на свою картинку. Заменить, когда появится своя, в
`models/block/class_altar.json`.

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

- Способности остальных трёх классов: тиры Очистителя для Кузнеца,
  грядка `plague_bloom` и сам реагент `cleansing_agent` для Фермера
  (нужен и Клирику — «обработка» в цепочке реагента, раздел 8 спека,
  пока не сделана), точный вывод Jade для Летописца.
- Своя текстура алтаря, кулона и отвара (владелец, `textures_src/`)
  вместо заглушек на ванильных текстурах.
- Растёт ли мастерство от реальных действий — поле заведено и режется
  при смене, но ничего его не пополняет: у Клирика логичный триггер —
  успешное исцеление отваром (стадия 3–4), ещё не подключено.
- Мастерство партии для построек (раздел 2.1 спека) — концепция есть,
  реализации нет: пока строить нечему, ни один экслюзив других
  классов не готов.

Связано: `docs/superpowers/specs/2026-09-04-klassy-design.md`,
lmpc-gmtools-sostoyanie, lmpc-shade-sostoyanie (память Kuragane).
