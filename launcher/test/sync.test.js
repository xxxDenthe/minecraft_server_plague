// planSync — чистая функция над состоянием папки, и именно она решает,
// что удалить. Все опасные сценарии проверяются здесь, без единого
// скачивания.

import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll } from 'vitest';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { createHash } from 'node:crypto';

import { planSync, applySync, PROTECTED } from '../src/main/sync.js';

const sha = (text) => createHash('sha256').update(text).digest('hex');

const BODY = 'джарник';
const BODY_SHA = sha(BODY);

let dir;

const manifest = (files, managedDirs = ['mods', 'config']) => ({
  packVersion: 1,
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  managedDirs,
  files,
});

const file = (p, body = BODY) => ({
  path: p,
  sha256: sha(body),
  size: Buffer.byteLength(body),
  url: `http://127.0.0.1:1/${p}`,
});

async function put(relative, body = BODY) {
  const full = path.join(dir, relative);
  await fsp.mkdir(path.dirname(full), { recursive: true });
  await fsp.writeFile(full, body);
  return full;
}

beforeEach(async () => {
  dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-sync-'));
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

const paths = (list) => list.map((f) => f.path ?? f).sort();

describe('план синхронизации', () => {
  it('файла нет — в toDownload', async () => {
    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(paths(plan.toDownload)).toEqual(['mods/create.jar']);
    expect(plan.toKeep).toEqual([]);
  });

  it('файл есть и хеш совпал — в toKeep, скачивания нет', async () => {
    await put('mods/create.jar');
    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDownload).toEqual([]);
    expect(paths(plan.toKeep)).toEqual(['mods/create.jar']);
  });

  it('файл есть, но хеш не тот — в toDownload', async () => {
    await put('mods/create.jar', 'версия позапрошлой недели');
    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(paths(plan.toDownload)).toEqual(['mods/create.jar']);
  });

  it('лишний файл в управляемой папке — в toDelete', async () => {
    await put('mods/create.jar');
    await put('mods/xray-1.21.1.jar', 'чужой мод');

    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDelete).toEqual(['mods/xray-1.21.1.jar']);
  });

  it('лишний файл вне управляемых папок не трогается', async () => {
    await put('mods/create.jar');
    await put('journeymap/data/waypoints.json', 'точки игрока');
    await put('emotes/wave.json', 'эмоция');

    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDelete).toEqual([]);
  });

  it('пользовательские файлы не удаляются никогда, даже внутри управляемой папки', async () => {
    await put('mods/create.jar');
    await put('config/options.txt', 'настройки');
    await put('config/saves/мир/level.dat', 'мир');
    await put('config/screenshots/1.png', 'снимок');
    await put('config/logs/latest.log', 'лог');
    await put('config/servers.dat', 'список серверов');

    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDelete).toEqual([]);
  });

  it('пустая управляемая папка и непустой манифест — всё в toDownload', async () => {
    await fsp.mkdir(path.join(dir, 'mods'), { recursive: true });

    const plan = await planSync(
      manifest([file('mods/a.jar'), file('mods/b.jar'), file('config/create.toml', 'конфиг')]),
      dir
    );

    expect(plan.toDownload).toHaveLength(3);
    expect(plan.toDelete).toEqual([]);
  });

  // Защита от самого дорогого сценария: сервер отдал пустой ответ,
  // лаунчер честно его исполнил, восемь человек остались без модов.
  it('манифест с пустым files не приводит к удалению всего', async () => {
    await put('mods/create.jar');
    await put('config/create.toml', 'конфиг');

    const plan = await planSync(manifest([]), dir);

    expect(plan.toDelete).toEqual([]);
    expect(plan.skippedDeletion).toMatch(/пуст/);
  });

  it('вложенные папки внутри управляемой обходятся', async () => {
    await put('config/create/common.toml', 'общий');
    await put('config/create/лишний.toml', 'лишний');

    const plan = await planSync(manifest([file('config/create/common.toml', 'общий')]), dir);

    expect(plan.toDelete).toEqual(['config/create/лишний.toml']);
  });

  it('обрубок .part от прошлой попытки убирается как лишний файл', async () => {
    await put('mods/create.jar');
    await put('mods/create.jar.part', 'половина');

    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDelete).toEqual(['mods/create.jar.part']);
  });

  it('каждое имя из PROTECTED переживает синхронизацию', async () => {
    await put('mods/create.jar');
    for (const name of PROTECTED) await put(`config/${name}/след.dat`, 'личное');

    const plan = await planSync(manifest([file('mods/create.jar')]), dir);

    expect(plan.toDelete).toEqual([]);
  });
});

describe('применение плана', () => {
  let server;
  let base;

  beforeAll(async () => {
    server = http.createServer((req, res) => res.writeHead(200).end(BODY));
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });
  afterAll(() => new Promise((resolve) => server.close(resolve)));

  it('сначала качает, потом удаляет', async () => {
    await put('mods/старый.jar', 'старьё');

    const wanted = { ...file('mods/новый.jar'), url: `${base}/новый.jar` };
    const plan = await planSync(manifest([wanted]), dir);
    const result = await applySync(plan, { instanceDir: dir });

    expect(result.downloaded).toBe(1);
    expect(result.deleted).toEqual(['mods/старый.jar']);
    expect(fs.readFileSync(path.join(dir, 'mods', 'новый.jar'), 'utf8')).toBe(BODY);
    expect(fs.existsSync(path.join(dir, 'mods', 'старый.jar'))).toBe(false);
  });

  it('опустевшая папка убирается, но инстанс остаётся', async () => {
    await put('config/лишнее/файл.toml', 'мусор');

    const wanted = { ...file('mods/нужный.jar'), url: `${base}/нужный.jar` };
    const plan = await planSync(manifest([wanted]), dir);
    await applySync(plan, { instanceDir: dir });

    expect(fs.existsSync(path.join(dir, 'config', 'лишнее'))).toBe(false);
    expect(fs.existsSync(dir)).toBe(true);
  });

  it('сорванная закачка не доходит до удаления', async () => {
    await put('mods/старый.jar', 'старьё');

    const wanted = { ...file('mods/новый.jar'), url: `${base}/новый.jar`, sha256: sha('совсем другое') };
    const plan = await planSync(manifest([wanted]), dir);

    await expect(applySync(plan, { instanceDir: dir, retries: 1 })).rejects.toThrow();
    expect(fs.existsSync(path.join(dir, 'mods', 'старый.jar'))).toBe(true);
  });
});
