# Краш при первой ночи: JRE без модуля jdk.random

**Дата:** 2026-09-04
**Относится к:** `plaguecore` (ядро) и лаунчер
**Статус:** лаунчер починен (коммит `5129f64`); в `plaguecore` — на решение владельца

## Симптом

В собранном лаунчере LMPC игру крашит на **первой ночи**. В лаунчере
Modrinth (та же сборка, те же 101 мод, тот же конфиг) — не крашит.

```
java.lang.IllegalArgumentException: No implementation of the random
number generator algorithm "Xoshiro256PlusPlus" is available
	at dev.denthe.plaguecore.mc.NightHook.генератор(NightHook.java:111)
	at dev.denthe.plaguecore.mc.NightHook.runNight(NightHook.java:89)
```

`Description: Exception in server tick loop` — падает встроенный сервер.

## Причина

`plaguecore` на ночном тике и в `/plague`-командах создаёт ГСЧ через
`RandomGeneratorFactory.of("Xoshiro256PlusPlus")` (`NightHook.java:113`,
`PlagueCommands.java:347`, а также тесты). Этот класс живёт в модуле
**`jdk.random`**, который есть в полном JDK, но которого **нет
в минимальном JRE от Adoptium** (он собран `jlink`'ом без него).

Наш лаунчер качал именно Adoptium **JRE** (`image_type=jre`). Modrinth
ставит полный JDK — поэтому там работало.

## Что сделано (лаунчер, наша половина)

`launcher/src/main/java.js`: `image_type=jre` → `jdk`. JDK тяжелее
(~200 МБ архив против ~45), но это единственная сборка Adoptium со всеми
стандартными модулями. Проверено: скачанный JDK содержит `jdk.random`,
`Xoshiro256PlusPlus` создаётся. Игроки получат новый рантайм при
следующем запуске лаунчера (папка `runtime/` пересоздаётся).

## Что на решение владельца (plaguecore)

Лаунчер теперь даёт полноценный JDK, так что краша больше не будет. Но
зависимость от `jdk.random` хрупкая — любой другой минимальный рантайм
(системная Java игрока, сервер на голом JRE) её не потянет. Варианты:

- оставить как есть (лаунчер гарантирует JDK) — самое ленивое;
- заменить на майнкрафтовский `XoroshiroRandomSource`
  (`net.minecraft.world.level.levelgen`) — есть всегда, но другая
  последовательность, тесты ядра надо переснять;
- свой маленький Xoshiro256++ на ~20 строк — детерминизм сохраняется,
  контрольные значения тестов не меняются.

## Чёрное небо днём — причина найдена (2026-09-04)

`lmpc_shade` `overcast` убирает небесный купол (`SkyType.NONE`). В
режиме графики **Fabulous!** (`graphicsMode:2`) дырку закрывает цвет
тумана — небо серое. В **Fancy** (`graphicsMode:1`, дефолт свежего
инстанса) дырку никто не заливает — небо чёрное. У владельца в Modrinth
стоял Fabulous, в инстансе нашего лаунчера — Fancy по умолчанию.
Подтверждено: смена `graphicsMode` на 2 небо чинит.

Фикс — на решение владельца (`lmpc_shade` — его половина):
- мод сам заливает небо цветом тумана в Fancy (фуллскрин-квад на
  `RenderLevelStageEvent.AFTER_SKY`), либо
- лаунчер кладёт дефолтный `options.txt` с `graphicsMode:2` при первом
  запуске (Fabulous имеет свои минусы — навязывать восьмерым спорно).
