# Состав модпака

Minecraft 1.21.1 · NeoForge 21.1.249 · Create 6.0.10

Джарники не хранятся в репозитории (см. `.gitignore`). Этот файл —
фиксация точных версий: по нему собирается идентичный набор у всех
игроков и на сервере.

**Список сверен с папкой `mods/` 2026-09-06 (вечер).** Он собран из
самой папки, а не правился вручную: всё, что ниже, реально лежит
в `mods/`, и наоборот. Игровой профиль владельца
(`ModrinthApp/profiles/LMPCCHUMA/mods`) в тот же момент совпадал
с папкой файл в файл.

**2026-09-08:** добавлены пять клиентских джарников ради стартового
экрана — `welcomescreen` и его обязательная цепочка зависимостей
(`fancymenu`, `necronomicon`, `melody`, `konkrete`). На сервер их
класть не надо, это клиентская сторона; сам экран лежит в раздаче,
`launcher/pack-config/config/fancymenu/customization/welcome_screen.txt`.
Заметка `docs/superpowers/notes/2026-09-08-startovyj-ekran.md`.

Всего: 108 сторонних модов + 4 наших. Один джарник отключён
(`.disabled`), он в списке не значится.

## Сторонние моды

```
AdvancementPlaques-1.21.1-neoforge-1.6.8.jar
AmbientSounds_NEOFORGE_v6.3.8_mc1.21.1.jar
appleskin-neoforge-mc1.21-3.0.9.jar
architectury-13.0.11-neoforge.jar
atmospherics-2.6.5-mc-1.21.1.jar
Axiom-6.0.5-for-MC1.21.1.jar
baguettelib-1.21.1-NeoForge-2.0.6.jar
better-advanced-tooltips-2101.1.0-build.5.jar
bettercombat-neoforge-2.4.0+1.21.1.jar
c2me-neoforge-mc1.21.1-0.4.0-alpha.0.120.jar
carryon-neoforge-1.21.1-2.2.6.13.jar
Chunky-NeoForge-1.4.23.jar
clickthrough-plus-neoforge-3.5.0+1.21.1.jar
cloth-config-15.0.140-neoforge.jar
ColdSweat-2.4.2.jar
connector-2.0.0-beta.17+1.21.1-full.jar
Controlling-neoforge-1.21.1-19.0.5.jar
copycats-3.0.8+mc.1.21.1-neoforge.jar
coroutil-neoforge-1.21.0-1.3.8.jar
corpse-neoforge-1.21.1-1.1.13.jar
corpsecurioscompat-1.21.1-NeoForge-4.0.1.jar
create-1.21.1-6.0.10.jar
create-collision-fix-1.0.0.jar   # заплатка к Create 6.0.10, снять на 6.0.11, см. заметку 2026-09-06-krashi-create-i-veil
create_connected-1.3.3-mc1.21.1.jar
create_power_loader-2.0.5-mc1.21.1.jar
createaddition-1.7.0.jar
createdeco-2.1.3.jar
CreativeCore_NEOFORGE_v2.13.44_mc1.21.1.jar
cristellib-neoforge-1.21.1-3.1.7.jar
curios-neoforge-9.5.1+1.21.1.jar
CustomSkinLoader_Universal-15.0.1.jar
dungeons-and-taverns-v4.4.4.jar
entity_model_features-3.3.3-1.21-neoforge.jar
entity_texture_features-7.2.1-1.21-neoforge.jar
exposure-neoforge-1.21.1-1.9.18.jar
extrasounds-1.5.6+1.21.1-neoforge.jar
FallingTree-1.21.1-1.21.1.11.jar
fancymenu_neoforge_3.9.12_MC_1.21.1.jar
FarmersDelight-1.21.1-1.3.4.jar
ferritecore-7.0.3-neoforge.jar
forgified-fabric-api-0.116.15+2.3.5+1.21.1.jar
ftb-library-neoforge-2101.1.35.jar
ftb-quests-neoforge-2101.1.34.jar
ftb-teams-neoforge-2101.1.11.jar
fzzy_config-0.7.6+1.21+neoforge.jar
geckolib-neoforge-1.21.1-4.9.2.jar
handcrafted-neoforge-1.21.1-4.0.3.jar
Iceberg-1.21.1-neoforge-1.3.2.jar
ImmersiveUI-NEOFORGE-0.3.3+1.21.1.jar
InvMove-0.9.3+1.21.1-NeoForge.jar
InvMoveCompats-0.5.0+1.21.8-NeoForge.jar
Jade-1.21.1-NeoForge-15.10.6.jar
JadeAddons-1.21.1-NeoForge-6.1.1.jar
jei-1.21.1-neoforge-19.51.0.418.jar
konkrete_neoforge_1.9.9_MC_1.21.jar
kotlinforforge-5.12.0-all.jar
krypton_fnp-neoforge-1.21.1-0.2.28.1-1.21.1.jar
kubejs-create-neoforge-2101.3.1-build.18.jar
kubejs-neoforge-2101.7.2-build.374.jar
kubejsadditions-neoforge-1.21.1-6.0.0.jar
lambdynamiclights-4.8.11+1.21.1.jar
LegendaryTooltips-1.21.1-neoforge-1.5.5.jar
lithium-neoforge-0.15.4+mc1.21.1.jar
lithostitched-1.8.0+beta4-neoforge-21.1.jar
mapwright-neoforge-1.21.1-1.0.6.jar
melody_neoforge_1.0.10_MC_1.21.jar
modernfix-neoforge-5.27.24+mc1.21.1.jar
moonlight-1.21.1-3.6.0-neoforge.jar
MouseTweaks-neoforge-mc1.21-2.26.1.jar
mru-1.0.19+LTS+1.21.1+neoforge.jar
Necronomicon-NeoForge-1.6.0+1.21.jar
OctoLib-NEOFORGE-0.6.2+1.21.jar
ok_zoomer-neo-10.0.0-beta.13.jar
particlerain-4.0.0-beta.11+1.21.1-neoforge.jar
particular-1.21.1-NeoForge-1.5.7.jar
PickUpNotifier-v21.1.1-1.21.1-NeoForge.jar
player-animation-lib-forge-2.0.4+1.21.1.jar
Prism-1.21.1-neoforge-1.0.11.jar
ProbeJS-8.0.3.jar
punchy-2.7d-neoforge-1.21.1.jar
PuzzlesLib-v21.1.56-mc1.21.1-NeoForge.jar
reeses-sodium-options-neoforge-2.2.3+mc1.21.1.jar
resourcefullib-neoforge-1.21-3.0.12.jar
rhino-2101.2.8-build.91.jar
sable-neoforge-1.21.1-2.0.5.jar
Searchables-neoforge-1.21.1-1.0.2.jar
sit-1.21.1-1.4.jar
skinlayers3d-neoforge-1.11.2-mc1.21.1.jar
sliceanddice-4.3.3-neoforge.jar
sodium-neoforge-0.8.13+mc1.21.1.jar
sodium-shadowy-path-blocks-neoforge-4.1.0.jar
sophisticatedbackpacks-1.21.1-3.25.78.2107.jar
sophisticatedcore-1.21.1-1.4.90.2299.jar
sound-physics-remastered-neoforge-1.21.1-1.5.1.jar
sounds-2.4.22+lts+1.21.1-neoforge.jar
spark-1.10.124-neoforge.jar
SubtleEffects-neoforge-1.21.1-1.14.3.jar
supplementaries-1.21.1-3.9.6-neoforge.jar
t_and_t-fabric-neoforge-1.13.11.jar
tectonic-3.0.26-neoforge-21.1.jar
ThirstWasTaken-1.21.1-2.1.5-nojade.jar   # пропатчен, см. заметку 2026-09-05-jade-otkachen-radi-zhazhdy
TravelersTitles-1.21.1-NeoForge-5.1.3.jar
veil-neoforge-1.21.1-4.4.1.jar   # поверх Veil 4.3.2 из sable, см. заметку 2026-09-06-krashi-create-i-veil
visuality-forge-3.0.0.jar
voicechat-neoforge-1.21.1-2.6.22.jar
welcomescreen-neoforge-1.0.0-1.21.1.jar
worldedit-mod-7.3.8.jar
yet_another_config_lib_v3-3.8.2+1.21.1-neoforge.jar
YungsApi-1.21.1-NeoForge-5.1.8.jar
zombieawareness-neoforge-1.21.0-1.13.2.jar
```

