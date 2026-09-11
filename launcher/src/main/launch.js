// Сборка команды запуска и сам запуск (спек, раздел 9).
// Подстановки берутся из профиля, а не из головы: список ${...}
// у Mojang и NeoForge разный и меняется от версии к версии.

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';

import * as paths from './paths.js';
import { ruleAllows, windowsLibraries } from './minecraft.js';
import { offlineUuid } from './offline.js';

export const LAUNCHER_NAME = 'LMPCLauncher';
export const LAUNCHER_VERSION = '0.1.0';

// Аргумент может быть строкой или объектом с правилами. Правила по
// features (демо-режим, своё разрешение) мы не включаем никогда,
// поэтому такие аргументы отбрасываются целиком.
export function selectArguments(list, platform = {}) {
  const out = [];

  for (const item of list ?? []) {
    if (typeof item === 'string') {
      out.push(item);
      continue;
    }
    if (item?.rules?.some((rule) => rule.features)) continue;
    if (!ruleAllows(item?.rules, platform)) continue;

    out.push(...(Array.isArray(item.value) ? item.value : [item.value]));
  }

  return out;
}

export function substitute(args, values) {
  return args.map((arg) =>
    Object.entries(values).reduce(
      (text, [key, value]) => text.replaceAll(`\${${key}}`, String(value)),
      arg
    )
  );
}

// Библиотеки NeoForge идут первыми: при совпадении координат должна
// побеждать его версия, иначе игра падает на несовпадении ASM.
export function buildClasspath(vanillaJson, forgeProfile, clientJar) {
  const entries = [];
  const seen = new Set();

  const add = (artifactPath) => {
    const key = artifactPath.toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);
    entries.push(path.join(paths.libraries(), ...artifactPath.split('/')));
  };

  const coordinate = (name) => name.split(':').slice(0, 2).join(':');
  const forgeCoordinates = new Set((forgeProfile.libraries ?? []).map((l) => coordinate(l.name)));

  for (const lib of forgeProfile.libraries ?? []) {
    if (lib.downloads?.artifact?.path) add(lib.downloads.artifact.path);
  }

  for (const lib of vanillaJson.libraries) {
    if (!ruleAllows(lib.rules)) continue;
    if (forgeCoordinates.has(coordinate(lib.name))) continue;
    if (lib.downloads?.artifact?.path) add(lib.downloads.artifact.path);
  }

  entries.push(clientJar);
  return entries;
}

export function buildCommand({
  vanillaJson,
  forgeProfile,
  clientJar,
  nickname,
  maxRamMb = 6144,
  minRamMb = null,
  extraJvmArgs = [],
  server = null,
  nativesDir = path.join(paths.root(), 'natives'),
}) {
  // По умолчанию — прежнее поведение: гигабайт или меньше, если
  // потолок ниже. Игрок может задать своё значение в настройках.
  const xms = Math.min(minRamMb ?? Math.min(1024, maxRamMb), maxRamMb);
  const classpath = buildClasspath(vanillaJson, forgeProfile, clientJar);

  const values = {
    natives_directory: nativesDir,
    launcher_name: LAUNCHER_NAME,
    launcher_version: LAUNCHER_VERSION,
    classpath: classpath.join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: paths.libraries(),
    version_name: vanillaJson.id,
    game_directory: paths.instance(),
    assets_root: paths.assets(),
    assets_index_name: vanillaJson.assetIndex.id,
    auth_player_name: nickname,
    auth_uuid: offlineUuid(nickname).replaceAll('-', ''),
    // В offline-режиме токен не проверяется никем, но пустым его
    // оставлять нельзя: игра спотыкается о пустой аргумент.
    auth_access_token: '0',
    clientid: '0',
    auth_xuid: '0',
    user_type: 'legacy',
    version_type: forgeProfile.type ?? vanillaJson.type,
  };

  const jvm = [
    ...selectArguments(vanillaJson.arguments?.jvm),
    ...selectArguments(forgeProfile.arguments?.jvm),
  ];

  const game = [
    ...selectArguments(vanillaJson.arguments?.game),
    ...selectArguments(forgeProfile.arguments?.game),
  ];

  const args = [
    `-Xmx${maxRamMb}M`,
    `-Xms${xms}M`,
    ...extraJvmArgs,
    ...substitute(jvm, values),
    forgeProfile.mainClass ?? vanillaJson.mainClass,
    ...substitute(game, values),
  ];

  if (server) args.push('--quickPlayMultiplayer', `${server.host}:${server.port}`);

  return { args, classpath, values };
}

