#!/usr/bin/env node
// Выкладывание пака в приватный релиз GitHub.
//
//   npm run publish
//
// Настройки — в launcher/publish.json, файл не версионируется, в нём
// токен. Ключи командной строки его перебивают, это удобно при отладке:
//
//   node tools/publish-pack.js --tag pack-test --dry-run
//
// Пак едет архивами, по одному на игровую папку: в релизе семь файлов
// вместо трёхсот, и страницу можно читать глазами. Внутри архива путь
// от корня инстанса, поэтому лаунчер распаковывает его как есть.
//
// Повторный запуск заливает только изменившиеся папки. Что изменилось,
// решает contentId из прошлого манифеста, а не хеш архива: побайтовой
// воспроизводимости zip tar не обещает.

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

import { parseManifest } from '../src/main/manifest.js';
import { PROTECTED } from '../src/main/sync.js';
import { zip } from '../src/main/archive.js';
import { apiHeaders, assetUrl, releaseByTag, checkToken } from '../src/main/github.js';
import { contentIdOf, planUpload } from './pack.js';

const API = 'https://api.github.com';
const here = path.dirname(fileURLToPath(import.meta.url));

export const SETTINGS_FILE = path.join(here, '..', 'publish.json');

const DEFAULTS = {
  dir: 'pack-build',
  tag: 'pack',
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  managed: 'mods,config,defaultconfigs,kubejs,resourcepacks,shaderpacks',
  'max-ram': '6144',
};

const HELP =
  `нет ${SETTINGS_FILE}.\n` +
  '  Создайте его — это единственная ручная настройка выкладки:\n' +
  '  {\n' +
  '    "repo": "xxxDenthe/minecraft_server_plague",\n' +
  '    "tag": "pack",\n' +
  '    "token": "github_pat_... с правом Contents: Read and write",\n' +
  '    "dir": "pack-build",\n' +
  '    "server": "хост:25565",\n' +
  '    "max-ram": "6144"\n' +
  '  }';

function fromFile() {
  try {
    const raw = JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf8'));
    return raw && typeof raw === 'object' ? raw : {};
  } catch {
    return {};
  }
}

function parseArgs(argv) {
  const args = { ...DEFAULTS, ...fromFile(), 'dry-run': false };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) throw new Error(`не понимаю аргумент «${token}»`);

    const key = token.slice(2);
    if (key === 'dry-run') {
      args[key] = true;
      continue;
    }

    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`у ключа --${key} нет значения`);

    args[key] = value;
    i += 1;
  }

  if (!args.repo?.includes('/')) throw new Error(`нужен репозиторий вида владелец/имя.\n${HELP}`);
  if (!args.token && !args['dry-run']) throw new Error(`нужен токен.\n${HELP}`);
  if (args.token) checkToken(args.token);

  return args;
}

async function sha256(file) {
  const hash = createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

async function walk(dir, base) {
  let entries;
  try {
    entries = await fsp.readdir(dir, { withFileTypes: true });
  } catch (err) {
    if (err.code === 'ENOENT') return [];
    throw err;
  }

  const out = [];
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(full, base)));
    else out.push(path.relative(base, full).split(path.sep).join('/'));
  }
  return out;
}

async function github(url, { token, method = 'GET', accept, body, contentType } = {}) {
  const headers = apiHeaders(token, accept);
  if (contentType) headers['Content-Type'] = contentType;

  const response = await fetch(url, { method, headers, body, redirect: 'follow' });

  if (!response.ok) {
    const text = await response.text().catch(() => '');

    // «Resource not accessible by personal access token» — самая
    // частая осечка первой раздачи, и по тексту не видно, что чинить.
    if (response.status === 403 && text.includes('not accessible')) {
      throw new Error(
        'токену не хватает прав на запись.\n' +
          '  Нужно Permissions → Repository permissions → Contents: Read and write.\n' +
          '  Права меняются на лету — поправьте их на GitHub и запустите заново,\n' +
          '  новый токен делать не нужно. Уже залитые файлы повторно не поедут.'
      );
    }

    throw new Error(`GitHub ответил ${response.status} на ${method} ${url}: ${text.slice(0, 300)}`);
  }

  return response;
}

// Релиза нет — создаём. Раньше скрипт здесь падал и отправлял человека
// в браузер; это была половина ручной возни при каждой первой выкладке.
async function ensureRelease({ owner, repo, tag, token }) {
  const existing = await releaseByTag({ owner, repo, tag, token, orNull: true });
  if (existing) return existing;

  console.log(`релиза «${tag}» нет — создаю`);

  const response = await github(`${API}/repos/${owner}/${repo}/releases`, {
    token,
    method: 'POST',
    contentType: 'application/json',
    body: JSON.stringify({
      tag_name: tag,
      name: `Модпак LMPC (${tag})`,
      body: 'Архивы пака и манифест. Файлы кладёт и обновляет `npm run publish`.',
      draft: false,
      prerelease: false,
    }),
  });

  return response.json();
}

async function uploadAsset({ release, repo, token, name, file, existing }) {
  // Перезалить ассет с тем же именем нельзя — сначала удаляем старый.
  if (existing) {
    await github(`${API}/repos/${repo}/releases/assets/${existing.id}`, { token, method: 'DELETE' });
  }

  const uploadUrl = release.upload_url.replace(/\{\?[^}]*\}$/, '');
  const body = await fsp.readFile(file);

  const response = await github(`${uploadUrl}?name=${encodeURIComponent(name)}`, {
    token,
    method: 'POST',
    body,
    contentType: 'application/octet-stream',
  });

  return response.json();
}

