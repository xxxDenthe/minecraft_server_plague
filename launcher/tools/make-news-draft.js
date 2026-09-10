#!/usr/bin/env node
// Черновик новостей: заготовки из коммитов с прошлой выкладки.
//
// Скрипт НЕ пишет текст для игрока — он только напоминает, о чём
// написать. Каждая заготовка помечена draft: true, и publish-news.js
// отказывается заливать ленту, пока пометка не снята руками.

import fsp from 'node:fs/promises';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const file = path.join(here, '..', 'news', 'news.json');
const repoRoot = path.join(here, '..', '..');

const git = (...args) => execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8' }).trim();

const today = new Date().toISOString().slice(0, 10);
const since = process.argv.includes('--since')
  ? process.argv[process.argv.indexOf('--since') + 1]
  : '7 days ago';

// Больше двух десятков заготовок за раз никто не перепишет, а лента
// из сотни «ЧЕРНОВИК: …» просто не читается. Нужны более старые —
// сузьте окно: --since '3 days ago'.
const MAX_DRAFTS = 20;

const subjects = git('log', `--since=${since}`, '--format=%s')
  .split('\n')
  .filter(Boolean)
  // Свои же служебные коммиты игроку неинтересны.
  .filter((s) => !/^(Заметк|Спек|План|Записк|docs)/i.test(s))
  .slice(0, MAX_DRAFTS);

const news = JSON.parse(await fsp.readFile(file, 'utf8'));
const known = new Set(news.items.map((item) => item.id));

let added = 0;
for (const [i, subject] of subjects.entries()) {
  const id = `draft-${today}-${i + 1}`;
  if (known.has(id)) continue;
  news.items.unshift({
    id,
    date: today,
    kind: 'add',
    title: subject.slice(0, 60),
    body: `ЧЕРНОВИК, переписать для игрока: ${subject}`.slice(0, 400),
    pinned: false,
    draft: true,
  });
  added += 1;
}

await fsp.writeFile(file, `${JSON.stringify(news, null, 2)}\n`, 'utf8');
console.log(`заготовок добавлено: ${added} (потолок ${MAX_DRAFTS}).`);
console.log(`Перепишите их в ${path.relative(repoRoot, file)} и снимите draft.`);
