<#
    Проверка стартового экрана своим дев-клиентом.

    Правило: экран проверяется своим клиентом, а не нажатиями в игру
    владельца — синтетические нажатия до Minecraft не доходят ни в одном
    лаунчере, а аргумент запуска доходит.
    Подробности: docs/pamyat/proverka-gui-cherez-dev-klient.md

    Хост — дев-клиент plaguecore. Иконки средней колонки принадлежат
    lmpc_classes, но берутся из его джарника в mods/, а не из дев-запуска:
    в lmpc_classes владелец сидит сам, и запускать проверку там значит
    гасить ему клиент на каждой правке.

    Клиент сам заходит в мир: `runClient -PquickPlay="<мир>"` (свойство
    заведено в plaguecore/build.gradle), а мод welcomescreen показывает
    экран при первом входе — для этого сбрасывается его кэш. Мир берётся
    готовый из дев-клиента lmpc_classes: `--quickPlaySingleplayer` умеет
    войти в существующий, но не создать.

    Подменять идентификатор на титульный экран бесполезно: FancyMenu такой
    макет грузит без ошибок, но не применяет (проверено 2026-09-08).

        pwsh launcher/tools/dev-welcome.ps1

    Один прогон — около двух минут. Снимок ложится в снимки/.
#>

param(
    [int]$ЖдатьСекунд = 240,
    [string]$ИмяМира = 'New World',
    [switch]$БезСнимка
)

$ErrorActionPreference = 'Stop'
$корень = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$мод = Join-Path $корень 'plaguecore'
$рант = Join-Path $мод 'run\client'
$снимки = Join-Path $PSScriptRoot 'снимки'

<#
    Писать конфиги только этим, а не Set-Content.

    `Set-Content -Encoding utf8` в Windows PowerShell 5.1 ставит в начало
    файла BOM, а разборщик FancyMenu на нём спотыкается: первая строка
    перестаёт быть `type = ...`, и весь файл молча не грузится. В логе это
    видно как «Failed to deserialize PropertyContainerSet! Missing type».
    Проверено 2026-09-08: с BOM не грузились ни макет, ни список
    кастомных GUI.
#>
function Записать-Текст([string]$путь, [string]$текст) {
    [System.IO.File]::WriteAllText($путь, $текст, [System.Text.UTF8Encoding]::new($false))
}

# --- 1. Перегенерировать макет -----------------------------------------

$env:PYTHONIOENCODING = 'utf-8'
python (Join-Path $PSScriptRoot 'make-welcome-screen.py')
if ($LASTEXITCODE -ne 0) { throw 'генератор макета упал' }

$макет = Join-Path $корень 'launcher\pack-config\config\fancymenu\customization\welcome_screen.txt'

# --- 2. Разложить обвязку в дев-клиент ---------------------------------

$нужныеМоды = @(
    'welcomescreen-neoforge-1.0.0-1.21.1.jar',
    'fancymenu_neoforge_3.9.12_MC_1.21.1.jar',
    'Necronomicon-NeoForge-1.6.0+1.21.jar',
    'melody_neoforge_1.0.10_MC_1.21.jar',
    'konkrete_neoforge_1.9.9_MC_1.21.jar',
    # Иконки средней колонки: отвар клирика, кодекс, очиститель.
    'lmpc_classes-0.19.0.jar',
    # Create и Curios — жёсткие зависимости lmpc_classes.
    'create-1.21.1-6.0.10.jar',
    'curios-neoforge-9.5.1+1.21.1.jar'
)

New-Item -ItemType Directory -Force -Path (Join-Path $рант 'mods') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $рант 'config\fancymenu\customization') | Out-Null
New-Item -ItemType Directory -Force -Path $снимки | Out-Null

foreach ($имя in $нужныеМоды) {
    Copy-Item (Join-Path $корень "mods\$имя") (Join-Path $рант "mods\$имя") -Force
}

# В дев-клиенте лежит ровно один макет — титульный. Оба сразу класть
# нельзя: у элементов в них одинаковые instance_identifier, и FancyMenu
# считает второй макет дублем.
Get-ChildItem (Join-Path $рант 'config\fancymenu\customization') -Filter '*.txt' -ErrorAction SilentlyContinue |
    Remove-Item -Force

