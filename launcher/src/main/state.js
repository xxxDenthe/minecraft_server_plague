// Что из пака реально лежит на диске игрока (спек раздачи архивами,
// раздел «Состояние на диске игрока»).
//
// Зачем файл, а не пересчёт хешей при каждом запуске: пак — это
// шесть архивов, и сверять их содержимое побайтово значит читать
// 262 МБ на каждом старте лаунчера. Хеш архива лежит здесь, а список
// распакованных файлов позволяет заметить, что игрок удалил мод
// руками.

import fsp from 'node:fs/promises';
import path from 'node:path';

export const STATE_FILE = '.lmpc-pack.json';

export const EMPTY = Object.freeze({ packVersion: 0, archives: {} });

export const stateFile = (instanceDir) => path.join(instanceDir, STATE_FILE);

const isText = (v) => typeof v === 'string' && v !== '';

// Битое состояние — не повод падать: хуже, чем лишняя перекачка,
// только лаунчер, который не запускается.
function parseState(raw) {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return EMPTY;

  const archives = {};
  const source = raw.archives;

  if (source !== null && typeof source === 'object' && !Array.isArray(source)) {
    for (const [dir, entry] of Object.entries(source)) {
      if (entry === null || typeof entry !== 'object' || Array.isArray(entry)) continue;
      if (!isText(entry.sha256)) continue;
      if (!Array.isArray(entry.files) || entry.files.some((f) => !isText(f))) continue;

      archives[dir] = { sha256: entry.sha256, files: [...entry.files] };
    }
  }

  const packVersion = Number.isInteger(raw.packVersion) && raw.packVersion >= 0 ? raw.packVersion : 0;

  return { packVersion, archives };
}

export async function readState(instanceDir) {
  try {
    return parseState(JSON.parse(await fsp.readFile(stateFile(instanceDir), 'utf8')));
  } catch {
    // Файла нет — установка с нуля. Файл нечитаем — считаем, что нет.
    return { ...EMPTY, archives: {} };
  }
}

export async function writeState(instanceDir, state) {
  const clean = parseState(state);
  await fsp.mkdir(instanceDir, { recursive: true });
  await fsp.writeFile(stateFile(instanceDir), `${JSON.stringify(clean, null, 2)}\n`, 'utf8');
  return clean;
}
