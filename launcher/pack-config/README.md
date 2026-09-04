# pack-config — файлы конфигов для модпака

Здесь лежат конфиги, которые должны раздаваться игрокам вместе с модами,
но не являются джарниками. Джарники живут в `mods/` (не версионируются),
а вот эти текстовые файлы — версионируются, потому что маленькие и
важно, чтобы у всех был один и тот же.

## Что внутри

- **`CustomSkinLoader/CustomSkinLoader.json`** — конфиг мода
  CustomSkinLoader. Ставит источником скинов ely.by (по нику), Mojang —
  запасным. Без этого файла мод тоже подтянул бы ely.by (он в списке
  по умолчанию), но порядок и `cacheExpiry` мы задаём явно.
  `buildNumber: 99999` — намеренно завышен, чтобы мод не считал конфиг
  устаревшим и не переписывал его при каждом запуске.
- **`config/subtle_effects/environment.toml`** — конфиг мода SubtleEffects
  (fzzy_config, TOML), только секция `[fireflies]`. По умолчанию у мода
  плотность светлячков 3 из 1–10 — здесь занижена до 1, чтобы они были
  редкими. Файл частичный: остальные секции (гейзеры, водопады и т.д.)
  мод дозаполнит своими дефолтами сам. Значения проверены по байткоду
  мода (`ValidatedInt(default, max, min)` из fzzy_config), вживую в
  клиенте пока не перепроверялись — если плотность 1 всё ещё заметна,
  крутить дальше здесь же.
- **`config/particular-common.toml`** — конфиг мода Particular, только
  `[enabledEffects] fireflies = false`. У Particular тоже есть свои
  светлячки, отдельные от SubtleEffects — оставили их выключенными,
  чтобы плотностью управлял один мод, а не два вразнобой.

## Как это попадает в пак

Раздатчик пака (`launcher/tools/publish-pack.js`) собирает пак из
папки `pack-build/`. Перед прогоном скопировать сюда содержимое:

```
cp -r launcher/pack-config/* pack-build/
node launcher/tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague \
     --tag pack --token <ghp_...> --managed mods,CustomSkinLoader
```

`--managed mods,CustomSkinLoader` — чтобы `CustomSkinLoader/` попала в
манифест (иначе раздаётся только `mods/`). `publish-pack.js` сам поднимет
`packVersion`, и лаунчер у игроков дольёт новый файл при следующем
запуске.
