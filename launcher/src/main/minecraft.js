// Ванильный клиент, библиотеки и ассеты (спек, раздел 8, шаги 1–2).
// Про NeoForge этот модуль не знает: его дело — довести папку
// до состояния, в котором ванильная игра запустилась бы сама.

import fsp from 'node:fs/promises';
import path from 'node:path';

import * as paths from './paths.js';
import { downloadFile, downloadAll } from './download.js';
import { progressEvent, STAGES } from './progress.js';

const VERSION_MANIFEST = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
const RESOURCES = 'https://resources.download.minecraft.net';

// Правила библиотек: список allow/deny, побеждает последнее совпавшее.
// Так же считает официальный лаунчер. Половина из 97 библиотек 1.21.1
// под Windows не нужна — это macOS и Linux.
export function ruleAllows(rules, { os = 'windows', arch = 'x64' } = {}) {
  if (!Array.isArray(rules) || rules.length === 0) return true;

  let allowed = false;
  for (const rule of rules) {
    const target = rule.os ?? {};

    if (target.name && target.name !== os) continue;
    if (target.arch && target.arch !== arch) continue;
    // Ограничения по версии ОС в 1.21.1 не встречаются; если появятся,
    // правило лучше пропустить, чем понять неверно.
    if (target.version) continue;
    // Правила по features (демо-режим, своё разрешение экрана)
    // к библиотекам не относятся — там их нет.
    if (rule.features) continue;

    allowed = rule.action === 'allow';
  }
  return allowed;
}

export function windowsLibraries(versionJson, platform = {}) {
  return versionJson.libraries
    .filter((lib) => ruleAllows(lib.rules, platform))
    .map((lib) => lib.downloads?.artifact)
    .filter(Boolean);
}

async function readJsonCache(file) {
  try {
    return JSON.parse(await fsp.readFile(file, 'utf8'));
  } catch {
    return null;
  }
}

export async function fetchVersionEntry(id, { fetchImpl = fetch } = {}) {
  const response = await fetchImpl(VERSION_MANIFEST, { redirect: 'follow' });
  if (!response.ok) throw new Error(`Mojang ответил ${response.status} на список версий`);

  const list = await response.json();
  const entry = list.versions?.find((v) => v.id === id);

  if (!entry) throw new Error(`версии ${id} нет в списке Mojang`);
  return entry;
}

// Описание версии кэшируется: оно не меняется, а лишний запрос
// на старте игры — лишний повод не запуститься без сети.
export async function ensureVersionJson(id, { fetchImpl = fetch, ...rest } = {}) {
  const dest = path.join(paths.versions(), id, `${id}.json`);
  const cached = await readJsonCache(dest);
  if (cached) return cached;

  const entry = await fetchVersionEntry(id, { fetchImpl });
  await downloadFile({ ...rest, url: entry.url, dest, sha1: entry.sha1, fetchImpl });

  const json = await readJsonCache(dest);
  if (!json) throw new Error(`описание версии ${id} скачалось, но не разбирается`);
  return json;
}

export async function ensureClient(versionJson, { onProgress = null, ...rest } = {}) {
  const id = versionJson.id;
  const dest = path.join(paths.versions(), id, `${id}.jar`);
  const client = versionJson.downloads.client;

  let bytesDone = 0;
  await downloadFile({
    ...rest,
    url: client.url,
    dest,
    sha1: client.sha1,
    onBytes: (n) => {
      bytesDone += n;
      onProgress?.(
        progressEvent({
          stage: STAGES.MINECRAFT,
          bytesDone,
          bytesTotal: client.size,
          message: `клиент ${id}`,
        })
      );
    },
  });

  return dest;
}

export async function ensureLibraries(versionJson, { onProgress = null, ...rest } = {}) {
  const artifacts = windowsLibraries(versionJson);

  const items = artifacts.map((a) => ({
    url: a.url,
    dest: path.join(paths.libraries(), ...a.path.split('/')),
    sha1: a.sha1,
    size: a.size,
  }));

  await downloadAll(items, {
    ...rest,
    onProgress,
    stage: STAGES.MINECRAFT,
    message: 'библиотеки',
  });

  return items.map((i) => i.dest);
}

export async function ensureAssets(versionJson, { onProgress = null, fetchImpl = fetch, ...rest } = {}) {
  const { id, url, sha1 } = versionJson.assetIndex;
  const indexFile = path.join(paths.assetIndexes(), `${id}.json`);

  await downloadFile({ ...rest, url, dest: indexFile, sha1, fetchImpl });

  const index = await readJsonCache(indexFile);
  if (!index?.objects) throw new Error(`индекс ассетов ${id} скачался, но не разбирается`);

  const items = Object.values(index.objects).map(({ hash, size }) => ({
    url: `${RESOURCES}/${hash.slice(0, 2)}/${hash}`,
    dest: path.join(paths.assetObjects(), hash.slice(0, 2), hash),
    sha1: hash,
    size,
  }));

  // Четыре тысячи мелких файлов: узкое место — не полоса, а задержка,
  // поэтому параллельность выше обычной.
  await downloadAll(items, {
    ...rest,
    fetchImpl,
    concurrency: 16,
    onProgress,
    stage: STAGES.ASSETS,
    message: 'ресурсы игры',
  });

  return { indexId: id, count: items.length };
}

export async function ensureVanilla(id = '1.21.1', options = {}) {
  const versionJson = await ensureVersionJson(id, options);

  const clientJar = await ensureClient(versionJson, options);
  const classpath = await ensureLibraries(versionJson, options);
  const assets = await ensureAssets(versionJson, options);

  return {
    versionJson,
    clientJar,
    classpath: [...classpath, clientJar],
    assetIndexId: assets.indexId,
    assetsDir: paths.assets(),
    mainClass: versionJson.mainClass,
  };
}
