<#
    Посмотреть на запись архива своим дев-клиентом.

    Правило проекта: игровой экран проверяется своим клиентом, а не
    нажатиями в игру владельца — синтетические нажатия до Minecraft
    не доходят, а аргумент запуска доходит.
    Подробности: docs/pamyat/proverka-gui-cherez-dev-klient.md

    Клиент входит в мир сам (-PquickPlay), проба внутри мода сама берёт
    записи функцией выдачи и открывает одну (-PprobaZapis — номер ячейки
    инвентаря). Дальше снимается три кадра подряд: проба листает страницу
    раз в две секунды, поэтому кадры показывают и разные листы, и
    переворот.

        powershell -File launcher/tools/dev-zapis.ps1 -Ячейка 0

    Снимки ложатся в launcher/tools/снимки/.
#>

param(
    [int]$Ячейка = 0,
    [int]$ЖдатьСекунд = 300,
    [string]$ИмяМира = 'New World'
)

$ErrorActionPreference = 'Stop'
$корень = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$мод = Join-Path $корень 'plaguecore'
$рант = Join-Path $мод 'run\client'
$снимки = Join-Path $PSScriptRoot 'снимки'
New-Item -ItemType Directory -Force -Path $снимки | Out-Null

function Записать-Текст([string]$путь, [string]$текст) {
    [System.IO.File]::WriteAllText($путь, $текст, [System.Text.UTF8Encoding]::new($false))
}

# Полноэкранный режим выключается принудительно: развёрнутое окно при
# потере фокуса перестаёт рисовать, и PrintWindow отдаёт черноту.
$настройкиИгры = Join-Path $рант 'options.txt'
$нужно = @{ 'guiScale' = '3'; 'fullscreen' = 'false' }
if (Test-Path $настройкиИгры) {
    $строки = Get-Content $настройкиИгры
    foreach ($ключ in $нужно.Keys) {
        if ($строки -match ('^' + $ключ + ':')) {
            $строки = $строки -replace ('^' + $ключ + ':.*'), ($ключ + ':' + $нужно[$ключ])
        } else { $строки += ($ключ + ':' + $нужно[$ключ]) }
    }
    Записать-Текст $настройкиИгры (($строки -join "`n") + "`n")
} else {
    Записать-Текст $настройкиИгры "guiScale:3`nfullscreen:false`n"
}

# Гасим только свой прошлый клиент: рядом крутится дев-клиент владельца.
Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like '*plaguecore*run*client*' } |
    ForEach-Object { Write-Host "гашу свой прошлый клиент, pid $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$метка = Get-Date -Format 'HHmmss'
$лог = Join-Path $снимки "zapis-$метка.log"

$процесс = Start-Process -FilePath (Join-Path $мод 'gradlew.bat') `
    -ArgumentList 'runClient', '--console=plain', ('-PquickPlay="{0}"' -f $ИмяМира), ('-PprobaZapis={0}' -f $Ячейка) `
    -WorkingDirectory $мод -PassThru `
    -RedirectStandardOutput $лог -RedirectStandardError "$лог.err" `
    -WindowStyle Hidden
Write-Host "клиент поднимается, pid $($процесс.Id), лог: $лог"

Add-Type -AssemblyName System.Drawing
$sig = @'
[DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
[DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int he, uint flags);
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
public struct RECT { public int L, T, R, B; }
'@
$u = Add-Type -MemberDefinition $sig -Name 'ОкноApi' -Namespace 'DevZapis' -PassThru |
     Where-Object { $_.Name -eq 'ОкноApi' }

$окно = $null
$дедлайн = (Get-Date).AddSeconds($ЖдатьСекунд)
while ((Get-Date) -lt $дедлайн) {
    Start-Sleep -Seconds 5
    if ($процесс.HasExited) { throw "gradlew вышел с кодом $($процесс.ExitCode), смотри $лог" }
    $моиПиды = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like '*plaguecore*' } |
        ForEach-Object { $_.ProcessId })
    $окно = Get-Process -Id $моиПиды -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
    if ($окно) { break }
}
if (-not $окно) { throw "своё окно Minecraft не появилось за $ЖдатьСекунд с, смотри $лог" }

[void]$u::SetWindowPos($окно.MainWindowHandle, [IntPtr]::Zero, 40, 20, 1616, 939, 0x0014)

$готово = $false
while ((Get-Date) -lt $дедлайн) {
    if (Select-String -Path $лог -Pattern 'proba-zapis-ready' -Quiet -ErrorAction SilentlyContinue) {
        $готово = $true; break
    }
    if ($процесс.HasExited) { throw "gradlew вышел с кодом $($процесс.ExitCode), смотри $лог" }
    Start-Sleep -Milliseconds 400
}
if (-not $готово) { throw "проба не открыла запись за $ЖдатьСекунд с, смотри $лог" }

function Снять([string]$файл) {
    $r = New-Object DevZapis.ОкноApi+RECT
    [void]$u::GetWindowRect($окно.MainWindowHandle, [ref]$r)
    $bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc()
    [void]$u::PrintWindow($окно.MainWindowHandle, $dc, 2)   # PW_RENDERFULLCONTENT
    $g.ReleaseHdc($dc); $g.Dispose()
    $bmp.Save($файл, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "снимок: $файл"
}

Start-Sleep -Milliseconds 400
for ($н = 1; $н -le 3; $н++) {
    Снять (Join-Path $снимки ("запись-{0}-{1}.png" -f $метка, $н))
    Start-Sleep -Milliseconds 1700
}

Write-Host "клиент оставлен запущенным (pid $($процесс.Id)); закрыть: Stop-Process -Id $($процесс.Id)"
