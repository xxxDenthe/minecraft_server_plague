# Второй сервер — на машине владельца, запуск через Radmin

**Дата:** 2026-09-08
**Повод:** запускаемся, играть решили через Radmin VPN; владельцу нужен
свой сервер, который поднимается батником в cmd на его же компьютере.
**Кто:** сессия владельца

Это **не замена** серверу из `2026-09-06-server-podnyat.md` (тот стоит
у Kuragane, `E:\CLAUDE\server`, на машине владельца диска `E:` нет).
Второй, независимый.

---

## Где он

```
D:\LMPC-server\
  start.bat            запуск двойным щелчком, окно cmd
  ЧИТАЙ_МЕНЯ.txt       шпаргалка владельцу: адреса, whitelist, op
  mods\                105 джарников
  config\              из launcher/pack-config
  user_jvm_args.txt    -Xmx6G -Xms6G -XX:+UseG1GC
  server.properties
  world\               создаётся при первом запуске
```

`run.bat`/`run.sh` от установщика удалены, чтобы не путались с `start.bat`.
`start.bat` зовёт java явным путём и добавляет `nogui` и `pause`.

## Окружение владельца (отличается от машины Kuragane)

```
Java 21.0.9 в PATH: C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
диска E: нет; C: ~24 ГБ свободно, D: ~24 ГБ свободно (сервер весит 639 МБ + мир)
RAM 16 ГБ -> серверу отдано 6, остальное клиенту
Ethernet 192.168.0.10 · Radmin VPN 26.80.6.64
```

`D:\JDK21` из старой заметки здесь нет и не нужен: в PATH ровно 21-я Java.

## Состав модов проверен диффом

Сервер собран из `mods/` репозитория минус 20 клиентских джарников.
Сверено с раздаваемым архивом `LMPCCHUMA-client.rar` (список
`unrar lb`, только папка `mods`):

```
в архиве 125 · на сервере 105 · разница ровно 20 · лишнего на сервере ноль
версии совпадают файл-в-файл, включая plaguecore 0.2.0,
lmpc_classes 0.19.0, lmpc_gmtools 0.19.0, lmpc_shade 0.9.0
```

Двадцать отсутствующих (список Kuragane плюс `CustomSkinLoader`):
sodium + reeses-sodium-options + sodium-shadowy-path-blocks,
entity_model_features, entity_texture_features, lambdynamiclights,
particlerain, atmospherics, ok_zoomer, Controlling, ImmersiveUI,
InvMove (+Compats), MouseTweaks, better-advanced-tooltips,
clickthrough-plus, punchy, sounds, extrasounds, CustomSkinLoader.
Ни один не регистрирует блоков и предметов.

## Проверено запуском

`Done (14.886s)!`, порт `*:25565`, все четыре наших мода в списке.
Ошибок нет — только обычные предупреждения mixin о необязательных целях
(easyanvils, toughasnails, starlight, sodium-опции) и таймаут
проверки обновлений NeoForge.

Тестовый мир после проверки удалён: сервер гасился `Stop-Process -Force`,
а не командой `stop`. Первый настоящий запуск создаст мир заново
(генерация ~3 минуты).

## Осталось руками — сессия сделать не может

1. **Правило файрвола.** `New-NetFirewallRule` упало на `Access is denied`.
   Владельцу: разрешить в подсказке Windows при первом запуске, либо
   в cmd от администратора
   `netsh advfirewall firewall add rule name="MC LMPC" dir=in action=allow protocol=TCP localport=25565`
2. **`op <ник>`** после первого захода в игру.
3. **Белый список.** Сейчас `white-list=false`, `online-mode=false` —
   зайти может любой под любым ником. Через Radmin риск мал, но
   порядок тот же, что в заметке от 2026-09-06:
   `whitelist add <ник>` × 8, `whitelist on`, `enforce-whitelist=true`.

## Заодно в этой же сессии

Пересобраны и разложены по одному в `mods/`: `plaguecore-0.2.0`
(был свежий) и `lmpc_classes-0.19.0` (в папке лежал устаревший 0.16.4).
Из `mods/` убраны семь старых дублей — по две-три версии одного мода
рядом, игра бы не поднялась. Дубли отложены в скретчпад сессии,
не удалены.

`mods/MODLIST.md` в рабочей копии остался с незакоммиченной правкой,
которая **откатывает файл к старому состоянию** (plaguecore 0.1.0,
«99 модов», дата 2026-09-03). Похоже на случайную перезапись старой
копией. Спрятана в `git stash` — разобрать и, скорее всего, выбросить.
