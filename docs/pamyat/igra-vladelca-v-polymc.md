---
name: igra-vladelca-v-polymc
description: "«Чума»: настоящая игра владельца — инстанс PolyMC, а не папка лаунчера LMPC и не ванильный .minecraft"
metadata:
  type: project
---

Владелец играет отсюда:

```
C:\Users\penis\AppData\Roaming\PolyMC\instances\1.21.1\.minecraft
```

**Why:** 2026-09-08 я дважды искал игру не там. `%APPDATA%\.minecraft`
— ванильный лаунчер без модов (версии 26.x). `%APPDATA%\LMPC\instance`
— папка нашего лаунчера, она существует и заполнена, но владелец в ней
не играет. Профиля Modrinth `LMPCCHUMA`, про который написано в
`launcher/pack-config/README.md`, на машине нет вообще — значит
`sync-profile.py` там не поможет.

**How to apply:** класть клиентские конфиги для проверки в инстанс
PolyMC. Кэш стартового экрана лежит в корне инстанса:
`welcomescreen_cache.json`, поле `shownWelcomeScreen`. Игра
перезаписывает его в `true` при выходе — сбрасывать только на закрытой
игре, иначе откатится.

Связано: [[proverka-gui-cherez-dev-klient]]