## Atmospherics и lmpc_shade

`atmospherics` рисует небо, солнце, звёзды, облака, туман и дымку;
`lmpc_shade` поверх этого делает то, чего у Atmospherics нет —
цветокор кадра, чёрную ночь через lightmap, подземный туман по глубине,
реакцию на HP и споры. Раньше они дрались: пасмурный купол
`SkyType.NONE` выключал всю машинерию Atmospherics, а два цвета тумана
спорили за один кадр.

Границу держат два конфига из раздачи (`launcher/pack-config/`):
`config/ambientfog/biome_fog.json` — серый пресет на 65 биомов,
генерируется `launcher/tools/atmospherics-preset.py`;
`config/lmpc_shade-client.toml` — `sky.overcast = false` и
`fog.fogColorStrength = 0.0`.

## Наш мод

```
plaguecore-0.2.0.jar      ядро чумы, сервер + клиент
lmpc_gmtools-0.19.0.jar   панель мастера игры
lmpc_shade-0.9.0.jar      цветокор, тьма, туман
lmpc_classes-0.19.0.jar   четыре класса, требует curios И create
```

Не качаются со стороны, собираются из исходников:

```
cd plaguecore    && ./gradlew build
cd lmpc_gmtools  && ./gradlew build
cd lmpc_shade    && ./gradlew build
cd lmpc_classes  && ./gradlew build
```

