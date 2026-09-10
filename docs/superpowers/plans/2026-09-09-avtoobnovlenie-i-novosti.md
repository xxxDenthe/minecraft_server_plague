# Автообновление лаунчера и новости — план

> **Для агентов:** исполнять по задачам через `superpowers:executing-plans`.
> Шаги отмечаются галочками.

**Цель:** лаунчер обновляет сам себя из приватного релиза и показывает
игроку человеческие новости о паке.

**Архитектура:** оба механизма ложатся на уже работающую раздачу через
приватный релиз GitHub. Два новых модуля основного процесса
(`selfupdate.js`, `news.js`) читают свои ассеты тем же кодом, что
и манифест пака (`github.js` + `download.js`), и не знают про Electron.
Окно получает новости через IPC, апдейтер работает до показа экрана.

**Стек:** Node 22 + Electron 44, vitest, без новых зависимостей.

**Спек:** `docs/superpowers/specs/2026-09-09-avtoobnovlenie-i-novosti-design.md`

## Общие ограничения

- **Никаких новых зависимостей в `package.json`.** `electron-updater`
  отклонён владельцем.
- **`electron` импортирует только `src/main/index.js`** — это стережёт
  `test/purity.test.js`. Новые модули основного процесса — обычный Node.
- **Весь текст, видимый игроку, — по-русски**, без сленга, без имён
  классов и версий модов, без спойлеров по лору.
- **Имя ассета с версией лаунчера — `launcher-release.json`.** Имя
  `launcher.json` занято конфигом игрока (`paths.configFile()`).
- **Ассет новостей — `news.json`**, тег релиза — `pack` (тот же, что
  у модпака).
- Ограничения записи новости: `title` ≤ 60 знаков, `body` ≤ 400 знаков,
  закреплённая (`pinned`) запись не больше одной.
- Тесты запускаются из `launcher/`: `npm test`.
- Цвета в интерфейсе — только существующие переменные `style.css`.

---

### Задача 1: Разбор описания релиза лаунчера и сравнение версий

**Файлы:**
- Создать: `launcher/src/main/selfupdate.js`
- Тест: `launcher/test/selfupdate.test.js`

**Интерфейсы:**
- Использует: `fail`-стиль проверок из `manifest.js` (образец).
- Даёт: `parseLauncherRelease(text) -> { version, file, size, sha256, notes }`,
  `isNewer(remoteVersion, localVersion) -> boolean`,
  `buildLauncherRelease({ version, file, size, sha256, notes }) -> object`.

- [x] **Шаг 1: Написать падающий тест**

```js
// launcher/test/selfupdate.test.js
import { describe, it, expect } from 'vitest';
import { parseLauncherRelease, isNewer, buildLauncherRelease } from '../src/main/selfupdate.js';

const good = JSON.stringify({
  version: '0.2.0',
  file: 'LMPC-Launcher-0.2.0.exe',
  size: 78123456,
  sha256: 'a'.repeat(64),
});

describe('разбор описания релиза', () => {
  it('читает нормальное описание', () => {
    expect(parseLauncherRelease(good).version).toBe('0.2.0');
  });

  it('не пускает путь вместо имени файла', () => {
    const bad = JSON.stringify({ ...JSON.parse(good), file: '../evil.exe' });
    expect(() => parseLauncherRelease(bad)).toThrow(/file/);
  });

  it('не пускает короткий хеш', () => {
    const bad = JSON.stringify({ ...JSON.parse(good), sha256: 'abc' });
    expect(() => parseLauncherRelease(bad)).toThrow(/sha256/);
  });

  it('требует версию из трёх чисел', () => {
    const bad = JSON.stringify({ ...JSON.parse(good), version: 'новая' });
    expect(() => parseLauncherRelease(bad)).toThrow(/version/);
  });
});

describe('сравнение версий', () => {
  it('0.10.0 новее 0.9.0 — сравниваем числами, а не строками', () => {
    expect(isNewer('0.10.0', '0.9.0')).toBe(true);
  });

  it('та же версия — не новее', () => {
    expect(isNewer('0.2.0', '0.2.0')).toBe(false);
  });

  it('старее — не новее', () => {
    expect(isNewer('0.1.9', '0.2.0')).toBe(false);
  });
});

describe('сборка описания для выкладки', () => {
  it('хеш приводится к нижнему регистру', () => {
    const built = buildLauncherRelease({
      version: '0.2.0', file: 'a.exe', size: 10, sha256: 'A'.repeat(64),
    });
    expect(built.sha256).toBe('a'.repeat(64));
  });
});
```