Copy-Item $макет (Join-Path $рант 'config\fancymenu\customization\welcome_screen.txt') -Force

# Мир для входа. Дев-клиент мир создать не умеет — `--quickPlaySingleplayer`
# входит только в существующий, — поэтому берём готовый из дев-клиента
# lmpc_classes.
$мир = Join-Path $рант "saves\$ИмяМира"
if (-not (Test-Path $мир)) {
    $источник = Join-Path $корень "lmpc_classes\run\client\saves\$ИмяМира"
    if (-not (Test-Path $источник)) { throw "мира «$ИмяМира» нет ни здесь, ни в lmpc_classes" }
    New-Item -ItemType Directory -Force -Path (Split-Path $мир -Parent) | Out-Null
    Copy-Item $источник $мир -Recurse -Force
}

# Экран показывается один раз за установку — сбрасываем отметку мода.
Записать-Текст (Join-Path $рант 'welcomescreen_cache.json') '{"shownWelcomeScreen": false}'

# Оба кастомных GUI заводит сам мод welcomescreen, но в чистом дев-клиенте
# файла ещё нет, а FancyMenu без него не знает экрана. Пишем сами: он
# короткий, и так у скрипта нет зависимости от чужой собранной сборки.
Записать-Текст (Join-Path $рант 'config\fancymenu\custom_gui_screens.txt') @'
type = custom_gui_screens

overridden_screens {
}

custom_gui {
  identifier = welcomescreen_update
  title = What's New?
  allow_esc = true
  transparent_world_background = true
  transparent_world_background_overlay = false
  pause_game = true
}
custom_gui {
  identifier = welcomescreen_welcome
  title =
  allow_esc = true
  transparent_world_background = true
  transparent_world_background_overlay = false
  pause_game = true
}
'@

# Панель «Customization / Tools / Help» в дев-режиме закрывает заголовок.
# Формат снят с рабочей сборки: секции в `##[…]`, строки `Т:ключ = 'знач';`,
# никакой строки `type =` в начале — в отличие от макетов.
Записать-Текст (Join-Path $рант 'config\fancymenu\options.txt') @'
##[general]

I:default_gui_scale = '-1';
B:force_fullscreen = 'false';
B:play_vanilla_menu_music = 'true';


##[customization]

B:modpack_mode = 'false';
B:show_customization_overlay = 'false';
B:advanced_customization_mode = 'false';
'@

# Без этого файла FancyMenu на первом запуске накрывает титульный экран
# окном «Welcome to FancyMenu!» — оно закрывает половину проверяемой
# раскладки, а закрыть его нечем: до дев-клиента не доходят ни мышь,
# ни клавиатура.
Записать-Текст (Join-Path $рант 'config\fancymenu\legacy_checklist.txt') @'
##[legacy]

B:custom_guis_ported = 'true';
'@

# Масштаб интерфейса 4 — тот самый тесный случай 480 × 270, под который
# считается раскладка. Окно ниже разворачивается на весь экран, иначе
# при масштабе 4 интерфейс получается уже макета и колонки срезает.
$настройкиИгры = Join-Path $рант 'options.txt'
if (Test-Path $настройкиИгры) {
    $строки = Get-Content $настройкиИгры
    if ($строки -match '^guiScale:') {
        $строки = $строки -replace '^guiScale:.*', 'guiScale:4'
    } else {
        $строки += 'guiScale:4'
    }
    Записать-Текст $настройкиИгры (($строки -join "`n") + "`n")
} else {
    Записать-Текст $настройкиИгры "guiScale:4`n"
}

# --- 3. Поднять клиент -------------------------------------------------