Джарники появляются в `<модуль>/build/libs/`, оттуда копируются в `mods/`.
Версии клиента и сервера обязаны совпадать у всех четырёх.

`lmpc_classes` 0.19.0 — все четыре класса играбельны: Клирик, Кузнец,
Фермер, Летописец. Жёстко требует `curios` **и `create`**, без любого из них не грузится:
с 0.15.0 очиститель — настоящая машина Create, он наследуется от
`KineticBlockEntity`. Вал подключается только снизу, нагрузка на сеть
8 у андезитового и 16 у латунного, в порту крутится настоящий вал.
Очиститель работает по площади 3 × 3 чанка (латунный — 7 × 7), заряд
возится лентой и виден сравнителем, у Кузнеца появился форсаж ключом.
Под землю добавлена курильница — ручная очистка без Create. В 0.13.0
сцены Ponder показывают настоящее водяное колесо вместо творческого
мотора, а утренний отчёт Кузнеца приходит раз за ночь и за каждую ночь,
а не только за удачную. В 0.14.0 андезитовый очиститель переделан
под облик Create: андезитовая оправа, гнездо вала на нижней грани,
серая голова с соплом — и вращение он принимает только сверху или
снизу, а не с любой стороны. Заметки `2026-09-06-ochistitel-oblast-i-skorost.md`
и `2026-09-06-kurilnica-i-forsazh.md`,
`2026-09-06-ochistitel-create-style.md`.

`plaguecore` 0.2.0 — симптомы: приступ кашля сбивает бег, стадии дают
шахтёрскую усталость и слабость, болезнь тратит голод и жажду, под
открытым небом ловятся вдохи спор. Подготовка — тряпичная повязка
в слот Curios и усиленная броня. Заметка `2026-09-06-simptomy-dnyom.md`.

`lmpc_gmtools` 0.19.0 — в разделе «Чума» появилась папка «Тело»:
кнопки одержимости (`/plague possess`, `seize`, `release`).

## Отключено намеренно

- `shine-2.0.2+1.21.1-neoforge.jar.disabled` — лежит в папке
  отключённым. Раньше отключённым числился
  `sodiumoptionsapi-neoforge-1.0.10-1.21.1.jar` (несовместим
  с Reese's Sodium Options по метаданным); сейчас его в папке нет
  вовсе.

## Удалено осознанно

| Мод | Причина |
|---|---|
| `create-aeronautics-bundled` | Альфа-физика (3 мода в одном), ранний полёт ломает дизайн перемещения |
| `swingthrough` | Тянул за собой Sinytra Connector ради мелкого QoL |
| `Terralith` | Спор рельефа решён в пользу `tectonic`, два генератора сразу держать нельзя |
| `languagereload` | Убран из папки; причина в заметках не записана |

`connector` и `forgified-fabric-api` эта таблица раньше считала
удалёнными, но в папке и в профиле они есть и работают. Строки про них
убраны: список ниже собирается из папки, и папка — источник правды.

## Пропавшие моды вернулись — блокер закрыт 2026-09-06

Список от 2026-09-04 считал пропавшими четырнадцать джарников, и среди
них был `ftb-teams`, без которого `ftb-quests` не грузится вовсе. Пак
в том виде не запустился бы.

**Сейчас в папке все четырнадцать**, включая `ftb-teams`, `ColdSweat`,
`CustomSkinLoader` и `tectonic`; конфиги на них давно лежали
в `launcher/pack-config`. Всего в `mods/` 107 джарников, обязательных
дыр по зависимостям не осталось.

Спор рельефа решён в пользу тектоника: `Terralith` из папки убран,
`tectonic` остался. Держать оба сразу по-прежнему не стоит.

Серверная папка (`E:\CLAUDE\server\mods`, 87 джарников) уже
клиентской — в ней нет клиентских модов вроде Sodium. Это нормально
и расхождением версий не считается.
