---
name: proverka-gui-cherez-dev-klient
description: "«Чума»: игровой экран проверять своим дев-клиентом с --quickPlaySingleplayer, а не тыкая в запущенную игру владельца"
metadata:
  type: feedback
---

Проверять GUI мода надо **своим дев-клиентом**, который сам заходит
в мир, а не синтетическими нажатиями в игру владельца.

**Why:** 2026-09-08 я пытался открыть стартовый экран в чужой игре
через `SendKeys` и `keybd_event` — до Minecraft не доходит ни одно
нажатие, ни в PolyMC, ни в дев-клиенте (мышь тоже). Владелец на это
резко ответил: «раньше сессия могла запустить дев-клиент через shell
и сама всё проверить, не трогая мой компьютер». Он прав: аргумент
запуска доходит там, где не доходит клавиатура.

**How to apply:** в `plaguecore/build.gradle`, блок `runs { client }`,
временно дописать:

```groovy
programArgument '--quickPlaySingleplayer'
programArgument 'New World'
```

Разложить нужные джарники в `plaguecore/run/client/mods/`, конфиг —
в `run/client/config/`, `./gradlew runClient` в фоне, ждать в логе
нужную строку, снимать экран через PowerShell (`CopyFromScreen` после
`SetForegroundWindow`). Панель FancyMenu сверху убирается в
`run/client/config/fancymenu/options.txt`:
`B:show_customization_overlay = 'false';`. После проверки вернуть
`build.gradle` и убрать разложенное. Цикл — около двух минут.

Подробности в репозитории:
`docs/superpowers/notes/2026-09-08-startovyj-ekran.md`.

Связано: [[igra-vladelca-v-polymc]], [[startovyj-ekran-sostoyanie]]