async function readPreviousManifest({ repo, token, asset }) {
  if (!asset) return null;

  try {
    const response = await github(`${API}/repos/${repo}/releases/assets/${asset.id}`, {
      token,
      accept: 'application/octet-stream',
    });
    return parseManifest(await response.text());
  } catch {
    // Нечитаемый или устаревший манифест — собираем всё заново.
    // Хуже лишней перезаливки только тихо разошедшийся пак.
    return null;
  }
}

const mb = (bytes) => (bytes / 1024 / 1024).toFixed(0);

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const [owner, repoName] = args.repo.split('/');
  const packDir = path.resolve(args.dir);
  const cacheDir = path.join(packDir, '..', 'pack-archives');
  const managedDirs = args.managed.split(',').map((d) => d.trim()).filter(Boolean);

  // 1. Что вообще раздаём
  const dirs = [];
  for (const dir of managedDirs) {
    const files = [];

    for (const relative of await walk(path.join(packDir, dir), packDir)) {
      // Пользовательские файлы в пак не попадают: иначе лаунчер будет
      // затирать игроку настройки при каждом запуске.
      if (relative.split('/').some((part) => PROTECTED.includes(part))) continue;

      const full = path.join(packDir, relative);
      files.push({ path: relative, sha256: await sha256(full), size: (await fsp.stat(full)).size });
    }

    if (files.length === 0) continue;
    dirs.push({ dir, files, contentId: contentIdOf(files) });
  }

  if (dirs.length === 0) {
    throw new Error(`в ${packDir} не нашлось ни одного файла — проверьте dir и managed`);
  }

  for (const d of dirs) {
    const bytes = d.files.reduce((sum, f) => sum + f.size, 0);
    console.log(`  ${d.dir}: ${d.files.length} файлов, ${mb(bytes)} МБ`);
  }

  if (args['dry-run']) {
    console.log('--dry-run: ничего не загружаю');
    return;
  }

  // 2. Релиз и прошлый манифест
  const release = await ensureRelease({ owner, repo: repoName, tag: args.tag, token: args.token });
  const existing = new Map((release.assets ?? []).map((a) => [a.name, a]));
  const previous = await readPreviousManifest({
    repo: args.repo,
    token: args.token,
    asset: existing.get('pack.json'),
  });

  // 3. Собираем и заливаем только изменившиеся папки
  const { reuse, build } = planUpload(dirs, previous);
  const archives = [...reuse];

  for (const d of build) {
    const file = path.join(cacheDir, `${d.dir}.zip`);

    await zip({ sourceDir: packDir, entries: d.files.map((f) => f.path), archive: file });

    const size = (await fsp.stat(file)).size;
    console.log(`жму и заливаю ${d.dir}.zip, ${mb(size)} МБ`);

    const asset = await uploadAsset({
      release,
      repo: args.repo,
      token: args.token,
      name: `${d.dir}.zip`,
      file,
      existing: existing.get(`${d.dir}.zip`),
    });

    archives.push({
      dir: d.dir,
      sha256: await sha256(file),
      contentId: d.contentId,
      size,
      url: assetUrl({ owner, repo: repoName, assetId: asset.id }),
    });

    await fsp.rm(file, { force: true });
  }

  console.log(`залито папок ${build.length}, оставлено без изменений ${reuse.length}`);

  // 4. Ассеты папок, которых в паке больше нет: релиз не должен
  // хранить то, что лаунчер уже не спросит.
  const wanted = new Set(archives.map((a) => `${a.dir}.zip`));
  for (const [name, asset] of existing) {
    if (name === 'pack.json' || wanted.has(name) || !name.endsWith('.zip')) continue;
    await github(`${API}/repos/${args.repo}/releases/assets/${asset.id}`, { token: args.token, method: 'DELETE' });
    console.log(`убран из релиза: ${name}`);
  }

  // 5. Манифест — последним, когда все id известны
  const manifest = {
    packVersion: (previous?.packVersion ?? 0) + 1,
    minecraft: args.minecraft,
    neoforge: args.neoforge,
    java: { major: 21 },
    launch: {
      maxRamMb: Number.parseInt(args['max-ram'], 10),
      jvmArgs: ['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50'],
    },
    managedDirs,
    archives: archives.sort((a, b) => a.dir.localeCompare(b.dir)),
  };

  if (args.server) {
    const [host, port = '25565'] = args.server.split(':');
    manifest.server = { host, port: Number.parseInt(port, 10) };
  }

  const text = `${JSON.stringify(manifest, null, 2)}\n`;

  // Манифест, который не проходит нашу же валидацию, не должен
  // доехать до игрока.
  parseManifest(text);

  const localManifest = path.join(cacheDir, 'pack.json');
  await fsp.mkdir(cacheDir, { recursive: true });
  await fsp.writeFile(localManifest, text, 'utf8');

  await uploadAsset({
    release,
    repo: args.repo,
    token: args.token,
    name: 'pack.json',
    file: localManifest,
    existing: existing.get('pack.json'),
  });

  console.log(`манифест выложен: версия пака ${manifest.packVersion}, архивов ${archives.length}`);
  console.log(`страница релиза: ${release.html_url}`);
}

main().catch((err) => {
  console.error(`раздача не удалась: ${err.message}`);
  process.exitCode = 1;
});
