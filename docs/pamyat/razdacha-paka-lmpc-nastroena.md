---
name: razdacha-paka-lmpc-nastroena
description: "Раздача модпака «Чума» настроена и работает: где секреты, чем публиковать, что нельзя ломать"
metadata:
  type: project
---

В `C:\Users\penis\Desktop\minecraft\launcher` раздача пака доведена
до рабочего состояния 2026-09-08. Пак лежит в релизе `pack`
репозитория `xxxDenthe/minecraft_server_plague`, `packVersion` 1.

Секреты на диске, оба вне git и оба **обязательны**:

- `launcher/publish.json` — токен на **запись**, им выкладывают пак
- `launcher/src/main/distribution.json` — токен на **чтение**,
  уезжает внутри `.exe` к игрокам

Обновить пак: положить файлы в `launcher/pack-build/`, затем
`npm run publish`. Установщик при этом пересобирать не надо — внутри
`.exe` только адрес репозитория и токен, список файлов читается
из сети при каждом запуске. Пересборка нужна только при правке
`launcher/src/`.

**Why:** без обоих файлов лаунчер собирается «пустым» и молча ставит
чистый NeoForge без модов, а `npm run publish` падает на отсутствии
токена. Ни один из них не восстанавливается из репозитория.

**How to apply:** перед любой работой с раздачей проверить, что оба
файла на месте. Список папок в `publish.json`
(`mods,config,kubejs,resourcepacks,CustomSkinLoader`) менять только
осознанно: папка, которая есть в списке, но которой нет в паке,
**стирается у игрока** — так устроен `planSync`.

Подробности, история и грабли — в репозитории,
`docs/superpowers/notes/2026-09-08-pervaya-razdacha-arhivami.md`.

Связано: [[parallelnaya-sessiya-v-plaguecore]], [[tokeny-lmpc-otozvat]],
[[server-vladelca-d-lmpc-server]]