- [x] **Шаг 2: Убедиться, что тест падает**

Запустить: `npm test -- selfupdate`
Ожидание: FAIL, модуля нет.

- [x] **Шаг 3: Написать модуль**

```js
// launcher/src/main/selfupdate.js
// Лаунчер обновляет сам себя из того же приватного релиза, откуда
// берёт пак. Своего кода тут немного, потому что скачивание с проверкой
// хеша уже написано в download.js, а чтение ассетов — в github.js.
//
// Имя ассета — launcher-release.json, а не launcher.json: последнее
// занято конфигом игрока, и одинаковые имена в логе сбивают с толку.

const SHA256 = /^[0-9a-f]{64}$/i;
const VERSION = /^\d+\.\d+\.\d+$/;

export const RELEASE_ASSET = 'launcher-release.json';

function fail(message) {
  throw new Error(`описание релиза лаунчера: ${message}`);
}

const isText = (v) => typeof v === 'string' && v.trim() !== '';

export function parseLauncherRelease(text) {
  let raw;
  try {
    raw = JSON.parse(text);
  } catch (err) {
    fail(`ответ не JSON (${err.message})`);
  }
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) fail('не объект');

  if (!isText(raw.version) || !VERSION.test(raw.version)) fail('version: не вида 1.2.3');

  // Имя файла склеивается с папкой кэша на машине игрока: всё, что
  // может увести запись наружу, отсекается здесь.
  if (!isText(raw.file)) fail('file: пустое или не строка');
  if (/[/\\]/.test(raw.file) || raw.file.includes('..')) fail(`file: не имя файла «${raw.file}»`);

  if (!isText(raw.sha256) || !SHA256.test(raw.sha256)) fail('sha256: не 64 шестнадцатеричных символа');
  if (!Number.isInteger(raw.size) || raw.size <= 0) fail('size: не целое положительное');
  if (raw.notes !== undefined && typeof raw.notes !== 'string') fail('notes: не строка');

  return {
    version: raw.version,
    file: raw.file,
    size: raw.size,
    sha256: raw.sha256.toLowerCase(),
    notes: raw.notes ?? '',
  };
}

// Строкой версии сравнивать нельзя: «0.10.0» < «0.9.0» по алфавиту.
export function isNewer(remote, local) {
  const parts = (v) => String(v).split('.').map((n) => Number.parseInt(n, 10) || 0);
  const [a, b] = [parts(remote), parts(local)];
  for (let i = 0; i < 3; i += 1) {
    if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) > (b[i] ?? 0);
  }
  return false;
}

export function buildLauncherRelease({ version, file, size, sha256, notes = '' }) {
  return parseLauncherRelease(JSON.stringify({ version, file, size, sha256, notes }));
}
```

- [x] **Шаг 4: Тесты зелёные**

Запустить: `npm test -- selfupdate`
Ожидание: PASS.

- [x] **Шаг 5: Коммит**

```bash
git add launcher/src/main/selfupdate.js launcher/test/selfupdate.test.js
git commit -m "Лаунчер: разбор описания своего релиза и сравнение версий"
```

---

### Задача 2: Проверка и скачивание обновления лаунчера

**Файлы:**
- Изменить: `launcher/src/main/selfupdate.js`
- Изменить: `launcher/src/main/progress.js` (стадия `LAUNCHER`)
- Тест: `launcher/test/selfupdate.test.js`

