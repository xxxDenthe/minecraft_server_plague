// Приведение игровой папки к состоянию манифеста (спек, раздел 5).
// Здесь живёт вся опасная логика лаунчера: этот модуль умеет удалять
// файлы в чужой папке. Поэтому решение отделено от исполнения —
// planSync только считает, applySync только делает.

import fsp from 'node:fs/promises';
import path from 'node:path';

import { fileMatches, downloadAll } from './download.js';
import { STAGES } from './progress.js';

// Пользовательские файлы, которые лаунчер не трогает никогда — даже
// если они лежат внутри управляемой папки и в манифесте их нет.
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

/**
 * Чистый расчёт: что скачать, что оставить, что удалить.
 * Ничего не меняет на диске — только читает и считает хеши.
 */
export async function planSync(manifest, instanceDir) {
  const wanted = new Map(manifest.files.map((f) => [f.path, f]));

  const toDownload = [];
  const toKeep = [];

  for (const file of manifest.files) {
    const dest = path.join(instanceDir, file.path);
    if (await fileMatches(dest, file.sha256)) toKeep.push(file);
    else toDownload.push(file);
  }

  // Пустой манифест — признак того, что загрузка сорвалась, а не команда
  // стереть пак. Самый дорогой сценарий: сервер отдал пустой ответ,
  // лаунчер честно его исполнил, восемь человек остались без модов
  // посреди сессии.
  if (manifest.files.length === 0) {
    return {
      toDownload,
      toKeep,
      toDelete: [],
      skippedDeletion: 'манифест пуст — удаление не выполняется',
    };
  }

  const toDelete = [];
  for (const dir of manifest.managedDirs) {
    const onDisk = await listFiles(path.join(instanceDir, dir));
    for (const name of onDisk) {
      const relative = `${dir}/${name}`;
      if (wanted.has(relative)) continue;
      if (isProtected(relative)) continue;
      toDelete.push(relative);
    }
  }

  return { toDownload, toKeep, toDelete, skippedDeletion: null };
}

/**
 * Порядок: сначала скачать всё новое, потом удалить лишнее.
 * Не наоборот — если сеть отвалится посередине, лучше остаться
 * с лишними файлами, чем без нужных.
 */
export async function applySync(
  plan,
  { instanceDir, headers = {}, concurrency = 8, onProgress = null, ...rest } = {}
) {
  const items = plan.toDownload.map((file) => ({
    url: file.url,
    dest: path.join(instanceDir, file.path),
    sha256: file.sha256,
    size: file.size,
  }));

  await downloadAll(items, {
    ...rest,
    headers,
    concurrency,
    onProgress,
    stage: STAGES.PACK,
    message: 'файлы пака',
  });

  const deleted = [];
  for (const relative of plan.toDelete) {
    const full = path.join(instanceDir, relative);
    await fsp.rm(full, { force: true });
    deleted.push(relative);
    await removeEmptyParents(path.dirname(full), instanceDir);
  }

  return { downloaded: items.length, deleted };
}

// Пустая папка после удаления последнего мода — мусор, но подниматься
// выше инстанса нельзя ни при каких условиях.
async function removeEmptyParents(dir, instanceDir) {
  let current = path.resolve(dir);
  const stop = path.resolve(instanceDir);

  while (current !== stop && current.startsWith(stop + path.sep)) {
    try {
      const entries = await fsp.readdir(current);
      if (entries.length > 0) return;
      await fsp.rmdir(current);
    } catch {
      return;
    }
    current = path.dirname(current);
  }
}
