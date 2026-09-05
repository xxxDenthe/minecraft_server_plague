// Единственное место, где склеиваются пути. Всё остальное спрашивает
// здесь — иначе рано или поздно кто-нибудь напишет путь руками и попадёт
// в ванильный .minecraft, который мы не трогаем ни при каких условиях
// (спек, раздел 6).

import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

// Переопределение корня нужно тестам и ручной отладке: топтать настоящую
// папку игрока ради проверки гипотезы не стоит.
export function root() {
  const override = process.env.LMPC_LAUNCHER_ROOT;
  if (override) return path.resolve(override);

  const appData =
    process.env.APPDATA ?? path.join(os.homedir(), 'AppData', 'Roaming');
  return path.join(path.resolve(appData), 'LMPC');
}

export const instance = () => path.join(root(), 'instance');
export const runtime = () => path.join(root(), 'runtime');
export const versions = () => path.join(root(), 'versions');
export const libraries = () => path.join(root(), 'libraries');
export const assets = () => path.join(root(), 'assets');
export const assetObjects = () => path.join(assets(), 'objects');
export const assetIndexes = () => path.join(assets(), 'indexes');
// Куда ложатся архивы пака на время распаковки. Внутри инстанса им
// нельзя: инстанс — это то, что лаунчер чистит.
export const packCache = () => path.join(root(), 'cache', 'pack');
export const configFile = () => path.join(root(), 'launcher.json');
export const logFile = () => path.join(root(), 'launcher.log');

// Путь к javaw.exe нашей JRE. Системная Java не используется никогда:
// на машине владельца их четыре, и `java` отвечает не той (спек, 7).
export const javaExe = () => path.join(runtime(), 'bin', 'javaw.exe');
export const javaConsoleExe = () => path.join(runtime(), 'bin', 'java.exe');

// Путь внутри игровой папки из строки манифеста. Проверка на выход
// наружу — здесь, а не только в manifest.js: два замка на одной двери
// дешевле, чем один разбор того, как файл оказался в System32.
export function inInstance(relative) {
  const base = instance();
  const full = path.resolve(base, relative);

  if (full !== base && !full.startsWith(base + path.sep)) {
    throw new Error(`путь выходит за пределы игровой папки: ${relative}`);
  }
  return full;
}

export function ensureDirs() {
  for (const dir of [root(), instance(), runtime(), versions(), libraries(), assets()]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}