**Интерфейсы:**
- Использует: `packSource()` из `install.js`, `releaseByTag`, `findAsset`,
  `assetUrl`, `apiHeaders` из `github.js`, `downloadFile` из `download.js`,
  `paths.root()`.
- Даёт: `checkLauncherUpdate({ source, currentVersion, fetchImpl }) -> null | { version, file, size, sha256, notes, url }`,
  `downloadInstaller(update, { onProgress, fetchImpl, token }) -> путь к .exe`.

- [x] **Шаг 1: Дописать падающие тесты**

```js
// добавить в launcher/test/selfupdate.test.js
import { checkLauncherUpdate } from '../src/main/selfupdate.js';

const release = {
  tag_name: 'pack',
  assets: [
    { id: 7, name: 'launcher-release.json' },
    { id: 8, name: 'LMPC-Launcher-0.2.0.exe' },
  ],
};

const fakeFetch = (body) => async (url) =>
  url.includes('/releases/tags/')
    ? { ok: true, status: 200, json: async () => release, text: async () => JSON.stringify(release) }
    : { ok: true, status: 200, text: async () => body, json: async () => JSON.parse(body) };

const source = { kind: 'github', owner: 'o', repo: 'r', tag: 'pack', token: 't' };

describe('проверка обновления', () => {
  it('находит новую версию и даёт адрес установщика по id ассета', async () => {
    const update = await checkLauncherUpdate({
      source, currentVersion: '0.1.0', fetchImpl: fakeFetch(good),
    });
    expect(update.version).toBe('0.2.0');
    expect(update.url).toBe('https://api.github.com/repos/o/r/releases/assets/8');
  });

  it('на своей же версии молчит', async () => {
    const update = await checkLauncherUpdate({
      source, currentVersion: '0.2.0', fetchImpl: fakeFetch(good),
    });
    expect(update).toBe(null);
  });

  it('без раздачи не ходит в сеть', async () => {
    const update = await checkLauncherUpdate({
      source: { kind: 'none' }, currentVersion: '0.1.0',
      fetchImpl: () => { throw new Error('в сеть ходить не должны'); },
    });
    expect(update).toBe(null);
  });
});
```

- [x] **Шаг 2: Убедиться, что тесты падают**

Запустить: `npm test -- selfupdate`
Ожидание: FAIL, `checkLauncherUpdate` не экспортируется.

- [x] **Шаг 3: Дописать модуль и стадию прогресса**

В `progress.js` в `STAGES` добавить строку `LAUNCHER: 'launcher',`
(после `MANIFEST`).

В `selfupdate.js` дописать:

```js
import path from 'node:path';

import * as paths from './paths.js';
import { releaseByTag, findAsset, assetUrl, apiHeaders } from './github.js';
import { downloadFile } from './download.js';
import { progressEvent, STAGES } from './progress.js';

// Проверка идёт только на старте лаунчера. Пока игра запущена, менять
// .exe незачем: за паком в это время следит watchForUpdates.
export async function checkLauncherUpdate({ source, currentVersion, fetchImpl = fetch } = {}) {
  if (!source || source.kind !== 'github') return null;

  const { owner, repo, tag, token } = source;
  const release = await releaseByTag({ owner, repo, tag, token, fetchImpl });
  const descriptor = findAsset(release, RELEASE_ASSET);

  const response = await fetchImpl(assetUrl({ owner, repo, assetId: descriptor.id }), {
    headers: apiHeaders(token, 'application/octet-stream'),
    redirect: 'follow',
  });
  if (!response.ok) throw new Error(`GitHub ответил ${response.status} на ${RELEASE_ASSET}`);

  const remote = parseLauncherRelease(await response.text());
  if (!isNewer(remote.version, currentVersion)) return null;

  const installer = findAsset(release, remote.file);
  return { ...remote, url: assetUrl({ owner, repo, assetId: installer.id }) };
}

export const installerDir = () => path.join(paths.root(), 'cache', 'launcher');

// Хеш сверяет downloadFile: файл с несошедшимся хешем до папки
// не доходит, и запускать нам будет нечего — это и нужно.
export async function downloadInstaller(update, { token = '', onProgress = null, fetchImpl = fetch } = {}) {
  const dest = path.join(installerDir(), update.file);

  let done = 0;
  const result = await downloadFile({
    url: update.url,
    dest,
    sha256: update.sha256,
    headers: apiHeaders(token, 'application/octet-stream'),
    fetchImpl,
    onBytes: (n) => {
      done += n;
      onProgress?.(progressEvent({
        stage: STAGES.LAUNCHER,
        bytesDone: done,
        bytesTotal: update.size,
        message: 'Обновляю лаунчер',
      }));
    },
  });

  return result.path;
}
```

