// Состояние читается на чужой машине и может быть чем угодно: обрывком
// после отключения питания, следом ручной правки, файлом от прошлой
// версии лаунчера. Ни один из этих случаев не должен ронять запуск.

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

import { readState, writeState, stateFile, STATE_FILE } from '../src/main/state.js';

let dir;

beforeEach(async () => {
  dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'plague-state-'));
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

const good = {
  packVersion: 5,
  archives: {
    mods: { sha256: 'a'.repeat(64), files: ['mods/create.jar'] },
    config: { sha256: 'b'.repeat(64), files: ['config/create.toml'] },
  },
};

describe('чтение состояния', () => {
  it('файла нет — ничего не установлено', async () => {
    expect(await readState(dir)).toEqual({ packVersion: 0, archives: {} });
  });

  it('файл не JSON — ничего не установлено', async () => {
    await fsp.writeFile(stateFile(dir), 'обрывок {');
    expect(await readState(dir)).toEqual({ packVersion: 0, archives: {} });
  });

  it('файл JSON, но не объект — ничего не установлено', async () => {
    await fsp.writeFile(stateFile(dir), '[1,2,3]');
    expect(await readState(dir)).toEqual({ packVersion: 0, archives: {} });
  });

  it('целый файл читается как есть', async () => {
    await writeState(dir, good);
    expect(await readState(dir)).toEqual(good);
  });

  it('запись с отступами и переводом строки в конце — файл открывают руками', async () => {
    await writeState(dir, good);
    const raw = await fsp.readFile(stateFile(dir), 'utf8');

    expect(raw).toMatch(/\n$/);
    expect(raw).toContain('\n  "packVersion"');
  });

  it('битая запись про один архив выбрасывается, целые остаются', async () => {
    await fsp.writeFile(
      stateFile(dir),
      JSON.stringify({
        packVersion: 5,
        archives: {
          mods: { sha256: 'a'.repeat(64), files: ['mods/create.jar'] },
          config: { sha256: 'b'.repeat(64) },
          kubejs: { files: ['kubejs/a.js'] },
          shaderpacks: 'мусор',
          resourcepacks: { sha256: 'c'.repeat(64), files: ['ok.zip', 42] },
        },
      })
    );

    const state = await readState(dir);

    expect(Object.keys(state.archives)).toEqual(['mods']);
  });

  it('packVersion не целое — считаем нулём, а не роняем запуск', async () => {
    await fsp.writeFile(stateFile(dir), JSON.stringify({ packVersion: 'пять', archives: {} }));
    expect((await readState(dir)).packVersion).toBe(0);
  });

  it('имя файла зафиксировано: по нему его ищут в поддержке', () => {
    expect(STATE_FILE).toBe('.lmpc-pack.json');
    expect(stateFile(dir)).toBe(path.join(dir, STATE_FILE));
  });
});
