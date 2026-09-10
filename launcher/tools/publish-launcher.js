#!/usr/bin/env node
// Заливка собранного установщика лаунчера в тот же приватный релиз,
// что и модпак (тег pack). Держим рядом с publish-pack.js, потому что
// друг на втором ПК берёт оттуда и пак, и сам лаунчер.
//
//   npm run dist
//   node tools/publish-launcher.js --repo xxxDenthe/minecraft_server_plague \
//        --tag pack --token ghp_... [--file dist/LMPC-Launcher-0.1.0.exe]
//
// publish-pack.js этот ассет не трогает: он управляет только папками
// mods/ и CustomSkinLoader/. Повторный запуск заменяет ассет.

import fsp from 'node:fs/promises';
import path from 'node:path';

import { apiHeaders, releaseByTag, checkToken } from '../src/main/github.js';
import { sha256OfFile } from '../src/main/download.js';
import { buildLauncherRelease, RELEASE_ASSET } from '../src/main/selfupdate.js';

const API = 'https://api.github.com';

const pkg = JSON.parse(
  await fsp.readFile(new URL('../package.json', import.meta.url), 'utf8')
);

function parseArgs(argv) {
  const args = { tag: 'pack', file: `dist/LMPC-Launcher-${pkg.version}.exe` };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i].replace(/^--/, '');
    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`у --${key} нет значения`);
    args[key] = value;
    i += 1;
  }
  if (!args.repo?.includes('/')) throw new Error('нужен --repo вида владелец/репозиторий');
  if (!args.token) throw new Error('нужен --token с правом Contents: read and write');
  checkToken(args.token);
  return args;
}

async function uploadAsset({ release, owner, repo, token, name, body, contentType }) {
  const existing = (release.assets ?? []).find((a) => a.name === name);
  if (existing) {
    const del = await fetch(`${API}/repos/${owner}/${repo}/releases/assets/${existing.id}`, {
      method: 'DELETE',
      headers: apiHeaders(token),
    });
    if (!del.ok) throw new Error(`не удалить старый ассет ${name}: ${del.status}`);
    console.log(`старый ${name} удалён`);
  }

  const uploadUrl =
    release.upload_url.replace(/\{\?[^}]*\}$/, '') + `?name=${encodeURIComponent(name)}`;
  const res = await fetch(uploadUrl, {
    method: 'POST',
    headers: { ...apiHeaders(token), 'Content-Type': contentType },
    body,
  });
  if (!res.ok) throw new Error(`GitHub ответил ${res.status} на ${name}: ${(await res.text()).slice(0, 300)}`);
  return res.json();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [owner, repo] = args.repo.split('/');
  const file = path.resolve(args.file);
  const name = path.basename(file);

  const stat = await fsp.stat(file).catch(() => null);
  if (!stat) throw new Error(`файла нет: ${file} — сначала npm run dist`);

  const release = await releaseByTag({ owner, repo, tag: args.tag, token: args.token });

  console.log(`заливаю ${name} (${(stat.size / 1048576).toFixed(0)} МБ)…`);

  // Буфером, а не потоком: эндпоинту GitHub нужен Content-Length,
  // который поток не выставляет («Bad Content-Length»).
  const asset = await uploadAsset({
    release,
    owner,
    repo,
    token: args.token,
    name,
    body: await fsp.readFile(file),
    contentType: 'application/octet-stream',
  });
  console.log(`готово: ${asset.name}, ${asset.size} байт`);

  // Версия, размер и хеш считаются здесь, а не вводятся руками:
  // расхождение хеша с файлом означает, что обновиться не сможет
  // ни один игрок, а заметно это станет далеко не сразу.
  const descriptor = buildLauncherRelease({
    version: pkg.version,
    file: name,
    size: stat.size,
    sha256: await sha256OfFile(file),
    notes: args.notes ?? '',
  });

  await uploadAsset({
    release,
    owner,
    repo,
    token: args.token,
    name: RELEASE_ASSET,
    body: `${JSON.stringify(descriptor, null, 2)}\n`,
    contentType: 'application/json',
  });
  console.log(`${RELEASE_ASSET} обновлён: версия ${descriptor.version}`);

  console.log(`скачать (в браузере, залогинившись): ${release.html_url}`);
}

main().catch((err) => {
  console.error(`заливка лаунчера не удалась: ${err.message}`);
  process.exitCode = 1;
});
