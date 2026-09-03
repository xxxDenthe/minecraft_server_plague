// Порядок установки из спека, раздел 8, собранный в одном месте:
// Java → ванильный клиент и ассеты → NeoForge → файлы пака → запуск.
// Модуль ничего не знает про окно: он принимает обработчики событий
// и потому отлаживается из терминала за секунды.

import path from 'node:path';

import * as paths from './paths.js';
import { readConfig, writeConfig } from './config.js';
import { fetchManifest } from './manifest.js';
import { ensureJava } from './java.js';
import { ensureVanilla, ensureVersionJson } from './minecraft.js';
import { ensureNeoForge, readProfile } from './neoforge.js';
import { planSync, applySync } from './sync.js';
import { launchGame } from './launch.js';
import { progressEvent, STAGES } from './progress.js';

// Адрес манифеста и токен доступа к приватному релизу подставляются
// при сборке (спек, раздел 12). Пока релиза нет, лаунчер обязан
// оставаться работоспособным: без манифеста он ставит чистый клиент
// с NeoForge и честно об этом пишет.
export const MANIFEST_URL = process.env.PLAGUE_MANIFEST_URL ?? '';
export const MANIFEST_TOKEN = process.env.PLAGUE_MANIFEST_TOKEN ?? '';

export function manifestHeaders(token = MANIFEST_TOKEN) {
  if (!token) return {};
  return { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' };
}

export async function loadManifest({ url = MANIFEST_URL, fetchImpl = fetch } = {}) {
  if (!url) return null;
  return fetchManifest(url, { fetchImpl, headers: manifestHeaders() });
}

// Файлы пака ставятся при каждом запуске, всё остальное — один раз.
export async function syncPack(manifest, { onProgress = null, ...rest } = {}) {
  const instance = paths.instance();

  onProgress?.(progressEvent({ stage: STAGES.PACK, message: 'сверяю файлы пака' }));

  const plan = await planSync(manifest, instance);

  if (plan.skippedDeletion) {
    onProgress?.(progressEvent({ stage: STAGES.PACK, message: plan.skippedDeletion }));
  }

  const result = await applySync(plan, {
    ...rest,
    instanceDir: instance,
    headers: manifestHeaders(),
    onProgress,
  });

  return { plan, result };
}

export async function prepare({
  nickname,
  onProgress = null,
  manifestUrl = MANIFEST_URL,
  fetchImpl = fetch,
  ...rest
} = {}) {
  paths.ensureDirs();

  const config = readConfig();
  const manifest = await loadManifest({ url: manifestUrl, fetchImpl });

  const minecraft = manifest?.minecraft ?? '1.21.1';
  const neoforge = manifest?.neoforge ?? '21.1.249';

  await ensureJava({ ...rest, major: manifest?.java?.major ?? 21, onProgress });

  const vanilla = await ensureVanilla(minecraft, { ...rest, onProgress });

  await ensureNeoForge(neoforge, {
    ...rest,
    javaExe: paths.javaConsoleExe(),
    onProgress,
  });

  const forgeProfile = await readProfile(neoforge);

  if (manifest) {
    await syncPack(manifest, { ...rest, onProgress });
    writeConfig({ ...config, nickname: nickname ?? config.nickname, packVersion: manifest.packVersion });
  } else {
    onProgress?.(
      progressEvent({
        stage: STAGES.PACK,
        message: 'манифест не задан — ставлю чистый клиент с NeoForge, без модов пака',
      })
    );
  }

  return {
    manifest,
    vanillaJson: vanilla.versionJson,
    forgeProfile,
    clientJar: vanilla.clientJar,
    minecraft,
    neoforge,
  };
}

export async function play({ nickname, maxRamMb = null, onProgress = null, onLine = null, ...rest } = {}) {
  if (!nickname?.trim()) throw new Error('без ника запускать нечего');

  const prepared = await prepare({ ...rest, nickname, onProgress });
  const config = readConfig();

  const ram = maxRamMb ?? prepared.manifest?.launch?.maxRamMb ?? config.maxRamMb;

  onProgress?.(progressEvent({ stage: STAGES.LAUNCH, message: 'запускаю игру' }));

  const started = await launchGame({
    javaExe: paths.javaExe(),
    vanillaJson: prepared.vanillaJson,
    forgeProfile: prepared.forgeProfile,
    clientJar: prepared.clientJar,
    nickname: nickname.trim(),
    maxRamMb: ram,
    extraJvmArgs: prepared.manifest?.launch?.jvmArgs ?? [],
    server: prepared.manifest?.server ?? null,
    onLine,
  });

  writeConfig({ ...config, nickname: nickname.trim(), maxRamMb: ram, lastPlayed: new Date().toISOString() });

  return { ...started, prepared };
}

// Опрос манифеста, пока игра запущена (спек, раздел 10). Файлы
// не докачиваются: заменять джарник под работающей игрой — способ
// получить краш в самом интересном месте.
export function watchForUpdates({
  knownVersion,
  intervalMs = 5 * 60 * 1000,
  url = MANIFEST_URL,
  fetchImpl = fetch,
  onUpdate = null,
  onError = null,
} = {}) {
  if (!url) return () => {};

  let stopped = false;
  let announced = knownVersion;

  const tick = async () => {
    if (stopped) return;
    try {
      const manifest = await fetchManifest(url, { fetchImpl, headers: manifestHeaders() });
      if (manifest.packVersion > announced) {
        announced = manifest.packVersion;
        onUpdate?.(manifest.packVersion);
      }
    } catch (err) {
      // Пропавшая на минуту сеть не должна ронять лаунчер посреди игры.
      onError?.(err);
    }
  };

  const timer = setInterval(tick, intervalMs);
  timer.unref?.();

  return () => {
    stopped = true;
    clearInterval(timer);
  };
}

export const gameLogFile = () => path.join(paths.root(), 'game.log');