- [x] **Шаг 4: Тесты зелёные**

Запустить: `npm test`
Ожидание: PASS, включая `purity` (новый модуль electron не импортирует).

- [x] **Шаг 5: Коммит**

```bash
git add launcher/src/main/selfupdate.js launcher/src/main/progress.js launcher/test/selfupdate.test.js
git commit -m "Лаунчер: проверка и скачивание своего обновления"
```

---

### Задача 3: Тихая установка обновления при старте

**Файлы:**
- Изменить: `launcher/src/main/index.js`
- Изменить: `launcher/src/renderer/state.js` (стадия → состояние)

**Интерфейсы:**
- Использует: `checkLauncherUpdate`, `downloadInstaller` из задачи 2,
  `packSource()` из `install.js`.
- Даёт: ничего наружу; поведение при старте окна.

- [x] **Шаг 1: Провести стадию в интерфейс**

В `src/renderer/state.js`, в `stateForStage`, добавить строку
`case 'launcher': return State.LOADING;` рядом с `manifest`.

- [x] **Шаг 2: Написать установку в `index.js`**

Импорты сверху файла:

```js
import { spawn } from 'node:child_process';
import { checkLauncherUpdate, downloadInstaller } from './selfupdate.js';
import { packSource } from './install.js';
```

Функция рядом с `createWindow`:

```js
// Лаунчер обновляется молча: спрашивать «поставить сейчас?» значит
// получить половину игроков на старой версии, которая однажды
// перестанет понимать формат манифеста.
//
// Любая осечка — не ошибка запуска. Упавший GitHub, битое описание,
// несошедшийся хеш: пишем строку в лог и работаем как есть. Лаунчер,
// который не стартует из-за чужого сбоя, хуже старого лаунчера.
async function updateSelf() {
  try {
    const update = await checkLauncherUpdate({
      source: packSource(),
      currentVersion: app.getVersion(),
    });
    if (!update) return false;

    send('log', `есть новая версия лаунчера: ${update.version}`);
    const installer = await downloadInstaller(update, {
      token: packSource().token,
      onProgress: (event) => send('progress', event),
    });

    // /S — тихая установка NSIS. Установщик дожидается закрытия
    // лаунчера и запускает новую версию сам, поэтому сразу выходим.
    spawn(installer, ['/S'], { detached: true, stdio: 'ignore' }).unref();
    app.quit();
    return true;
  } catch (err) {
    send('log', `обновление лаунчера не состоялось: ${err.message}`);
    return false;
  }
}
```

Вызов — в `app.whenReady()`, после `createWindow()`:

```js
  // После создания окна, а не до: игрок должен видеть, что происходит,
  // а не гадать над пустым экраном.
  window.webContents.once('did-finish-load', () => { updateSelf(); });
```

- [x] **Шаг 3: Проверить, что ничего не сломалось**

Запустить: `npm test`
Ожидание: PASS (в `index.js` тестов нет, но `purity` следит за
остальными файлами).

- [x] **Шаг 4: Проверить руками**

Запустить: `npm start`
Ожидание: окно открывается, в логе строка про обновление либо ничего;
игра запускается как раньше.

