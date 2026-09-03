// Правила библиотек — единственное место в разборе формата Mojang,
// где легко ошибиться молча: лишняя библиотека в classpath не мешает,
// а недостающая роняет игру уже после установки.

import { describe, it, expect } from 'vitest';

import { ruleAllows, windowsLibraries } from '../src/main/minecraft.js';

const allowOsx = [{ action: 'allow', os: { name: 'osx' } }];
const allowAll = [{ action: 'allow' }];
const allowAllExceptOsx = [
  { action: 'allow' },
  { action: 'disallow', os: { name: 'osx' } },
];
const onlyX86 = [{ action: 'allow', os: { arch: 'x86' } }];

describe('правила библиотек', () => {
  it('без правил библиотека берётся', () => {
    expect(ruleAllows(undefined)).toBe(true);
    expect(ruleAllows([])).toBe(true);
  });

  it('правило под macOS под Windows не срабатывает', () => {
    expect(ruleAllows(allowOsx)).toBe(false);
  });

  it('разрешение без условий срабатывает', () => {
    expect(ruleAllows(allowAll)).toBe(true);
  });

  it('запрет после разрешения побеждает', () => {
    expect(ruleAllows(allowAllExceptOsx, { os: 'osx' })).toBe(false);
    expect(ruleAllows(allowAllExceptOsx, { os: 'windows' })).toBe(true);
  });

  it('архитектура учитывается', () => {
    expect(ruleAllows(onlyX86, { os: 'windows', arch: 'x64' })).toBe(false);
    expect(ruleAllows(onlyX86, { os: 'windows', arch: 'x86' })).toBe(true);
  });
});

describe('отбор библиотек', () => {
  const versionJson = {
    libraries: [
      { name: 'общая', downloads: { artifact: { path: 'a/a.jar', sha1: 'a', size: 1, url: 'https://x/a.jar' } } },
      { name: 'маковая', rules: allowOsx, downloads: { artifact: { path: 'b/b.jar', sha1: 'b', size: 1, url: 'https://x/b.jar' } } },
      { name: 'без артефакта', rules: allowAll, downloads: {} },
    ],
  };

  it('берёт нужные под Windows и не спотыкается о записи без артефакта', () => {
    const libs = windowsLibraries(versionJson);
    expect(libs.map((l) => l.path)).toEqual(['a/a.jar']);
  });
});
