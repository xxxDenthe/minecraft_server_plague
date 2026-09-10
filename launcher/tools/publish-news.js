#!/usr/bin/env node
// Заливка ленты новостей в тот же приватный релиз, что и пак.
//
//   node tools/publish-news.js --repo xxxDenthe/minecraft_server_plague \
//        --tag pack --token ghp_...
//
// Пак и лаунчер этот ассет не трогают: новость правится и выкладывается
// одной командой, без пересборки чего бы то ни было.

import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { apiHeaders, releaseByTag, checkToken } from '../src/main/github.js';
import { parseNews, NEWS_ASSET, LIMITS } from '../src/main/news.js';

const API = 'https://api.github.com';
const here = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = { tag: 'pack' };
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [owner, repo] = args.repo.split('/');
  const file = path.join(here, '..', 'news', 'news.json');

  const text = await fsp.readFile(file, 'utf8');
  const raw = JSON.parse(text);

  // Черновик не уезжает игрокам: технический текст из коммитов — это
  // ровно то, чего в новостях быть не должно.
  const drafts = (raw.items ?? []).filter((item) => item.draft === true);
  if (drafts.length > 0) {
    // Списком в сотню строк ошибка становится нечитаемой: показываем
    // первые несколько, остальные видно в самом файле.
    const shown = drafts.slice(0, 5).map((d) => d.id).join(', ');
    const names = drafts.length > 5 ? `${shown} и ещё ${drafts.length - 5}` : shown;
    throw new Error(
      `в ленте ${drafts.length} черновик(ов): ${names}.\n` +
        '  Перепишите их для игрока и уберите "draft": true.'
    );
  }

  // Тот же разбор, что и в лаунчере: длины, виды, одна закреплённая.
  const items = parseNews(text);
  if (items.length === 0) throw new Error('лента пустая — заливать нечего');
  console.log(`записей: ${items.length}, показывается до ${LIMITS.shown}`);

  const release = await releaseByTag({ owner, repo, tag: args.tag, token: args.token });
  const existing = (release.assets ?? []).find((a) => a.name === NEWS_ASSET);
  if (existing) {
    const del = await fetch(`${API}/repos/${owner}/${repo}/releases/assets/${existing.id}`, {
      method: 'DELETE',
      headers: apiHeaders(args.token),
    });
    if (!del.ok) throw new Error(`не удалить старый ${NEWS_ASSET}: ${del.status}`);
  }

  const uploadUrl =
    release.upload_url.replace(/\{\?[^}]*\}$/, '') + `?name=${encodeURIComponent(NEWS_ASSET)}`;
  const res = await fetch(uploadUrl, {
    method: 'POST',
    headers: { ...apiHeaders(args.token), 'Content-Type': 'application/json' },
    body: text,
  });
  if (!res.ok) throw new Error(`GitHub ответил ${res.status}: ${(await res.text()).slice(0, 300)}`);

  console.log(`${NEWS_ASSET} обновлён в релизе «${args.tag}»`);
}

main().catch((err) => {
  console.error(`выкладка новостей не удалась: ${err.message}`);
  process.exitCode = 1;
});