- [x] **Шаг 5: Коммит**

```bash
git add launcher/src/main/index.js launcher/src/renderer/state.js
git commit -m "Лаунчер: тихое обновление себя при старте"
```

---

### Задача 4: Выкладка описания релиза вместе с установщиком

**Файлы:**
- Изменить: `launcher/tools/publish-launcher.js`

**Интерфейсы:**
- Использует: `buildLauncherRelease` из `selfupdate.js`,
  `sha256OfFile` из `download.js`, `apiHeaders`, `releaseByTag`.
- Даёт: ассет `launcher-release.json` в релизе рядом с `.exe`.

- [x] **Шаг 1: Вынести заливку ассета в функцию**

В `publish-launcher.js` заменить тело заливки на общую функцию — она
понадобится дважды (для `.exe` и для описания):

```js
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

  const uploadUrl = release.upload_url.replace(/\{\?[^}]*\}$/, '') + `?name=${encodeURIComponent(name)}`;
  const res = await fetch(uploadUrl, {
    method: 'POST',
    headers: { ...apiHeaders(token), 'Content-Type': contentType },
    body,
  });
  if (!res.ok) throw new Error(`GitHub ответил ${res.status} на ${name}: ${(await res.text()).slice(0, 300)}`);
  return res.json();
}
```

- [x] **Шаг 2: Собрать и залить описание**

После заливки `.exe` в `main()`:

```js
  // Версия, размер и хеш считаются здесь, а не вводятся руками:
  // расхождение хеша с файлом означает, что обновиться не сможет
  // ни один игрок, а заметно это станет далеко не сразу.
  const pkg = JSON.parse(await fsp.readFile(new URL('../package.json', import.meta.url), 'utf8'));
  const descriptor = buildLauncherRelease({
    version: pkg.version,
    file: name,
    size: stat.size,
    sha256: await sha256OfFile(file),
    notes: args.notes ?? '',
  });

  await uploadAsset({
    release, owner, repo, token: args.token,
    name: RELEASE_ASSET,
    body: `${JSON.stringify(descriptor, null, 2)}\n`,
    contentType: 'application/json',
  });
  console.log(`${RELEASE_ASSET} обновлён: версия ${descriptor.version}`);
```

Импорты в шапке файла:

```js
import { sha256OfFile } from '../src/main/download.js';
import { buildLauncherRelease, RELEASE_ASSET } from '../src/main/selfupdate.js';
```

- [x] **Шаг 3: Проверить разбор аргументов**

Запустить: `node tools/publish-launcher.js --repo a/b --token x`
Ожидание: падает на отсутствующем файле `dist/...exe`, а не на
синтаксисе.

- [x] **Шаг 4: Коммит**

```bash
git add launcher/tools/publish-launcher.js
git commit -m "Выкладка лаунчера: описание релиза с версией и хешем"
```

---

### Задача 5: Чтение и проверка новостей

**Файлы:**
- Создать: `launcher/src/main/news.js`
- Тест: `launcher/test/news.test.js`

**Интерфейсы:**
- Использует: `releaseByTag`, `findAsset`, `assetUrl`, `apiHeaders`,
  `paths.root()`.
- Даёт: `parseNews(text) -> Array<{ id, date, kind, title, body, pinned }>`,
  `fetchNews({ source, fetchImpl }) -> items`,
  `readCachedNews() -> items`, `writeCachedNews(items)`,
  `loadNews({ source, fetchImpl }) -> items` (сеть, при осечке — кэш),
  `noteLocal(item) -> items` (дописать местную запись в кэш),
  `NEWS_ASSET`, `LIMITS`.

- [x] **Шаг 1: Написать падающий тест**