// Настройки первого запуска. Ресурспаки лежат в паке, а какие из них
// включены и в каком порядке — только в options.txt, а он у нас
// в PROTECTED и не раздаётся. Без этого игрок получал бы девять
// архивов в папке и ни одного включённого.
//
// Образец лежит в самом паке, а не здесь: порядок паков меняется вместе
// с паком, и пересобирать установщик ради него не нужно.
export const OPTIONS_SEED = 'config/lmpc-default-options.txt';

const PACKS_KEY = 'resourcePacks:';

const packsLine = (text) =>
  text.split('\n').find((line) => line.startsWith(PACKS_KEY)) ?? null;

// Порядок задаёт пак, но записи, которых в образце нет, остаются:
// часть паков добавляют сами моды (generated/…, mod/…), и вычёркивать
// их из чужого списка лаунчеру не за чем.
export function mergePacks(seedLine, playerLine) {
  const parse = (line) => JSON.parse(line.slice(PACKS_KEY.length));
  const seed = parse(seedLine);
  const extra = parse(playerLine).filter((pack) => !seed.includes(pack));
  return PACKS_KEY + JSON.stringify([...seed, ...extra]);
}

// Чужой options.txt правится ровно в одной строке — списке включённых
// ресурспаков. Всё остальное (клавиши, громкость, графика) принадлежит
// игроку. Без этого девять архивов лежат в resourcepacks/ невключёнными
// у всех, кто запускался до появления образца, а новый пак в обновлении
// не включается никогда.
function withSeedPacks(player, seedLine) {
  const mine = packsLine(player);
  if (!mine) return `${player}${player.endsWith('\n') ? '' : '\n'}${seedLine}\n`;

  try {
    const merged = mergePacks(seedLine, mine);
    return player.replace(mine, () => merged); // функция: в именах паков бывает $
  } catch {
    return player.replace(mine, () => seedLine); // список игрока не читается — ставим наш
  }
}

// При первом запуске образец кладётся целиком, дальше — только паки.
export async function seedOptions(instanceDir) {
  const optionsTxt = path.join(instanceDir, 'options.txt');

  // Образца в паке нет — ставим хотя бы графику «Ультра» (Fabulous):
  // lmpc_shade убирает небесный купол, и в «Детально» (Fancy) на его
  // месте чёрная дыра.
  const seed = path.join(instanceDir, OPTIONS_SEED);
  const text = fs.existsSync(seed)
    ? await fsp.readFile(seed, 'utf8')
    : 'graphicsMode:2\n';

  if (!fs.existsSync(optionsTxt)) {
    await fsp.writeFile(optionsTxt, text, 'utf8');
    return text;
  }

  const seedLine = packsLine(text);
  if (!seedLine) return null;

  const player = await fsp.readFile(optionsTxt, 'utf8');
  const fixed = withSeedPacks(player, seedLine);
  if (fixed === player) return null;

  await fsp.writeFile(optionsTxt, fixed, 'utf8');
  return fixed;
}

export async function launchGame({
  javaExe = paths.javaExe(),
  vanillaJson,
  forgeProfile,
  clientJar,
  nickname,
  maxRamMb = 6144,
  minRamMb = null,
  extraJvmArgs = [],
  server = null,
  onLine = null,
}) {
  const { args } = buildCommand({
    vanillaJson,
    forgeProfile,
    clientJar,
    nickname,
    maxRamMb,
    minRamMb,
    extraJvmArgs,
    server,
  });

  await fsp.mkdir(paths.instance(), { recursive: true });
  await fsp.mkdir(path.join(paths.root(), 'natives'), { recursive: true });

  await seedOptions(paths.instance());

  // Лог пишется на диск целиком: разбор чужого краша не должен
  // превращаться в переписку «пришли скриншот».
  const logFile = path.join(paths.root(), 'game.log');
  const log = fs.createWriteStream(logFile, { flags: 'w' });

  const child = spawn(javaExe, args, {
    cwd: paths.instance(),
    windowsHide: false,
  });

  let tail = '';
  const feed = (chunk) => {
    log.write(chunk);
    tail += chunk.toString('utf8');

    const lines = tail.split('\n');
    tail = lines.pop() ?? '';
    for (const line of lines) onLine?.(line.trimEnd());
  };

  child.stdout.on('data', feed);
  child.stderr.on('data', feed);
  child.on('close', () => log.end());

  return { process: child, logFile, args };
}
