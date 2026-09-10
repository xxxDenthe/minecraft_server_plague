// Лаунчер обновляет сам себя из того же приватного релиза, откуда
// берёт пак. Своего кода тут немного, потому что скачивание с проверкой
// хеша уже написано в download.js, а чтение ассетов — в github.js.
//
// Имя ассета — launcher-release.json, а не launcher.json: последнее
// занято конфигом игрока (paths.configFile()), и одинаковые имена
// в логе сбивают с толку.

import path from 'node:path';

import * as paths from './paths.js';
import { releaseByTag, findAsset, assetUrl, apiHeaders } from './github.js';
import { downloadFile } from './download.js';
import { progressEvent, STAGES } from './progress.js';

const SHA256 = /^[0-9a-f]{64}$/i;
const VERSION = /^\d+\.\d+\.\d+$/;

export const RELEASE_ASSET = 'launcher-release.json';

function fail(message) {
  throw new Error(`описание релиза лаунчера: ${message}`);
}

const isText = (v) => typeof v === 'string' && v.trim() !== '';

export function parseLauncherRelease(text) {
  let raw;
  try {
    raw = JSON.parse(text);
  } catch (err) {
    fail(`ответ не JSON (${err.message})`);
  }
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) fail('не объект');

  if (!isText(raw.version) || !VERSION.test(raw.version)) fail('version: не вида 1.2.3');

  // Имя файла склеивается с папкой кэша на машине игрока: всё, что
  // может увести запись наружу, отсекается здесь.
  if (!isText(raw.file)) fail('file: пустое или не строка');
  if (/[/\\]/.test(raw.file) || raw.file.includes('..')) fail(`file: не имя файла «${raw.file}»`);

  if (!isText(raw.sha256) || !SHA256.test(raw.sha256)) fail('sha256: не 64 шестнадцатеричных символа');
  if (!Number.isInteger(raw.size) || raw.size <= 0) fail('size: не целое положительное');
  if (raw.notes !== undefined && typeof raw.notes !== 'string') fail('notes: не строка');

  return {
    version: raw.version,
    file: raw.file,
    size: raw.size,
    sha256: raw.sha256.toLowerCase(),
    notes: raw.notes ?? '',
  };
}

// Строкой версии сравнивать нельзя: «0.10.0» < «0.9.0» по алфавиту.
export function isNewer(remote, local) {
  const parts = (v) => String(v).split('.').map((n) => Number.parseInt(n, 10) || 0);
  const [a, b] = [parts(remote), parts(local)];
  for (let i = 0; i < 3; i += 1) {
    if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) > (b[i] ?? 0);
  }
  return false;
}

export function buildLauncherRelease({ version, file, size, sha256, notes = '' }) {
  return parseLauncherRelease(JSON.stringify({ version, file, size, sha256, notes }));
}

// Проверка идёт только на старте лаунчера. Пока игра запущена, менять
// .exe незачем: за паком в это время следит watchForUpdates.
export async function checkLauncherUpdate({ source, currentVersion, fetchImpl = fetch } = {}) {
  if (!source || source.kind !== 'github') return null;

  const { owner, repo, tag, token } = source;
  const release = await releaseByTag({ owner, repo, tag, token, fetchImpl });
  const descriptor = findAsset(release, RELEASE_ASSET);

  const response = await fetchImpl(assetUrl({ owner, repo, assetId: descriptor.id }), {
    headers: apiHeaders(token, 'application/octet-stream'),
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`GitHub ответил ${response.status} на ${RELEASE_ASSET}`);

  const remote = parseLauncherRelease(await response.text());
  if (!isNewer(remote.version, currentVersion)) return null;

  // Установщика ищем только после сравнения версий: на своей же версии
  // его отсутствие в релизе — не наша забота.
  const installer = findAsset(release, remote.file);
  return { ...remote, url: assetUrl({ owner, repo, assetId: installer.id }) };
}

export const installerDir = () => path.join(paths.root(), 'cache', 'launcher');

// Хеш сверяет downloadFile: файл с несошедшимся хешем до папки
// не доходит, и запускать нам будет нечего — это и нужно.
export async function downloadInstaller(update, { token = '', onProgress = null, fetchImpl = fetch } = {}) {
  const dest = path.join(installerDir(), update.file);

  let done = 0;
  const result = await downloadFile({
    url: update.url,
    dest,
    sha256: update.sha256,
    headers: apiHeaders(token, 'application/octet-stream'),
    fetchImpl,
    onBytes: (n) => {
      done += n;
      onProgress?.(progressEvent({
        stage: STAGES.LAUNCHER,
        bytesDone: done,
        bytesTotal: update.size,
        message: 'Обновляю лаунчер',
      }));
    },
  });

  return result.path;
}