```js
// launcher/test/news.test.js
import { describe, it, expect } from 'vitest';
import { parseNews, LIMITS } from '../src/main/news.js';

const item = {
  id: '2026-09-09-purifier',
  date: '2026-09-09',
  kind: 'add',
  title: 'Очиститель воздуха',
  body: 'Новый блок Кузнеца: снимает заражение вокруг себя.',
};
const news = (items) => JSON.stringify({ items });

describe('разбор новостей', () => {
  it('читает нормальную ленту', () => {
    expect(parseNews(news([item]))[0].title).toBe('Очиститель воздуха');
  });

  it('закреплённая запись идёт первой', () => {
    const items = parseNews(news([item, { ...item, id: 'x', title: 'Сервер переедет', pinned: true }]));
    expect(items[0].title).toBe('Сервер переедет');
  });

  it('двух закреплённых не бывает', () => {
    expect(() => parseNews(news([
      { ...item, pinned: true },
      { ...item, id: 'y', pinned: true },
    ]))).toThrow(/закреплённ/);
  });

  it('слишком длинный заголовок — ошибка сборки, а не кривая вёрстка', () => {
    expect(() => parseNews(news([{ ...item, title: 'я'.repeat(LIMITS.title + 1) }]))).toThrow(/title/);
  });

  it('неизвестный вид записи не пропускается', () => {
    expect(() => parseNews(news([{ ...item, kind: 'секрет' }]))).toThrow(/kind/);
  });

  it('черновик игрокам не показывается', () => {
    expect(parseNews(news([{ ...item, draft: true }]))).toHaveLength(0);
  });
});
```

- [x] **Шаг 2: Убедиться, что тест падает**

Запустить: `npm test -- news`
Ожидание: FAIL, модуля нет.

- [x] **Шаг 3: Написать модуль**

```js
// launcher/src/main/news.js
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
```

- [x] **Шаг 4: Тесты зелёные**

Запустить: `npm test -- news`
Ожидание: PASS.

- [x] **Шаг 5: Коммит**

```bash
git add launcher/src/main/news.js launcher/test/news.test.js
git commit -m "Лаунчер: чтение и проверка новостей с кэшем"
```

---

### Задача 6: Новости в окне

**Файлы:**
- Изменить: `launcher/src/main/index.js` (IPC `news:load`, автозапись)
- Изменить: `launcher/src/preload.cjs`
- Изменить: `launcher/src/renderer/news.js`
- Изменить: `launcher/src/renderer/style.css` (блок `.news*`)

**Интерфейсы:**
- Использует: `loadNews`, `noteLocal` из `news.js`, `el`, `clear` из `ui.js`.
- Даёт: `window.launcher.loadNews() -> Promise<items>`;
  `createNews()` без аргументов, как и раньше.

- [x] **Шаг 1: Провести новости через мост**

В `index.js`:

```js
import { loadNews, noteLocal } from './news.js';

ipcMain.handle('news:load', () => loadNews({ source: packSource() }));
```

В `preload.cjs`, в список методов:

```js
  loadNews: () => ipcRenderer.invoke('news:load'),
```

- [x] **Шаг 2: Автозапись об обновлении пака**

В `index.js`, в обработчике `game:play`, после успешного `play(...)`
— пак к этому моменту уже сверен:

```js
    // Игрок должен видеть, что трёхминутное скачивание было не зря,
    // даже если новость про содержимое написать забыли.
    const version = session.prepared?.manifest?.packVersion ?? 0;
    if (version > (readConfig().packVersion ?? 0)) {
      noteLocal({
        id: `local-pack-${version}`,
        date: new Date().toISOString().slice(0, 10),
        kind: 'info',
        title: 'Модпак обновлён',
        body: 'Лаунчер догрузил новые файлы пака. Ничего делать не нужно.',
      });
    }
```

- [x] **Шаг 3: Переписать компонент новостей**

`src/renderer/news.js` целиком:

