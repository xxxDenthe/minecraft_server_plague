// Скачивание с проверкой SHA-256. Правило одно: в папке игрока
// не должно появиться ни одного файла, чей хеш мы не сверили.
// Недокачанный джарник в mods/ хуже, чем его отсутствие: игра падает
// с сообщением, по которому ничего не понять.

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

import { progressEvent } from './progress.js';

export async function hashOfFile(file, algorithm = 'sha256') {
  const hash = createHash(algorithm);
  await pipeline(fs.createReadStream(file), hash);
  return hash.digest('hex');
}

export const sha256OfFile = (file) => hashOfFile(file, 'sha256');

// Файлы пака проверяются по SHA-256, файлы Mojang — по SHA-1: чужой
// формат, чужой выбор, спорить не с кем.
export function checksumOf({ sha256 = null, sha1 = null } = {}) {
  if (sha256) return { algorithm: 'sha256', value: sha256.toLowerCase() };
  if (sha1) return { algorithm: 'sha1', value: sha1.toLowerCase() };
  return null;
}

export async function fileMatches(file, checksum) {
  const wanted = typeof checksum === 'string' ? checksumOf({ sha256: checksum }) : checksum;
  if (!wanted) return false;

  try {
    return (await hashOfFile(file, wanted.algorithm)) === wanted.value;
  } catch (err) {
    if (err.code === 'ENOENT') return false;
    throw err;
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function removeQuietly(file) {
  await fsp.rm(file, { force: true });
}

// Одна попытка: скачать во временный файл, посчитать хеш на лету
// и переименовать только после сверки. Игрок закроет лаунчер
// на середине — и папка не останется с обрубками.
async function attempt({ url, dest, checksum, headers, fetchImpl, onBytes }) {
  const temp = `${dest}.part`;
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  await removeQuietly(temp);

  const response = await fetchImpl(url, { headers, redirect: 'follow' });
  if (!response.ok) {
    throw new Error(`сервер ответил ${response.status} на ${url}`);
  }
  if (!response.body) {
    throw new Error(`пустой ответ на ${url}`);
  }

  const hash = createHash(checksum?.algorithm ?? 'sha256');
  const out = fs.createWriteStream(temp);
  const source = Readable.fromWeb(response.body);

  source.on('data', (chunk) => {
    hash.update(chunk);
    onBytes?.(chunk.length);
  });

  try {
    await pipeline(source, out);
  } catch (err) {
    await removeQuietly(temp);
    throw err;
  }

  const actual = hash.digest('hex');
  if (checksum && actual !== checksum.value) {
    await removeQuietly(temp);
    throw new Error(`хеш не совпал для ${url}: ждали ${checksum.value}, получили ${actual}`);
  }

  await fsp.rm(dest, { force: true });
  await fsp.rename(temp, dest);

  return actual;
}

export async function downloadFile({
  url,
  dest,
  sha256 = null,
  sha1 = null,
  headers = {},
  retries = 3,
  retryDelayMs = 500,
  fetchImpl = fetch,
  onBytes = null,
}) {
  const checksum = checksumOf({ sha256, sha1 });

  // Уже лежит с верным хешем — не качаем. Ради этого правила и заведён
  // весь манифест: повторный запуск не должен тянуть гигабайт заново.
  if (checksum && (await fileMatches(dest, checksum))) {
    return { path: dest, sha256: checksum.value, downloaded: false };
  }

  let lastError;
  for (let tryNo = 1; tryNo <= retries; tryNo += 1) {
    try {
      const actual = await attempt({ url, dest, checksum, headers, fetchImpl, onBytes });
      return { path: dest, sha256: actual, downloaded: true };
    } catch (err) {
      lastError = err;
      // Пауза растёт: сервер, который лёг под восемью параллельными
      // запросами, от немедленного повтора не оживает.
      if (tryNo < retries) await sleep(retryDelayMs * tryNo);
    }
  }

  throw new Error(`не удалось скачать ${url} за ${retries} попытки: ${lastError.message}`, {
    cause: lastError,
  });
}

// Окно из concurrency задач. Больше восьми смысла не имеет: упирается
// не в нас, а в канал игрока; на ассетах ставим шестнадцать, там файлы
// мелкие и задержка важнее полосы.
export async function downloadAll(
  items,
  { concurrency = 8, onProgress = null, stage = '', message = '', ...rest } = {}
) {
  const total = items.length;
  const bytesTotal = items.reduce((sum, item) => sum + (item.size ?? 0), 0);

  let current = 0;
  let bytesDone = 0;
  let next = 0;

  const report = () =>
    onProgress?.(progressEvent({ stage, current, total, bytesDone, bytesTotal, message }));

  report();

  const results = new Array(total);
  const failures = [];

  async function worker() {
    while (next < total) {
      const index = next;
      next += 1;

      try {
        results[index] = await downloadFile({
          ...rest,
          ...items[index],
          onBytes: (n) => {
            bytesDone += n;
          },
        });
      } catch (err) {
        failures.push(err);
      }

      current += 1;
      report();
    }
  }

  await Promise.all(
    Array.from({ length: Math.max(1, Math.min(concurrency, total || 1)) }, worker)
  );

  if (failures.length > 0) {
    throw new Error(
      `не скачалось файлов: ${failures.length} из ${total}. Первая ошибка: ${failures[0].message}`,
      { cause: failures[0] }
    );
  }

  return results;
}
