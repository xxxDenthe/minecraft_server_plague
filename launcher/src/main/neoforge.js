// Установка NeoForge официальным установщиком (спек, раздел 8, шаг 3).
// Своего разбора формата не пишем: он меняется от версии к версии,
// а установщик поддерживают его авторы.

import fsp from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import * as paths from './paths.js';
import { downloadFile } from './download.js';
import { progressEvent, STAGES } from './progress.js';

const run = promisify(execFile);

const INSTALLER_URL = (version) =>
  `https://maven.neoforged.net/releases/net/neoforged/neoforge/${version}/` +
  `neoforge-${version}-installer.jar`;

export const versionId = (version) => `neoforge-${version}`;
export const profilePath = (version) =>
  path.join(paths.versions(), versionId(version), `${versionId(version)}.json`);

// Установщик считает целевую папку папкой официального лаунчера и ищет
// в ней launcher_profiles.json. Файл ему нужен только чтобы дописать
// туда профиль; пустого объекта достаточно, а мы этот файл не читаем.
async function ensureLauncherProfiles(root) {
  const file = path.join(root, 'launcher_profiles.json');
  try {
    await fsp.access(file);
  } catch {
    await fsp.mkdir(root, { recursive: true });
    await fsp.writeFile(file, JSON.stringify({ profiles: {}, version: 3 }, null, 2), 'utf8');
  }
  return file;
}

export async function ensureNeoForge(
  version = '21.1.249',
  { javaExe = paths.javaConsoleExe(), onProgress = null, ...rest } = {}
) {
  const profile = profilePath(version);

  // Профиль на месте — установщик уже отработал. Он идёт полторы минуты
  // и качает библиотеки заново, повторять его на каждом запуске незачем.
  try {
    await fsp.access(profile);
    return profile;
  } catch {
    // Ставим.
  }

  onProgress?.(progressEvent({ stage: STAGES.NEOFORGE, message: `скачиваю установщик ${version}` }));

  const installer = path.join(paths.root(), 'cache', `neoforge-${version}-installer.jar`);
  await downloadFile({ ...rest, url: INSTALLER_URL(version), dest: installer });

  await ensureLauncherProfiles(paths.root());

  onProgress?.(progressEvent({ stage: STAGES.NEOFORGE, message: `ставлю NeoForge ${version}` }));

  // Запускаем нашей JRE, а не системной: на машине игрока с Java 8
  // установка упала бы непонятно.
  const { stdout, stderr } = await run(
    javaExe,
    ['-jar', installer, '--install-client', paths.root()],
    { cwd: path.dirname(installer), maxBuffer: 16 * 1024 * 1024 }
  );

  try {
    await fsp.access(profile);
  } catch {
    throw new Error(
      `установщик NeoForge отработал, но профиля ${profile} нет:\n${stdout}\n${stderr}`
    );
  }

  // Лог установщика игроку не нужен, а место занимает.
  await fsp.rm(path.join(path.dirname(installer), `neoforge-${version}-installer.jar.log`), {
    force: true,
  });
  await fsp.rm(installer, { force: true });

  onProgress?.(progressEvent({ stage: STAGES.NEOFORGE, message: `NeoForge ${version} установлен` }));

  return profile;
}

export async function readProfile(version = '21.1.249') {
  return JSON.parse(await fsp.readFile(profilePath(version), 'utf8'));
}
