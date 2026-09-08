---
name: startovyj-ekran-sostoyanie
description: "«Чума»: стартовый экран проверен в игре и закоммичен, осталось выложить в пак — ждём коммита напарника в launcher/"
metadata:
  type: project
---

Стартовый экран (мод `welcomescreen` + макет FancyMenu) на 2026-09-08
проверен живьём и закоммичен (`08aa2cf`). Текст сокращён, каретки
центрирования, кегль заголовка и высоты строк починены.

**Осталось:** выложить пак игрокам. `launcher/pack-build/` уже собрана
и содержит пять клиентских джарников и свежий макет; токен лежит
в `launcher/publish.json`.

```
cd launcher
node tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague --dry-run
node tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague --tag pack
```

**Why не выложено:** напарник в тот же вечер правил
`launcher/src/main/archive.js` и `launch.js` — именно тот код, который
пакует архивы, и правки были незакоммичены. Заливать пак недоделанным
упаковщиком рискованно. Перед выкладкой убедиться, что `git status`
в `launcher/` чист.

Текст правится в `launcher/tools/make-welcome-screen.py`, потом
`python launcher/tools/make-welcome-screen.py`. Файл
`launcher/pack-config/.../welcome_screen.txt` — производная,
руками не трогать.

Известное косметическое: на 1920x1080 содержимое занимает верхние две
пятых, низ пустой — экран считался под 480x270.

Связано: [[proverka-gui-cherez-dev-klient]], [[parallelnaya-sessiya-v-plaguecore]]
