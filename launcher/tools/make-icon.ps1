# Иконка приложения из логотипа LMPC.
#
# Логотип широкий («LMPC» в строку), иконка квадратная — вписываем по
# ширине и центрируем по высоте на прозрачном фоне. Логотип не
# перерисовываем (ассет владельца), только масштабируем.
#
# На выходе (оба в src/renderer/assets/ — папка build/ у нас в .gitignore):
#   lmpc-icon.ico   — для electron-builder (exe + установщик), 7 размеров
#   lmpc-icon.png   — 256×256, для окна (BrowserWindow.icon)
#
# Запуск:  powershell -File tools/make-icon.ps1

param(
  [string]$Src    = "$PSScriptRoot\..\src\renderer\assets\lmpc-logo.png",
  [string]$IcoOut = "$PSScriptRoot\..\src\renderer\assets\lmpc-icon.ico",
  [string]$PngOut = "$PSScriptRoot\..\src\renderer\assets\lmpc-icon.png"
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$sizes = 16, 24, 32, 48, 64, 128, 256
# Не $src: параметр $Src объявлен [string] и переприсваивание вернуло бы строку.
$logo = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Src).Path)

function Render([int]$S) {
  $bmp = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode  = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  $scale = ($S * 0.96) / $logo.Width
  $w = [int][math]::Round($logo.Width  * $scale)
  $h = [int][math]::Round($logo.Height * $scale)
  $g.DrawImage($logo, [int](($S - $w) / 2), [int](($S - $h) / 2), $w, $h)
  $g.Dispose()
  $bmp
}

# PNG 256 для окна
$big = Render 256
New-Item -ItemType Directory -Force -Path (Split-Path $PngOut) | Out-Null
$big.Save($PngOut, [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()

# ICO: заголовок + каталог + PNG-блобы (PNG внутри .ico поддерживается с Vista)
$entries = foreach ($s in $sizes) {
  $bmp = Render $s
  $ms = New-Object System.IO.MemoryStream
  $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  [PSCustomObject]@{ Size = $s; Data = $ms.ToArray() }
}

New-Item -ItemType Directory -Force -Path (Split-Path $IcoOut) | Out-Null
$fs = [System.IO.File]::Create($IcoOut)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]$entries.Count)
$offset = 6 + 16 * $entries.Count
foreach ($e in $entries) {
  $dim = if ($e.Size -ge 256) { 0 } else { $e.Size }
  $bw.Write([Byte]$dim); $bw.Write([Byte]$dim)
  $bw.Write([Byte]0);    $bw.Write([Byte]0)
  $bw.Write([UInt16]1);  $bw.Write([UInt16]32)
  $bw.Write([UInt32]$e.Data.Length)
  $bw.Write([UInt32]$offset)
  $offset += $e.Data.Length
}
foreach ($e in $entries) { $bw.Write($e.Data) }
$bw.Close(); $fs.Close()
$logo.Dispose()

Write-Host "готово: icon.ico ($($entries.Count) размеров) + lmpc-icon.png"