```js
// Новости пака. Данные приходят из основного процесса: он ходит
// в релиз с токеном и держит кэш, поэтому окно про сеть не знает.
// Пустой список показывает спокойную заглушку.

import { el, clear } from './ui.js';

export function createNews({ source = () => window.launcher.loadNews() } = {}) {
  const list = el('div', { class: 'news-list' });
  const node = el('section', { class: 'news', 'aria-label': 'Новости' },
    el('div', { class: 'panel-head' }, el('h2', { text: 'Новости' })),
    list,
  );

  function renderEmpty() {
    clear(list);
    list.append(el('p', { class: 'news-empty', text: 'Здесь будут появляться новости и объявления сервера.' }));
  }

  function renderItems(items) {
    clear(list);
    for (const item of items) {
      // Длинная запись свёрнута до двух строк и раскрывается кликом:
      // так изредка можно написать подробнее, не ломая вёрстку.
      const body = el('p', { class: 'news-body', text: item.body });
      const article = el('article', {
        class: `news-item${item.pinned ? ' news-pinned' : ''}`,
        onclick: () => article.classList.toggle('open'),
      },
        el('div', { class: 'news-meta' },
          el('span', { class: `news-kind news-kind-${item.kind}`, text: item.label }),
          el('time', { class: 'news-date', text: item.date }),
        ),
        el('h3', { class: 'news-title', text: item.title }),
        body,
      );
      list.append(article);
    }
  }

  Promise.resolve(source())
    .then((items) => (Array.isArray(items) && items.length ? renderItems(items) : renderEmpty()))
    .catch(renderEmpty);

  return node;
}
```

- [x] **Шаг 4: Облик**

В `style.css`, рядом с существующими правилами `.news*`:

```css
.news-item { display: flex; flex-direction: column; gap: 4px; cursor: pointer; }
.news-meta { display: flex; align-items: baseline; gap: 8px; }
.news-kind {
  font-size: 10.5px; letter-spacing: 0.06em; text-transform: uppercase;
  color: var(--stone-1); border: 1px solid currentColor; border-radius: 2px;
  padding: 1px 5px; opacity: 0.85;
}
.news-kind-info { color: var(--ember); }
.news-pinned {
  border-left: 2px solid var(--ember); padding-left: 10px; margin-left: -12px;
}
.news-body {
  margin: 0; font-size: 12.5px; color: var(--mist); line-height: 1.55;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.news-item.open .news-body { -webkit-line-clamp: unset; }
```

Если переменной `--ember` в файле нет — взять существующий тёплый
акцент из `:root` и не заводить новых цветов.

- [x] **Шаг 5: Проверить руками и глазами**

Запустить: `npm start`
Ожидание: панель показывает заглушку без сети; с подложенным
`%APPDATA%/LMPC/news.json` — записи, метка, клик раскрывает текст.
Снять окно и посмотреть, что вёрстка не разъехалась.

- [x] **Шаг 6: Коммит**

```bash
git add launcher/src/main/index.js launcher/src/preload.cjs launcher/src/renderer/news.js launcher/src/renderer/style.css
git commit -m "Лаунчер: новости в окне, плашка «Важно» и раскрытие по клику"
```

---

### Задача 7: Черновик и выкладка новостей

**Файлы:**
- Создать: `launcher/news/news.json`
- Создать: `launcher/tools/make-news-draft.js`
- Создать: `launcher/tools/publish-news.js`
- Изменить: `launcher/package.json` (команды `news:draft`, `news:publish`)
- Изменить: `launcher/pack-config/README.md` (порядок выкладки)

**Интерфейсы:**
- Использует: `parseNews` из `news.js`, `releaseByTag`, `apiHeaders`.
- Даёт: команды `npm run news:draft`, `npm run news:publish -- --repo … --token …`.

- [x] **Шаг 1: Завести ленту с первой записью**

`launcher/news/news.json` — файл ведётся руками, это исходник:

```json
{
  "items": [
    {
      "id": "2026-09-09-launcher",
      "date": "2026-09-09",
      "kind": "add",
      "title": "Лаунчер обновляется сам",
      "body": "Больше не нужно скачивать новый установщик: лаунчер проверяет обновление при запуске и ставит его сам. Здесь же теперь появляются новости о сборке.",
      "pinned": false
    }
  ]
}
```