# Гасим ровно свой прошлый запуск. Свои процессы узнаются по пути проекта
# в командной строке; бить по названию окна нельзя — рядом крутится
# дев-клиент владельца с тем же названием, и 2026-09-08 я дважды выкинул
# его из игры именно так.
Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like '*plaguecore*' } |
    ForEach-Object { Write-Host "гашу свой прошлый клиент, pid $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

# Имя лога со временем: прошлый прогон ещё держит свой файл открытым
# (демон Gradle отпускает его не сразу), и перезаписать его нельзя.
$метка = Get-Date -Format 'HHmmss'
$лог = Join-Path $снимки "runClient-$метка.log"

$процесс = Start-Process -FilePath (Join-Path $мод 'gradlew.bat') `
    -ArgumentList 'runClient', '--console=plain', ('-PquickPlay="{0}"' -f $ИмяМира) `
    -WorkingDirectory $мод -PassThru `
    -RedirectStandardOutput $лог -RedirectStandardError "$лог.err" `
    -WindowStyle Hidden

Write-Host "клиент поднимается, pid $($процесс.Id), лог: $лог"

# --- 4. Дождаться своего окна и снять его -----------------------------
#
# Окно ищется по командной строке процесса (в ней стоит путь к нашим
# классам), а не по названию: рядом крутится дев-клиент владельца с тем же
# названием окна, и промахнуться значит снять — или, хуже, развернуть
# на весь экран — чужую игру. Дерево процессов для этого не годится:
# gradlew уходит в демона, и java оказывается не нашим потомком.

Add-Type -AssemblyName System.Drawing
$sig = @'
[DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
[DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int he, uint flags);
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
public struct RECT { public int L, T, R, B; }
'@
$u = Add-Type -MemberDefinition $sig -Name 'ОкноApi' -Namespace 'Dev' -PassThru |
     Where-Object { $_.Name -eq 'ОкноApi' }

$окно = $null
$дедлайн = (Get-Date).AddSeconds($ЖдатьСекунд)
while ((Get-Date) -lt $дедлайн) {
    Start-Sleep -Seconds 5
    if ($процесс.HasExited) { throw "gradlew вышел с кодом $($процесс.ExitCode), смотри $лог" }
    # Под фильтр попадает и демон Gradle — у него тоже наш путь в командной
    # строке, но окна нет. Поэтому берём не первый попавшийся процесс,
    # а тот из них, у которого есть окно игры.
    $моиПиды = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like '*plaguecore*' } |
        ForEach-Object { $_.ProcessId })
    $окно = Get-Process -Id $моиПиды -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
    if ($окно) { break }
}
if (-not $окно) { throw "своё окно Minecraft не появилось за $ЖдатьСекунд с, смотри $лог" }

# Размер окна под 1920 × 1080 клиентской области: при масштабе интерфейса 4
# это ровно те 480 × 270, под которые считается раскладка. Ставится без
# активации — окно владельца поверх остаётся, ничего не мигает.
[void]$u::SetWindowPos($окно.MainWindowHandle, [IntPtr]::Zero, 40, 20, 1936, 1119, 0x0014)  # NOZORDER|NOACTIVATE

# Дальше ждём не время, а строку в логе: клиент грузит мир, и сколько это
# займёт, заранее неизвестно.
$готово = $false
while ((Get-Date) -lt $дедлайн) {
    if (Select-String -Path $лог -Pattern 'registered: welcomescreen_welcome' -Quiet -ErrorAction SilentlyContinue) {
        $готово = $true
        break
    }
    if ($процесс.HasExited) { throw "gradlew вышел с кодом $($процесс.ExitCode), смотри $лог" }
    Start-Sleep -Seconds 5
}
if (-not $готово) { throw "стартовый экран не открылся за $ЖдатьСекунд с, смотри $лог" }

# Экран открылся, но кадр с ним ещё не отрисован.
Start-Sleep -Seconds 4

if (-not $БезСнимка) {
    $r = New-Object Dev.ОкноApi+RECT
    [void]$u::GetWindowRect($окно.MainWindowHandle, [ref]$r)
    $bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc()
    # PW_RENDERFULLCONTENT: без него у окна с OpenGL снимок выходит чёрным.
    [void]$u::PrintWindow($окно.MainWindowHandle, $dc, 2)
    $g.ReleaseHdc($dc)
    $файл = Join-Path $снимки ('экран-' + (Get-Date -Format 'HHmmss') + '.png')
    $bmp.Save($файл, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Host "снимок: $файл"
}

Write-Host "клиент оставлен запущенным (pid $($процесс.Id)); закрыть: Stop-Process -Id $($процесс.Id)"
