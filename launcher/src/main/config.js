// launcher.json: ник, память, версия пака. Файл маленький и целиком
// переписывается — истории версий тут не нужно.

import fs from 'node:fs';
import path from 'node:path';

import * as paths from './paths.js';

export const DEFAULTS = Object.freeze({
  nickname: '',
  minRamMb: 1024,
  maxRamMb: 6144,
  jvmArgs: '',
  packVersion: 0,
  lastPlayed: null,
});

// Испорченный конфиг не должен мешать игроку запустить игру: молча
// подставляем значения по умолчанию и пишем в лог. Разбираться с битым
// JSON посреди сессии — не его работа.
export function readConfig({ onWarn = console.warn } = {}) {
  const file = paths.configFile();

  let text;
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch (err) {
    if (err.code !== 'ENOENT') onWarn(`конфиг не читается (${err.message}), беру значения по умолчанию`);
    return { ...DEFAULTS };
  }

  let raw;
  try {
    raw = JSON.parse(text);
  } catch (err) {
    onWarn(`конфиг испорчен (${err.message}), беру значения по умолчанию`);
    return { ...DEFAULTS };
  }

  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    onWarn('конфиг не объект, беру значения по умолчанию');
    return { ...DEFAULTS };
  }

  const maxRamMb = Number.isInteger(raw.maxRamMb) && raw.maxRamMb > 0 ? raw.maxRamMb : DEFAULTS.maxRamMb;

  return {
    nickname: typeof raw.nickname === 'string' ? raw.nickname : DEFAULTS.nickname,
    // Нижний порог не может быть больше верхнего: битую пару чиним молча.
    minRamMb:
      Number.isInteger(raw.minRamMb) && raw.minRamMb > 0 && raw.minRamMb <= maxRamMb
        ? raw.minRamMb
        : Math.min(DEFAULTS.minRamMb, maxRamMb),
    maxRamMb,
    jvmArgs: typeof raw.jvmArgs === 'string' ? raw.jvmArgs : DEFAULTS.jvmArgs,
    packVersion: Number.isInteger(raw.packVersion) && raw.packVersion >= 0 ? raw.packVersion : DEFAULTS.packVersion,
    lastPlayed: typeof raw.lastPlayed === 'string' ? raw.lastPlayed : DEFAULTS.lastPlayed,
  };
}

export function writeConfig(config) {
  const file = paths.configFile();
  fs.mkdirSync(path.dirname(file), { recursive: true });

  const merged = { ...DEFAULTS, ...config };
  // Запись через временный файл: выдернутый шнур не должен оставить
  // обрубок вместо конфига.
  const temp = `${file}.tmp`;
  fs.writeFileSync(temp, `${JSON.stringify(merged, null, 2)}\n`, 'utf8');
  fs.renameSync(temp, file);

  return merged;
}
