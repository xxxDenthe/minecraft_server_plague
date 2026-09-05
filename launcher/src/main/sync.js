// Приведение игровой папки к состоянию манифеста. Здесь живёт вся
// опасная логика лаунчера: этот модуль умеет удалять файлы в чужой
// папке. Поэтому решение отделено от исполнения — planSync только
// считает, applySync только делает.
//
// Пак приходит архивами, по одному на игровую папку. Что установлено,
// помнит `.lmpc-pack.json` рядом с игрой: сверять 262 МБ побайтово
// на каждом запуске лаунчера дороже, чем помнить хеш архива.

import fsp from 'node:fs/promises';
import path from 'node:path';

import { downloadAll } from './download.js';
import { unzip } from './archive.js';
import { STAGES } from './progress.js';
import { STATE_FILE, writeState } from './state.js';

// Пользовательские файлы, которые лаунчер не трогает никогда — даже
// если они лежат внутри управляемой папки и в паке их нет.
// Потерянная раскладка клавиш на третий день сессии — это возмущение,
// с которым игрок будет прав.
export const PROTECTED = Object.freeze([
  'options.txt',
  'optionsof.txt',
  'optionsshaders.txt',
  'servers.dat',
  'servers.dat_old',
  'saves',
  'screenshots',
  'logs',
  'crash-reports',
  'launcher.log',
  STATE_FILE,
]);

const toPosix = (p) => p.split(path.sep).join('/');

function isProtected(relative) {
  const parts = relative.split('/');
  return parts.some((part) => PROTECTED.includes(part));
}

async function listFiles(dir, base = dir) {
  let entries;
  try {
    entries = await fsp.readdir(dir, { withFileTypes: true });
  } catch (err) {
    if (err.code === 'ENOENT') return [];
    throw err;
  }

  const out = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await listFiles(full, base)));
    else out.push(toPosix(path.relative(base, full)));
  }
  return out;
}

// Пути внутри игровой папки, с именем управляемой папки впереди:
// именно в таком виде они лежат и в архиве, и в состоянии.
const listDir = async (instanceDir, dir) =>
  (await listFiles(path.join(instanceDir, dir))).map((name) => `${dir}/${name}`);

async function allExist(instanceDir, files) {
  for (const relative of files) {
    try {
      await fsp.access(path.join(instanceDir, relative));
    } catch {
      return false;
    }
  }
  return true;
}

/**
 * Чистый расчёт: какие архивы ставить, какие оставить, какие папки
 * убрать целиком. Ничего не меняет на диске.
 */
export async function planSync(manifest, instanceDir, state = { archives: {} }) {
  const installed = state.archives ?? {};

  const toInstall = [];
  const toKeep = [];

  for (const archive of manifest.archives) {
    const have = installed[archive.dir];

    // Хеш отвечает «та ли версия», список файлов — «цел ли пак».
    // Второе нужно ради живого сценария: игрок удалил мод, чтобы
    // «убрать лаги», и потом не понимает, почему не заходит.
    const same = have?.sha256 === archive.sha256 && (await allExist(instanceDir, have.files));

    if (same) toKeep.push(archive);
    else toInstall.push(archive);
  }

  // Пустой манифест — признак того, что загрузка сорвалась, а не команда
  // стереть пак. Самый дорогой сценарий: сервер отдал пустой ответ,
  // лаунчер честно его исполнил, восемь человек остались без модов
  // посреди сессии.
  if (manifest.archives.length === 0) {
    return {
      packVersion: manifest.packVersion,
      toInstall,
      toKeep,
      dirsToWipe: [],
      skippedDeletion: 'манифест пуст — удаление не выполняется',
    };
  }

  const wanted = new Set(manifest.archives.map((a) => a.dir));

  const dirsToWipe = [];
  for (const dir of manifest.managedDirs) {
    if (wanted.has(dir)) continue;
    if ((await listDir(instanceDir, dir)).length > 0) dirsToWipe.push(dir);
  }

  return {
    packVersion: manifest.packVersion,
    toInstall,
    toKeep,
    dirsToWipe,
    skippedDeletion: null,
  };
}

// Вычистить папку под распаковку, не тронув пользовательское.
async function wipeDir(instanceDir, dir) {
  const removed = [];

  for (const relative of await listDir(instanceDir, dir)) {
    if (isProtected(relative)) continue;
    await fsp.rm(path.join(instanceDir, relative), { force: true });
    removed.push(relative);
  }

  await removeEmptyParents(path.join(instanceDir, dir), instanceDir);
  return removed;
}

/**
 * Порядок: сначала скачать все архивы, потом что-либо трогать на диске.
 * Не наоборот — если сеть отвалится посередине, лучше остаться
 * со старым паком, чем с пустой папкой модов.
 */
export async function applySync(
  plan,
  { instanceDir, cacheDir, state = { archives: {} }, headers = {}, concurrency = 4, onProgress = null, ...rest } = {}
) {
  const cache = cacheDir ?? path.join(instanceDir, '.cache', 'pack');

  const items = plan.toInstall.map((archive) => ({
    url: archive.url,
    dest: path.join(cache, `${archive.dir}.zip`),
    sha256: archive.sha256,
    size: archive.size,
  }));

  await downloadAll(items, {
    ...rest,
    headers,
    concurrency,
    onProgress,
    stage: STAGES.PACK,
    message: 'архивы пака',
  });

  const archives = { ...(state.archives ?? {}) };
  const installed = [];

  for (const archive of plan.toInstall) {
    const file = path.join(cache, `${archive.dir}.zip`);

    await wipeDir(instanceDir, archive.dir);
    await unzip(file, instanceDir);

    archives[archive.dir] = {
      sha256: archive.sha256,
      files: await listDir(instanceDir, archive.dir),
    };
    installed.push(archive.dir);

    // Архив больше не нужен: второй раз ту же версию не качают,
    // а 262 МБ на диске игрока лежат зря.
    await fsp.rm(file, { force: true });
  }

  const wiped = [];
  for (const dir of plan.dirsToWipe) {
    await wipeDir(instanceDir, dir);
    delete archives[dir];
    wiped.push(dir);
  }

  // Состояние пишется последним: оборвись распаковка — на диске
  // осталось старое состояние, и следующий запуск повторит работу.
  await writeState(instanceDir, { packVersion: plan.packVersion, archives });

  return { installed, wiped, kept: plan.toKeep.map((a) => a.dir) };
}

// Пустая папка после удаления последнего мода — мусор, но подниматься
// выше инстанса нельзя ни при каких условиях.
async function removeEmptyParents(dir, instanceDir) {
  const stop = path.resolve(instanceDir);
  const start = path.resolve(dir);

  // Сначала вглубь: опустевшие подпапки мешают убрать родителя.
  for (const nested of await nestedDirsDeepestFirst(start)) {
    try {
      if ((await fsp.readdir(nested)).length === 0) await fsp.rmdir(nested);
    } catch {
      // Занято или уже нет — не наша забота.
    }
  }

  let current = start;
  while (current !== stop && current.startsWith(stop + path.sep)) {
    try {
      if ((await fsp.readdir(current)).length > 0) return;
      await fsp.rmdir(current);
    } catch {
      return;
    }
    current = path.dirname(current);
  }
}

async function nestedDirsDeepestFirst(dir) {
  let entries;
  try {
    entries = await fsp.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }

  const out = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const full = path.join(dir, entry.name);
    out.push(...(await nestedDirsDeepestFirst(full)), full);
  }
  return out;
}
