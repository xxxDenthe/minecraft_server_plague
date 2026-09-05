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
import { zip } from '../src/main/archive.js';
import { readState, STATE_FILE } from '../src/main/state.js';

const sha = (text) => createHash('sha256').update(text).digest('hex');
const shaOfFile = (file) => sha(fs.readFileSync(file));

let dir;

const manifest = (archives, managedDirs = ['mods', 'config']) => ({
  packVersion: 7,
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  managedDirs,
  archives,
});

const archive = (dirName, body = dirName) => ({
  dir: dirName,
  sha256: sha(body),
  contentId: sha(`${body}-content`),
  size: Buffer.byteLength(body),
  url: `http://127.0.0.1:1/${dirName}.zip`,
});

const state = (archives) => ({ packVersion: 7, archives });

async function put(relative, body = 'джарник') {
  const full = path.join(dir, relative);
  await fsp.mkdir(path.dirname(full), { recursive: true });
  await fsp.writeFile(full, body);
  return full;
}

beforeEach(async () => {
  dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-sync-'));
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

const dirs = (list) => list.map((a) => a.dir ?? a).sort();

describe('план синхронизации', () => {
  it('состояния нет — все архивы в toInstall', async () => {
    const plan = await planSync(manifest([archive('mods'), archive('config')]), dir);

    expect(dirs(plan.toInstall)).toEqual(['config', 'mods']);
    expect(plan.toKeep).toEqual([]);
  });

  it('хеш совпал и файлы на месте — в toKeep, скачивания нет', async () => {
    const mods = archive('mods');
    await put('mods/create.jar');

    const plan = await planSync(
      manifest([mods]),
      dir,
      state({ mods: { sha256: mods.sha256, files: ['mods/create.jar'] } })
    );

    expect(plan.toInstall).toEqual([]);
    expect(dirs(plan.toKeep)).toEqual(['mods']);
  });

  it('хеш не тот — в toInstall, даже если файлы на месте', async () => {
    await put('mods/create.jar');

    const plan = await planSync(
      manifest([archive('mods', 'новая версия')]),
      dir,
      state({ mods: { sha256: sha('старая версия'), files: ['mods/create.jar'] } })
    );

    expect(dirs(plan.toInstall)).toEqual(['mods']);
  });

  // Ради этого в состоянии и лежит список файлов.
  it('игрок удалил мод руками — папка переставится, хотя хеш совпал', async () => {
    const mods = archive('mods');
    await put('mods/create.jar');

    const plan = await planSync(
      manifest([mods]),
      dir,
      state({ mods: { sha256: mods.sha256, files: ['mods/create.jar', 'mods/пропавший.jar'] } })
    );

    expect(dirs(plan.toInstall)).toEqual(['mods']);
  });

  it('управляемая папка без архива в манифесте — в dirsToWipe', async () => {
    await put('config/create.toml', 'конфиг');

    const plan = await planSync(manifest([archive('mods')]), dir);

    expect(plan.dirsToWipe).toEqual(['config']);
  });

  it('управляемая папка без архива и без файлов на диске не попадает никуда', async () => {
    const plan = await planSync(manifest([archive('mods')]), dir);
    expect(plan.dirsToWipe).toEqual([]);
  });

  it('файлы вне управляемых папок не трогаются', async () => {
    await put('journeymap/data/waypoints.json', 'точки игрока');
    await put('emotes/wave.json', 'эмоция');

    const plan = await planSync(manifest([archive('mods'), archive('config')]), dir);

    expect(plan.dirsToWipe).toEqual([]);
  });

  // Защита от самого дорогого сценария: сервер отдал пустой ответ,
  // лаунчер честно его исполнил, восемь человек остались без модов.
  it('манифест с пустым archives не приводит к удалению всего', async () => {
    await put('mods/create.jar');
    await put('config/create.toml', 'конфиг');

    const plan = await planSync(manifest([]), dir);

    expect(plan.dirsToWipe).toEqual([]);
    expect(plan.skippedDeletion).toMatch(/пуст/);
  });

  it('в PROTECTED есть файл состояния: чистка не должна его съесть', () => {
    expect(PROTECTED).toContain(STATE_FILE);
  });
});

describe('применение плана', () => {
  let server;
  let base;
  let packDir;
  let zips;

  // Раздаём настоящие zip, собранные тем же tar.exe, каким лаунчер
  // их распаковывает: подделка архива проверила бы только заглушку.
  beforeAll(async () => {
    packDir = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-pack-'));
    zips = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-zips-'));

    await fsp.mkdir(path.join(packDir, 'mods'), { recursive: true });
    await fsp.mkdir(path.join(packDir, 'config', 'create'), { recursive: true });
    await fsp.writeFile(path.join(packDir, 'mods', 'create.jar'), 'новый create');
    await fsp.writeFile(path.join(packDir, 'mods', 'jei.jar'), 'jei');
    await fsp.writeFile(path.join(packDir, 'config', 'create', 'common.toml'), 'общий');

    await zip({
      sourceDir: packDir,
      entries: ['mods/create.jar', 'mods/jei.jar'],
      archive: path.join(zips, 'mods.zip'),
    });
    await zip({
      sourceDir: packDir,
      entries: ['config/create/common.toml'],
      archive: path.join(zips, 'config.zip'),
    });

    server = http.createServer((req, res) => {
      const file = path.join(zips, path.basename(req.url));
      if (!fs.existsSync(file)) return res.writeHead(404).end();
      return res.writeHead(200).end(fs.readFileSync(file));
    });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  afterAll(async () => {
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(packDir, { recursive: true, force: true });
    fs.rmSync(zips, { recursive: true, force: true });
  });

  const served = (name) => ({
    dir: name,
    sha256: shaOfFile(path.join(zips, `${name}.zip`)),
    contentId: 'c'.repeat(64),
    size: fs.statSync(path.join(zips, `${name}.zip`)).size,
    url: `${base}/${name}.zip`,
  });

  it('качает архив, чистит папку и распаковывает', async () => {
    await put('mods/старый.jar', 'старьё');

    const plan = await planSync(manifest([served('mods')]), dir);
    const result = await applySync(plan, { instanceDir: dir });

    expect(result.installed).toEqual(['mods']);
    expect(fs.readFileSync(path.join(dir, 'mods', 'create.jar'), 'utf8')).toBe('новый create');
    expect(fs.existsSync(path.join(dir, 'mods', 'старый.jar'))).toBe(false);
  });

  it('состояние после установки описывает то, что реально лежит', async () => {
    const plan = await planSync(manifest([served('mods')]), dir);
    await applySync(plan, { instanceDir: dir });

    const saved = await readState(dir);

    expect(saved.packVersion).toBe(7);
    expect(saved.archives.mods.sha256).toBe(served('mods').sha256);
    expect(saved.archives.mods.files.sort()).toEqual(['mods/create.jar', 'mods/jei.jar']);
  });

  it('второй запуск на том же паке ничего не качает', async () => {
    const wanted = manifest([served('mods')]);

    await applySync(await planSync(wanted, dir), { instanceDir: dir });
    const second = await planSync(wanted, dir, await readState(dir));

    expect(second.toInstall).toEqual([]);
    expect(dirs(second.toKeep)).toEqual(['mods']);
  });

  it('вложенные папки внутри архива распаковываются', async () => {
    const plan = await planSync(manifest([served('mods'), served('config')]), dir);
    await applySync(plan, { instanceDir: dir });

    expect(fs.readFileSync(path.join(dir, 'config', 'create', 'common.toml'), 'utf8')).toBe('общий');
  });

  it('пользовательские файлы переживают установку, даже внутри управляемой папки', async () => {
    for (const name of PROTECTED) await put(`mods/${name}/след.dat`, 'личное');

    const plan = await planSync(manifest([served('mods')]), dir);
    await applySync(plan, { instanceDir: dir });

    for (const name of PROTECTED) {
      expect(fs.existsSync(path.join(dir, 'mods', name, 'след.dat'))).toBe(true);
    }
  });

  it('папка, выпавшая из пака, вычищается, а инстанс остаётся', async () => {
    await put('config/лишнее/файл.toml', 'мусор');

    const plan = await planSync(manifest([served('mods')]), dir);
    const result = await applySync(plan, { instanceDir: dir });

    expect(result.wiped).toEqual(['config']);
    expect(fs.existsSync(path.join(dir, 'config'))).toBe(false);
    expect(fs.existsSync(dir)).toBe(true);
  });

  it('архивы не остаются на диске после распаковки', async () => {
    const cacheDir = path.join(dir, '.cache', 'pack');
    const plan = await planSync(manifest([served('mods')]), dir);
    await applySync(plan, { instanceDir: dir, cacheDir });

    expect(fs.existsSync(path.join(cacheDir, 'mods.zip'))).toBe(false);
  });

  it('сорванная закачка не доходит до чистки папки', async () => {
    await put('mods/старый.jar', 'старьё');

    const broken = { ...served('mods'), sha256: sha('совсем другое') };
    const plan = await planSync(manifest([broken]), dir);

    await expect(applySync(plan, { instanceDir: dir, retries: 1 })).rejects.toThrow();
    expect(fs.existsSync(path.join(dir, 'mods', 'старый.jar'))).toBe(true);
    expect(fs.existsSync(path.join(dir, STATE_FILE))).toBe(false);
  });
});
