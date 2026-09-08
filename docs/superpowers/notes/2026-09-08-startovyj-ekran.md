# Стартовый экран через Welcome Screen — сделано, живьём не проверено

**Дата:** 2026-09-08
**Статус:** файлы в репозитории и проверены парсером,
в игре НЕ открывали ни разу

## Что сделано

Экран при первом входе в мир — мод `welcomescreen` (ElocinDev).
Сам он ничего не рисует: заводит два пустых кастомных GUI
(`welcomescreen_welcome`, `welcomescreen_update`) и показывает первый
один раз. Всё содержимое — макет FancyMenu.

Добавлено пять клиентских джарников в `mods/` (на сервер не класть):
`welcomescreen-neoforge-1.0.0-1.21.1.jar`,
`fancymenu_neoforge_3.9.12_MC_1.21.1.jar`,
`Necronomicon-NeoForge-1.6.0+1.21.jar`,
`melody_neoforge_1.0.10_MC_1.21.jar`,
`konkrete_neoforge_1.9.9_MC_1.21.jar`.

Сам экран:
`launcher/pack-config/config/fancymenu/customization/welcome_screen.txt`
— производная. Править надо генератор
`launcher/tools/make-welcome-screen.py` и перезапускать его.

Три колонки, как на образце Prominence II: лор слева, механики
с иконками предметов посередине, памятка справа; сверху заголовок,
снизу кнопка «Войти в деревню» (действие `closegui`).

## Правила содержания, которые задал владелец

- Лор **без разгадки**: откуда пошла зараза — это то, что игроки
  собирают по бумагам всю сессию. На экране только место, время
  и зачем позвали.
- Про `lmpc_shade` не писать вообще.
- Текст для человека, который наших модов не знает.
- **Коротко.** 2026-09-08 текст урезан примерно вдвое: подробности про
  чуму (плесень по полям, колодцы, «лечить уже некого») убраны, в лоре
  осталось три строки, в механиках — по одной строке объяснения.
  Длинный текст на стартовом экране не читают.

## Формат макета — снят с байткода FancyMenu 3.9.12

Публичного описания формата нет, поэтому ключи вытащены `javap`:

- файл: `type = fancymenu_layout`, дальше `layout-meta { }` и `element { }`;
- `identifier` в мете — идентификатор экрана (`welcomescreen_welcome`);
- перенос строки в значении — `%n%`;
- у `text_v2` цвета и кегля в свойствах нет: `%#RRGGBB%…%#%` цвет,
  `^^^…` центрирование, `# ` крупный заголовок, `**…**` жирный;
- кнопка: `button_element_executable_block_identifier` плюс пара
  `[executable_block:<id>][type:generic] = [executables:<act>;]`
  и `[executable_action_instance:<act>][action_type:closegui] =`.

## Проверено без игры

- Файл разбирается **настоящим парсером FancyMenu** — 26 секций,
  `identifier = welcomescreen_welcome`, кириллица в UTF-8 доходит.
  Повторить (JDK 21, jar-ы из `mods/`, log4j из кэша Gradle):

  ```bash
  cat > /tmp/Check.java <<'J'
  import de.keksuccino.fancymenu.util.properties.*;
  import java.nio.file.*;
  public class Check { public static void main(String[] a) throws Exception {
      PropertyContainerSet s = PropertiesParser.deserializeSetFromFancyString(
          Files.readString(Path.of(a[0])));
      System.out.println(s.getType() + " / " + s.getContainers().size() + " секций / "
          + s.getFirstContainerOfType("layout-meta").getValue("identifier")); } }
  J
  L4J=$(ls ~/.gradle/caches/modules-2/files-2.1/org.apache.logging.log4j/log4j-api/*/*/*.jar | head -1)
  "/c/Program Files/Java/jdk-21/bin/java.exe" -Dfile.encoding=UTF-8       -cp "mods/fancymenu_neoforge_3.9.12_MC_1.21.1.jar;mods/konkrete_neoforge_1.9.9_MC_1.21.jar;$(cygpath -w $L4J)"       /tmp/Check.java launcher/pack-config/config/fancymenu/customization/welcome_screen.txt
  ```

- Ключи предметов `lmpc_classes:clerics_brew`, `class_codex`,
  `andesite_purifier` сверены с регистрацией в `lmpc_classes/` — есть все три.
- Раскладка по высоте после сокращения текста: лор ~90 точек,
  памятка ~120, механики кончаются на 212 при потолке 222.
  При масштабе интерфейса 4 (480 × 270) запас есть.

## Что осталось проверить в игре

Ни один пункт не проверен: клиента Minecraft на машине владельца нет,
профиля Modrinth `LMPCCHUMA` тоже нет — `sync-profile.py` разложить пак
некуда.

1. Открывается ли экран вообще: `/openguiscreen welcomescreen_welcome`.
2. Крупный заголовок: строка `# ^^^ЧУМА`. В байткоде FancyMenu 3.9.12
   заголовок и центрирование разбираются в одном проходе по строке,
   так что вместе они должны работать — но проверено это только чтением.
   Увидите литеральную решётку — уберите `# `, оставьте `^^^**ЧУМА**`,
   это одна строка в генераторе.
3. Рисуются ли иконки предметов (мод `lmpc_classes` обязан быть загружен).
4. Не светит ли мир сквозь фон: свой тёмный прямоугольник лежит поверх
   мира с прозрачностью 0.94.

Показать экран заново — удалить `welcomescreen_cache.json` из папки игры.

## Что осталось сделать руками

Раздать игрокам: новый конфиг и пять клиентских джарников в паке ещё
не выложены. Токена раздачи в дереве нет, выкладывает владелец:

```
cp -r launcher/pack-config/* pack-build/
node launcher/tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague      --tag pack --token <ghp_...> --managed mods,CustomSkinLoader
```