- [x] **Шаг 2: Написать сборщик черновика**

`launcher/tools/make-news-draft.js` — дописывает в ленту записи-черновики
по коммитам с прошлой выкладки. Текст в них технический: он подсказка
для человека, который перепишет запись по-людски, а не то, что увидит
игрок. Поэтому у каждой стоит `"draft": true`, и выкладка их не пустит.

```js
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

const subjects = git('log', `--since=${since}`, '--format=%s')
  .split('\n')
  .filter(Boolean)
  // Свои же служебные коммиты игроку неинтересны.
  .filter((s) => !/^(Заметк|Спек|План|Записк|docs)/i.test(s));

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
console.log(`заготовок добавлено: ${added}. Перепишите их в ${path.relative(repoRoot, file)} и снимите draft.`);
```

- [x] **Шаг 3: Написать выкладку**

`launcher/tools/publish-news.js`:

```js
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
    throw new Error(
      `в ленте ${drafts.length} черновик(ов): ${drafts.map((d) => d.id).join(', ')}.\n` +
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
```

- [x] **Шаг 4: Команды в `package.json`**

В `scripts`:

```json
    "news:draft": "node tools/make-news-draft.js",
    "news:publish": "node tools/publish-news.js",
```

- [x] **Шаг 5: Проверить черновик и отказ выкладки**

Запустить: `npm run news:draft`
Ожидание: в `news/news.json` появились записи с `draft: true`.

Запустить: `npm run news:publish -- --repo a/b --token ghp_xxxxxxxxxxxx`
Ожидание: падает со словами про черновики, в сеть не ходит.

Убрать черновики из файла руками, оставить одну человеческую запись.

- [x] **Шаг 6: Дописать порядок выкладки в README**

В `launcher/pack-config/README.md` — раздел о том, что выкладка теперь
состоит из трёх независимых команд: пак (`publish-pack.js`), лаунчер
с описанием релиза (`publish-launcher.js`), новости
(`publish-news.js`). Новость можно выложить одну, ничего не пересобирая.

- [x] **Шаг 7: Коммит**

```bash
git add launcher/news launcher/tools/make-news-draft.js launcher/tools/publish-news.js launcher/package.json launcher/pack-config/README.md
git commit -m "Новости: черновик из коммитов и выкладка отдельной командой"
```

---

### Задача 8: Проверка красоты и итоговый прогон

**Файлы:**
- Изменить: при необходимости `launcher/src/renderer/style.css`
- Создать: `docs/superpowers/notes/2026-09-09-avtoobnovlenie-i-novosti.md`

- [x] **Шаг 1: Полный прогон тестов**

Запустить: `npm test`
Ожидание: все зелёные, включая старые 171.

- [x] **Шаг 2: Снять окно и посмотреть**

Положить в `%APPDATA%/LMPC/news.json` ленту из четырёх записей: одна
закреплённая, одна длинная (на раскрытие), две обычные. Запустить
`npm start`, снять окно и **посмотреть глазами**: не разъехались ли
колонки, не спорит ли метка «Важно» с общим тоном экрана, читается ли
дата. Править, пока не станет опрятно.

- [x] **Шаг 3: Заметка в репозиторий**

Записать в `docs/superpowers/notes/`: что сделано, что осталось живой
проверке (полный круг обновления на второй машине), где лежит лента
новостей и как её выкладывать.

- [x] **Шаг 4: Коммит**

```bash
git add launcher/src/renderer/style.css docs/superpowers/notes/2026-09-09-avtoobnovlenie-i-novosti.md
git commit -m "Заметка об автообновлении и новостях, доводка облика панели"
```

---

## Что остаётся живой проверке владельца

- Полный круг обновления лаунчера: поставить старую версию, выложить
  новую, убедиться, что установщик отработал тихо и лаунчер
  перезапустился новым.
- Первая настоящая выкладка новостей (нужен токен: старый отозван).
