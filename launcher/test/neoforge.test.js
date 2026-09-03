// Установщик NeoForge проверяется вживую (см. коммит задачи 10):
// его поведение не подделать разумным тестом. Здесь — то, на чём
// строятся пути, и обещание не трогать чужие папки.

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { versionId, profilePath } from '../src/main/neoforge.js';
import * as paths from '../src/main/paths.js';

let temp;
let savedRoot;

beforeEach(() => {
  savedRoot = process.env.LMPC_LAUNCHER_ROOT;
  temp = fs.mkdtempSync(path.join(os.tmpdir(), 'plague-nf-'));
  process.env.LMPC_LAUNCHER_ROOT = temp;
});

afterEach(() => {
  if (savedRoot === undefined) delete process.env.LMPC_LAUNCHER_ROOT;
  else process.env.LMPC_LAUNCHER_ROOT = savedRoot;
  fs.rmSync(temp, { recursive: true, force: true });
});

describe('пути NeoForge', () => {
  it('идентификатор версии — как у установщика', () => {
    expect(versionId('21.1.249')).toBe('neoforge-21.1.249');
  });

  it('профиль лежит в нашей папке версий, а не в ванильной', () => {
    const profile = profilePath('21.1.249');

    expect(profile.startsWith(paths.versions() + path.sep)).toBe(true);
    expect(profile.endsWith(path.join('neoforge-21.1.249', 'neoforge-21.1.249.json'))).toBe(true);
    expect(profile).not.toMatch(/\.minecraft/);
  });
});
