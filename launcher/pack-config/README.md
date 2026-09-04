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
