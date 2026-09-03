// Порядок установки из спека, раздел 8, собранный в одном месте:
// Java → ванильный клиент и ассеты → NeoForge → файлы пака → запуск.
// Модуль ничего не знает про окно: он принимает обработчики событий
// и потому отлаживается из терминала за секунды.

import path from 'node:path';

import * as paths from './paths.js';
import { readConfig, writeConfig } from './config.js';
import { fetchManifest, parseManifest } from './manifest.js';
import { fetchPackManifestText, apiHeaders } from './github.js';
import { distribution } from './distribution.js';
import { ensureJava } from './java.js';
import { ensureVanilla, ensureVersionJson } from './minecraft.js';
import { ensureNeoForge, readProfile } from './neoforge.js';
import { planSync, applySync } from './sync.js';
import { launchGame } from './launch.js';
import { progressEvent, STAGES } from './progress.js';

// Откуда берётся пак: приватный релиз GitHub плюс токен только
// на чтение (спек, раздел 12). Значения кладёт в сборку
// `npm run configure`. Пока раздачи нет, лаунчер обязан оставаться
// работоспособным — без манифеста он ставит чистый клиент с NeoForge
// и честно об этом пишет.

// Ассеты приватного релиза отдаются только по API и только с этим
// Accept: с обычным придёт JSON с описанием, а не файл.
export function manifestHeaders(token = distribution().token) {
  return apiHeaders(token, 'application/octet-stream');
}

export function packSource(config = distribution()) {
  if (config.repo?.includes('/')) {
    const [owner, repo] = config.repo.split('/');
    return { kind: 'github', owner, repo, tag: config.tag, token: config.token };
  }
  if (config.manifestUrl) return { kind: 'url', url: config.manifestUrl, token: config.token };
  return { kind: 'none' };
}

export async function loadManifest({ source = packSource(), fetchImpl = fetch } = {}) {
  if (source.kind === 'none') return null;

  if (source.kind === 'github') {
    const text = await fetchPackManifestText({ ...source, fetchImpl });
    return parseManifest(text);
  }

  return fetchManifest(source.url, { fetchImpl, headers: manifestHeaders(source.token) });
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
  source = packSource(),
  fetchImpl = fetch,
  ...rest
} = {}) {
  paths.ensureDirs();

  const config = readConfig();
  const manifest = await loadManifest({ source, fetchImpl });

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
  source = packSource(),
  fetchImpl = fetch,
  onUpdate = null,
  onError = null,
} = {}) {
  if (source.kind === 'none') return () => {};

  let stopped = false;
  let announced = knownVersion;

  const tick = async () => {
    if (stopped) return;
    try {
      const manifest = await loadManifest({ source, fetchImpl });
      if (manifest && manifest.packVersion > announced) {
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
