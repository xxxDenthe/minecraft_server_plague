#!/usr/bin/env node
// Выкладывание пака в приватный релиз GitHub и сборка манифеста
// по результатам загрузки.
//
//   node tools/publish-pack.js --repo xxxDenthe/minecraft_server_plague \
//                             --tag pack --token ghp_... [--dir pack-build] [--dry-run]
//
// Почему манифест собирается здесь, а не отдельным скриптом заранее:
// адресом файла служит id ассета, а id известен только после загрузки.
// Имена ассетов для этого не годятся — GitHub переименовывает файлы
// со спецсимволами, а «+» есть у двадцати одного мода пака.
//
// Повторный запуск заливает только изменившиеся файлы, остальные
// остаются в релизе как есть. Правку баланса посреди сессии это
// превращает в минуту работы вместо перезаливки трёхсот файлов.

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

import { parseManifest } from '../src/main/manifest.js';
import { PROTECTED } from '../src/main/sync.js';
import { apiHeaders, assetUrl, releaseByTag } from '../src/main/github.js';

const API = 'https://api.github.com';

const DEFAULTS = {
  dir: 'pack-build',
  tag: 'pack',
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  managed: 'mods,config,defaultconfigs,kubejs,resourcepacks,shaderpacks',
  'max-ram': '6144',
};

function parseArgs(argv) {
  const args = { ...DEFAULTS, 'dry-run': false };

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

  if (!args.repo?.includes('/')) throw new Error('нужен --repo вида владелец/репозиторий');
  if (!args.token && !args['dry-run']) throw new Error('нужен --token с правом Contents: read and write');

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

// Ассеты лежат плоским списком, папок в нём нет. Путь кодируем в имя
// не ради лаунчера — он берёт путь из манифеста, — а чтобы человек,
// открывший релиз, понимал, что где лежит.
const assetName = (relative) => relative.replaceAll('/', '__');

async function github(url, { token, method = 'GET', accept, body, contentType } = {}) {
  const headers = apiHeaders(token, accept);
  if (contentType) headers['Content-Type'] = contentType;

  const response = await fetch(url, { method, headers, body, redirect: 'follow' });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`GitHub ответил ${response.status} на ${method} ${url}: ${text.slice(0, 300)}`);
  }

  return response;
}

async function uploadAsset({ release, repo, token, name, file, existing }) {
  // Перезалить ассет с тем же именем нельзя — сначала удаляем старый.
  if (existing) {
    await github(`${API}/repos/${repo}/releases/assets/${existing.id}`, { token, method: 'DELETE' });
  }

  const uploadUrl = release.upload_url.replace(/\{\?[^}]*\}$/, '');
  const body = await fsp.readFile(file);

  // Имя обязательно кодировать: незакодированный «+» GitHub принимает
  // за пробел и заменяет точкой. Мы всё равно адресуемся по id, но
  // человеку в релизе лучше видеть настоящие имена.
  const response = await github(`${uploadUrl}?name=${encodeURIComponent(name)}`, {
    token,
    method: 'POST',
    body,
    contentType: 'application/octet-stream',
  });

  return response.json();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const [owner, repoName] = args.repo.split('/');
  const packDir = path.resolve(args.dir);
  const managedDirs = args.managed.split(',').map((d) => d.trim()).filter(Boolean);

  // 1. Что вообще раздаём
  const local = [];
  for (const dir of managedDirs) {
    for (const relative of await walk(path.join(packDir, dir), packDir)) {
      // Пользовательские файлы в пак не попадают: иначе лаунчер будет
      // затирать игроку настройки при каждом запуске.
      if (relative.split('/').some((part) => PROTECTED.includes(part))) continue;

      const full = path.join(packDir, relative);
      local.push({
        path: relative,
        sha256: await sha256(full),
        size: (await fsp.stat(full)).size,
        file: full,
      });
    }
  }

  if (local.length === 0) {
    throw new Error(`в ${packDir} не нашлось ни одного файла — проверьте --dir и --managed`);
  }

  const bytes = local.reduce((sum, f) => sum + f.size, 0);
  console.log(`в паке ${local.length} файлов, ${(bytes / 1024 / 1024).toFixed(0)} МБ`);

  if (args['dry-run']) {
    console.log('--dry-run: ничего не загружаю, показываю первые десять имён ассетов');
    for (const f of local.slice(0, 10)) console.log(`  ${f.path}  →  ${assetName(f.path)}`);
    return;
  }

  // 2. Релиз должен уже существовать: создавать его и решать, каким
  // он будет, — не дело скрипта.
  const release = await releaseByTag({ owner, repo: repoName, tag: args.tag, token: args.token });
  const existing = new Map((release.assets ?? []).map((a) => [a.name, a]));

  // 3. Заливаем только изменившееся. GitHub отдаёт digest не всегда;
  // когда его нет, сверяем размер — имя ассета включает версию мода,
  // так что молчаливая подмена содержимого при том же размере
  // означала бы, что кто-то переложил файл руками.
  const manifestFiles = [];
  let uploaded = 0;
  let kept = 0;

  for (const file of local) {
    const name = assetName(file.path);
    const already = existing.get(name);
    const digest = already?.digest ?? '';
    const same = already && (digest ? digest === `sha256:${file.sha256}` : already.size === file.size);

    const asset = same
      ? already
      : await uploadAsset({
          release,
          repo: args.repo,
          token: args.token,
          name,
          file: file.file,
          existing: already,
        });

    if (same) {
      kept += 1;
    } else {
      uploaded += 1;
      console.log(`залито: ${file.path}`);
    }

    manifestFiles.push({
      path: file.path,
      sha256: file.sha256,
      size: file.size,
      url: assetUrl({ owner, repo: repoName, assetId: asset.id }),
    });
  }

  console.log(`загружено ${uploaded}, оставлено без изменений ${kept}`);

  // 4. Манифест — последним, когда все id известны
  const previous = existing.get('pack.json');
  let packVersion = 1;

  if (previous) {
    try {
      const response = await github(`${API}/repos/${args.repo}/releases/assets/${previous.id}`, {
        token: args.token,
        accept: 'application/octet-stream',
      });
      packVersion = parseManifest(await response.text()).packVersion + 1;
    } catch {
      // Нечитаемый старый манифест — начинаем с единицы, хуже не будет.
    }
  }

  const manifest = {
    packVersion,
    minecraft: args.minecraft,
    neoforge: args.neoforge,
    java: { major: 21 },
    launch: {
      maxRamMb: Number.parseInt(args['max-ram'], 10),
      jvmArgs: ['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50'],
    },
    managedDirs,
    files: manifestFiles,
  };

  if (args.server) {
    const [host, port = '25565'] = args.server.split(':');
    manifest.server = { host, port: Number.parseInt(port, 10) };
  }

  const text = `${JSON.stringify(manifest, null, 2)}\n`;

  // Манифест, который не проходит нашу же валидацию, не должен
  // доехать до игрока.
  parseManifest(text);

  const local_manifest = path.join(path.dirname(packDir), 'pack.json');
  await fsp.writeFile(local_manifest, text, 'utf8');

  await uploadAsset({
    release,
    repo: args.repo,
    token: args.token,
    name: 'pack.json',
    file: local_manifest,
    existing: previous,
  });

  console.log(`манифест выложен: версия пака ${packVersion}, файлов ${manifestFiles.length}`);
  console.log(`лаунчер собирать с PLAGUE_REPO=${args.repo} PLAGUE_RELEASE_TAG=${args.tag}`);
}

main().catch((err) => {
  console.error(`раздача не удалась: ${err.message}`);
  process.exitCode = 1;
});
