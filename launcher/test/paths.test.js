// Раздел 6 спека: лаунчер живёт в своей папке и не трогает ванильный
// .minecraft. Синхронизация умеет удалять файлы, поэтому ошибка в путях
// стоит чужих миров — отсюда тест, а не «и так очевидно».

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

import * as paths from '../src/main/paths.js';

let temp;
let savedRoot;

beforeEach(() => {
  savedRoot = process.env.PLAGUE_LAUNCHER_ROOT;
  temp = fs.mkdtempSync(path.join(os.tmpdir(), 'plague-paths-'));
  process.env.PLAGUE_LAUNCHER_ROOT = temp;
});

afterEach(() => {
  if (savedRoot === undefined) delete process.env.PLAGUE_LAUNCHER_ROOT;
  else process.env.PLAGUE_LAUNCHER_ROOT = savedRoot;
  fs.rmSync(temp, { recursive: true, force: true });
});

const allPaths = () => [
  paths.instance(),
  paths.runtime(),
  paths.versions(),
  paths.libraries(),
  paths.assets(),
  paths.configFile(),
  paths.logFile(),
];

describe('пути лаунчера', () => {
  it('корень берётся из PLAGUE_LAUNCHER_ROOT', () => {
    expect(paths.root()).toBe(path.resolve(temp));
  });

  it('всё лежит внутри корня', () => {
    const root = paths.root();
    for (const p of allPaths()) {
      expect(path.resolve(p).startsWith(root + path.sep)).toBe(true);
    }
  });

  it('ни один путь не ведёт наружу', () => {
    for (const p of allPaths()) {
      expect(path.relative(paths.root(), p)).not.toMatch(/(^|[\/])\.\.($|[\/])/);
    }
  });

  it('ни один путь не совпадает с ванильным .minecraft', () => {
    const vanilla = path.resolve(
      process.env.APPDATA ?? path.join(os.homedir(), 'AppData', 'Roaming'),
      '.minecraft'
    );
    for (const p of [paths.root(), ...allPaths()]) {
      expect(path.resolve(p)).not.toBe(vanilla);
      expect(vanilla.startsWith(path.resolve(p) + path.sep)).toBe(false);
    }
  });

  it('корень по умолчанию — %APPDATA%\PlagueLauncher', () => {
    delete process.env.PLAGUE_LAUNCHER_ROOT;
    expect(path.basename(paths.root())).toBe('PlagueLauncher');
  });

  it('inInstance не выпускает за пределы инстанса', () => {
    expect(() => paths.inInstance('../../windows/system32')).toThrow();
    expect(() => paths.inInstance('mods/create.jar')).not.toThrow();
    expect(paths.inInstance('mods/create.jar')).toBe(
      path.join(paths.instance(), 'mods', 'create.jar')
    );
  });

  it('ensureDirs создаёт дерево папок', () => {
    paths.ensureDirs();
    for (const p of [paths.instance(), paths.runtime(), paths.versions(), paths.libraries(), paths.assets()]) {
      expect(fs.existsSync(p)).toBe(true);
    }
  });
});
