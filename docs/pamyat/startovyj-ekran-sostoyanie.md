---
name: startovyj-ekran-sostoyanie
description: "«Чума»: стартовый экран переделан под новые длинные тексты и проверен в дев-клиенте; правится генератором, проверяется одной командой"
metadata:
  type: project
---

Стартовый экран (мод `welcomescreen` + макет FancyMenu) на 2026-09-08
переделан под тексты владельца, которые стали втрое длиннее (1347 знаков
против прежних ~490), и проверен в дев-клиенте: три колонки влезают
целиком, ничего не режет, кнопка ни на что не наезжает.

**Как править.** Текст и раскладка живут в
`launcher/tools/make-welcome-screen.py`. Файл
`launcher/pack-config/.../welcome_screen.txt` — производная, руками
не трогать. Кегль тела руками тоже не задаётся: генератор берёт самый
крупный из ряда 0.85 … 0.6, при котором влезают все три колонки, и падает
с `assert`, если не влезает даже мелкий. На нынешних текстах выходит 0.65.

**Как проверить.** Одна команда, около полуминуты:

```
powershell -File launcher/tools/dev-welcome.ps1
```

Скрипт перегенерирует макет, разложит обвязку в `plaguecore/run/client`,
поднимет дев-клиент, дождётся экрана и снимет окно в `launcher/tools/снимки`.
Вход в мир — свойство `-PquickPlay`, заведённое в `plaguecore/build.gradle`,
так что временных правок сборки и отката больше не нужно.

**Грабли, стоившие времени** (подробно — в спеке
`docs/superpowers/specs/2026-09-08-startovyj-ekran-dlinnye-teksty-design.md`):
BOM от `Set-Content -Encoding utf8` молча ломает разбор файлов FancyMenu;
макет с `identifier = title_screen` грузится, но не применяется;
свой клиент надо узнавать по командной строке процесса, а не по названию
окна — иначе гасишь клиент владельца.

**Осталось:** выложить пак игрокам. `launcher/pack-build/` собрана,
токен у владельца.

```
cd launcher
node tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague --tag pack
```

Связано: [[proverka-gui-cherez-dev-klient]], [[parallelnaya-sessiya-v-plaguecore]]
