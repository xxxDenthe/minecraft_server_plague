// Новости пака: ассет news.json в том же приватном релизе. Текст
// пишется руками (спек, часть 2) — здесь только чтение, проверка
// и кэш, чтобы панель не пустела при пропавшей сети.

import fs from 'node:fs';
import path from 'node:path';

import * as paths from './paths.js';
import { releaseByTag, findAsset, assetUrl, apiHeaders } from './github.js';

export const NEWS_ASSET = 'news.json';

// Панель узкая: длинный заголовок в ней не переносится красиво,
// а длинный текст превращает новость в статью, которую не читают.
export const LIMITS = Object.freeze({ title: 60, body: 400, shown: 8 });

const KINDS = Object.freeze({ add: 'Добавлено', fix: 'Исправлено', info: 'Важно' });

function fail(message) {
  throw new Error(`новости: ${message}`);
}

const isText = (v) => typeof v === 'string' && v.trim() !== '';

export function parseNews(text) {
  let raw;
  try {
    raw = JSON.parse(text);
  } catch (err) {
    fail(`ответ не JSON (${err.message})`);
  }
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) fail('не объект');
  if (!Array.isArray(raw.items)) fail('items: не массив');

  const items = [];
  for (const [i, entry] of raw.items.entries()) {
    const at = `items[${i}]`;
    if (entry === null || typeof entry !== 'object' || Array.isArray(entry)) fail(`${at}: не объект`);

    // Черновик — рабочее состояние файла, а не ошибка: он просто
    // не доходит до игрока. Не пустить его в релиз — дело выкладки.
    if (entry.draft === true) continue;

    if (!isText(entry.id)) fail(`${at}.id: пустой или не строка`);
    if (!isText(entry.date) || !/^\d{4}-\d{2}-\d{2}$/.test(entry.date)) fail(`${at}.date: не вида 2026-09-09`);
    if (!isText(entry.kind) || !(entry.kind in KINDS)) fail(`${at}.kind: не add, fix или info`);
    if (!isText(entry.title)) fail(`${at}.title: пустой или не строка`);
    if (entry.title.length > LIMITS.title) fail(`${at}.title: длиннее ${LIMITS.title} знаков`);
    if (!isText(entry.body)) fail(`${at}.body: пустой или не строка`);
    if (entry.body.length > LIMITS.body) fail(`${at}.body: длиннее ${LIMITS.body} знаков`);

    items.push({
      id: entry.id,
      date: entry.date,
      kind: entry.kind,
      label: KINDS[entry.kind],
      title: entry.title,
      body: entry.body,
      pinned: entry.pinned === true,
    });
  }

  const pinned = items.filter((item) => item.pinned);
  // Две закреплённые записи означают, что важного нет ни одной.
  if (pinned.length > 1) fail(`закреплённых записей ${pinned.length}, можно одну`);

  return [...pinned, ...items.filter((item) => !item.pinned)];
}

export const cacheFile = () => path.join(paths.root(), 'news.json');

export function readCachedNews() {
  try {
    return parseNews(fs.readFileSync(cacheFile(), 'utf8'));
  } catch {
    return [];
  }
}

export function writeCachedNews(items) {
  const file = cacheFile();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  // Через временный файл: оборванная запись не должна превращать
  // кэш в мусор, который потом молча читается пустым.
  const temp = `${file}.tmp`;
  fs.writeFileSync(temp, `${JSON.stringify({ items }, null, 2)}\n`, 'utf8');
  fs.renameSync(temp, file);
  return items;
}

export async function fetchNews({ source, fetchImpl = fetch } = {}) {
  if (!source || source.kind !== 'github') return [];

  const { owner, repo, tag, token } = source;
  const release = await releaseByTag({ owner, repo, tag, token, fetchImpl });
  const asset = findAsset(release, NEWS_ASSET);

  const response = await fetchImpl(assetUrl({ owner, repo, assetId: asset.id }), {
    headers: apiHeaders(token, 'application/octet-stream'),
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`GitHub ответил ${response.status} на ${NEWS_ASSET}`);

  return parseNews(await response.text());
}

// Сеть, а при любой осечке — последнее известное. Пустая панель хуже
// вчерашней новости.
export async function loadNews({ source, fetchImpl = fetch } = {}) {
  try {
    const fresh = await fetchNews({ source, fetchImpl });
    const local = readCachedNews().filter((item) => item.id.startsWith('local-'));
    return writeCachedNews([...fresh, ...local].slice(0, LIMITS.shown));
  } catch {
    return readCachedNews();
  }
}

// Местная запись — например, о том, что пак только что обновился.
// В релиз она не уезжает и живёт до следующей такой же.
export function noteLocal({ id, date, kind, title, body }) {
  const rest = readCachedNews().filter((item) => item.id !== id);
  const item = { id, date, kind, label: KINDS[kind], title, body, pinned: false };
  return writeCachedNews([item, ...rest].slice(0, LIMITS.shown));
}
