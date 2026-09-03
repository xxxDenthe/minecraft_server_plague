// Тесты на настоящем HTTP-сервере, поднятом здесь же: подмена fetch
// проверила бы наши представления о потоках, а не сами потоки.

import { describe, it, expect, beforeAll, afterAll, beforeEach, afterEach } from 'vitest';
import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';

import { downloadFile, downloadAll, sha256OfFile } from '../src/main/download.js';

const sha = (text) => createHash('sha256').update(text).digest('hex');

const BODY = 'содержимое джарника, притворяющегося модом';
const BODY_SHA = sha(BODY);

let server;
let base;
const hits = new Map();
let failuresLeft = 0;

beforeAll(async () => {
  server = http.createServer((req, res) => {
    hits.set(req.url, (hits.get(req.url) ?? 0) + 1);

    if (req.url === '/flaky') {
      if (failuresLeft > 0) {
        failuresLeft -= 1;
        res.writeHead(200, { 'content-length': String(Buffer.byteLength(BODY)) });
        res.write(BODY.slice(0, 5));
        res.destroy(); // обрыв посреди передачи
        return;
      }
      res.writeHead(200).end(BODY);
      return;
    }

    if (req.url === '/missing') {
      res.writeHead(404).end('нет такого');
      return;
    }

    if (req.url.startsWith('/file')) {
      res.writeHead(200).end(BODY);
      return;
    }

    res.writeHead(404).end();
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

afterAll(() => new Promise((resolve) => server.close(resolve)));

let dir;
beforeEach(async () => {
  dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-dl-'));
  hits.clear();
  failuresLeft = 0;
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

describe('скачивание одного файла', () => {
  it('файл скачивается, хеш совпадает', async () => {
    const dest = path.join(dir, 'mods', 'create.jar');
    const res = await downloadFile({ url: `${base}/file`, dest, sha256: BODY_SHA });

    expect(res.downloaded).toBe(true);
    expect(await sha256OfFile(dest)).toBe(BODY_SHA);
  });

  it('при несовпадении хеша файл удаляется и бросается ошибка', async () => {
    const dest = path.join(dir, 'mods', 'create.jar');

    await expect(
      downloadFile({ url: `${base}/file`, dest, sha256: sha('другое'), retries: 1 })
    ).rejects.toThrow(/хеш не совпал/);

    expect(fs.existsSync(dest)).toBe(false);
    expect(fs.existsSync(`${dest}.part`)).toBe(false);
  });

  it('обрыв соединения приводит к повтору', async () => {
    failuresLeft = 2;
    const dest = path.join(dir, 'flaky.jar');

    const res = await downloadFile({
      url: `${base}/flaky`,
      dest,
      sha256: BODY_SHA,
      retries: 3,
      retryDelayMs: 1,
    });

    expect(res.downloaded).toBe(true);
    expect(hits.get('/flaky')).toBe(3);
  });

  it('после трёх неудач сдаётся с внятной ошибкой', async () => {
    failuresLeft = 99;

    await expect(
      downloadFile({ url: `${base}/flaky`, dest: path.join(dir, 'x.jar'), sha256: BODY_SHA, retries: 3, retryDelayMs: 1 })
    ).rejects.toThrow(/за 3 попытки/);

    expect(hits.get('/flaky')).toBe(3);
  });

  it('существующий файл с верным хешем не скачивается заново', async () => {
    const dest = path.join(dir, 'create.jar');
    await fsp.writeFile(dest, BODY);

    const res = await downloadFile({ url: `${base}/file`, dest, sha256: BODY_SHA });

    expect(res.downloaded).toBe(false);
    expect(hits.get('/file')).toBe(undefined);
  });

  it('существующий файл с чужим хешем перекачивается', async () => {
    const dest = path.join(dir, 'create.jar');
    await fsp.writeFile(dest, 'старая версия');

    const res = await downloadFile({ url: `${base}/file`, dest, sha256: BODY_SHA });

    expect(res.downloaded).toBe(true);
    expect(await fsp.readFile(dest, 'utf8')).toBe(BODY);
  });

  it('404 — ошибка, а не файл со страницей ошибки внутри', async () => {
    const dest = path.join(dir, 'create.jar');

    await expect(
      downloadFile({ url: `${base}/missing`, dest, sha256: BODY_SHA, retries: 1 })
    ).rejects.toThrow(/404/);

    expect(fs.existsSync(dest)).toBe(false);
  });
});

describe('скачивание пачкой', () => {
  const items = (n) =>
    Array.from({ length: n }, (_, i) => ({
      url: `${base}/file?i=${i}`,
      dest: path.join(dir, 'mods', `mod-${i}.jar`),
      sha256: BODY_SHA,
      size: Buffer.byteLength(BODY),
    }));

  it('качает всё и отдаёт прогресс штуками и байтами', async () => {
    const events = [];
    await downloadAll(items(12), { concurrency: 4, stage: 'pack', onProgress: (e) => events.push(e) });

    for (let i = 0; i < 12; i += 1) {
      expect(fs.existsSync(path.join(dir, 'mods', `mod-${i}.jar`))).toBe(true);
    }

    const last = events.at(-1);
    expect(last.stage).toBe('pack');
    expect(last.current).toBe(12);
    expect(last.total).toBe(12);
    expect(last.bytesDone).toBe(last.bytesTotal);
  });

  it('одна неудача не проходит незамеченной', async () => {
    const list = [...items(3), { url: `${base}/missing`, dest: path.join(dir, 'bad.jar'), sha256: BODY_SHA }];

    await expect(downloadAll(list, { concurrency: 2, retries: 1 })).rejects.toThrow(/не скачалось файлов/);
  });

  it('пустой список — не ошибка', async () => {
    await expect(downloadAll([], {})).resolves.toEqual([]);
  });
});

describe('SHA-1 для файлов Mojang', () => {
  it('файл проверяется по sha1, если задан он', async () => {
    const { createHash } = await import('node:crypto');
    const sha1 = createHash('sha1').update(BODY).digest('hex');
    const dest = path.join(dir, 'client.jar');

    const res = await downloadFile({ url: `${base}/file`, dest, sha1 });
    expect(res.downloaded).toBe(true);

    const again = await downloadFile({ url: `${base}/file`, dest, sha1 });
    expect(again.downloaded).toBe(false);
  });

  it('чужой sha1 отвергается так же, как чужой sha256', async () => {
    const dest = path.join(dir, 'client.jar');
    await expect(
      downloadFile({ url: `${base}/file`, dest, sha1: 'f'.repeat(40), retries: 1 })
    ).rejects.toThrow(/хеш не совпал/);
    expect(fs.existsSync(dest)).toBe(false);
  });
});
