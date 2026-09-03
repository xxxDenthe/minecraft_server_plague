// Конфиг обязан переживать порчу файла: игрок не должен упереться
// в «не удалось разобрать JSON» за пятнадцать минут до сессии.

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { readConfig, writeConfig, DEFAULTS } from '../src/main/config.js';
import * as paths from '../src/main/paths.js';

let temp;
let savedRoot;
const warnings = [];
const onWarn = (m) => warnings.push(m);

beforeEach(() => {
  savedRoot = process.env.PLAGUE_LAUNCHER_ROOT;
  temp = fs.mkdtempSync(path.join(os.tmpdir(), 'plague-config-'));
  process.env.PLAGUE_LAUNCHER_ROOT = temp;
  warnings.length = 0;
});

afterEach(() => {
  if (savedRoot === undefined) delete process.env.PLAGUE_LAUNCHER_ROOT;
  else process.env.PLAGUE_LAUNCHER_ROOT = savedRoot;
  fs.rmSync(temp, { recursive: true, force: true });
});

describe('конфиг', () => {
  it('без файла отдаёт значения по умолчанию и молчит', () => {
    expect(readConfig({ onWarn })).toEqual({ ...DEFAULTS });
    expect(warnings).toEqual([]);
  });

  it('испорченный JSON заменяется дефолтом с записью в лог', () => {
    fs.writeFileSync(paths.configFile(), '{ это не json', 'utf8');
    expect(readConfig({ onWarn })).toEqual({ ...DEFAULTS });
    expect(warnings.length).toBe(1);
  });

  it('поля неверных типов заменяются по одному', () => {
    fs.writeFileSync(
      paths.configFile(),
      JSON.stringify({ nickname: 'Denthe', maxRamMb: 'много', packVersion: 12.5 }),
      'utf8'
    );
    expect(readConfig({ onWarn })).toEqual({
      ...DEFAULTS,
      nickname: 'Denthe',
    });
  });

  it('записанное читается обратно', () => {
    writeConfig({ nickname: 'Denthe', maxRamMb: 8192, packVersion: 3, lastPlayed: '2026-09-03T12:00:00Z' });
    expect(readConfig({ onWarn })).toEqual({
      nickname: 'Denthe',
      maxRamMb: 8192,
      packVersion: 3,
      lastPlayed: '2026-09-03T12:00:00Z',
    });
  });

  it('запись создаёт корень, если его ещё нет', () => {
    fs.rmSync(temp, { recursive: true, force: true });
    writeConfig({ nickname: 'Kuragane' });
    expect(readConfig({ onWarn }).nickname).toBe('Kuragane');
  });
});
