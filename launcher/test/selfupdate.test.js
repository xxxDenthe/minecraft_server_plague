// Апдейтер лаунчера. Проверяется то, что ломается молча: сравнение
// версий строкой («0.10.0» меньше «0.9.0» по алфавиту), имя файла
// с путём наружу и поход в сеть там, где раздачи нет.

import { describe, it, expect } from 'vitest';

import {
  parseLauncherRelease,
  isNewer,
  buildLauncherRelease,
  checkLauncherUpdate,
  RELEASE_ASSET,
  restartCommand,
} from '../src/main/selfupdate.js';

const descriptor = {
  version: '0.2.0',
  file: 'LMPC-Launcher-0.2.0.exe',
  size: 78123456,
  sha256: 'a'.repeat(64),
};
const good = JSON.stringify(descriptor);

describe('разбор описания релиза', () => {
  it('читает нормальное описание', () => {
    expect(parseLauncherRelease(good).version).toBe('0.2.0');
  });

  it('notes необязательны', () => {
    expect(parseLauncherRelease(good).notes).toBe('');
  });

  it('не пускает путь вместо имени файла', () => {
    const bad = JSON.stringify({ ...descriptor, file: '../evil.exe' });
    expect(() => parseLauncherRelease(bad)).toThrow(/file/);
  });

  it('не пускает короткий хеш', () => {
    const bad = JSON.stringify({ ...descriptor, sha256: 'abc' });
    expect(() => parseLauncherRelease(bad)).toThrow(/sha256/);
  });

  it('требует версию из трёх чисел', () => {
    const bad = JSON.stringify({ ...descriptor, version: 'новая' });
    expect(() => parseLauncherRelease(bad)).toThrow(/version/);
  });

  it('ответ не JSON — понятная ошибка, а не падение разбора', () => {
    expect(() => parseLauncherRelease('<html>404</html>')).toThrow(/не JSON/);
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
      version: '0.2.0',
      file: 'a.exe',
      size: 10,
      sha256: 'A'.repeat(64),
    });
    expect(built.sha256).toBe('a'.repeat(64));
  });

  it('битое значение ловится на выкладке, а не у игрока', () => {
    expect(() =>
      buildLauncherRelease({ version: '0.2', file: 'a.exe', size: 10, sha256: 'a'.repeat(64) })
    ).toThrow(/version/);
  });
});

const release = {
  tag_name: 'pack',
  assets: [
    { id: 7, name: RELEASE_ASSET },
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
      source,
      currentVersion: '0.1.0',
      fetchImpl: fakeFetch(good),
    });
    expect(update.version).toBe('0.2.0');
    expect(update.url).toBe('https://api.github.com/repos/o/r/releases/assets/8');
  });

  it('на своей же версии молчит', async () => {
    const update = await checkLauncherUpdate({
      source,
      currentVersion: '0.2.0',
      fetchImpl: fakeFetch(good),
    });
    expect(update).toBe(null);
  });

  it('без раздачи в сеть не ходит', async () => {
    const update = await checkLauncherUpdate({
      source: { kind: 'none' },
      currentVersion: '0.1.0',
      fetchImpl: () => {
        throw new Error('в сеть ходить не должны');
      },
    });
    expect(update).toBe(null);
  });

  it('описание есть, а установщика в релизе нет — говорим прямо', async () => {
    const noExe = { tag_name: 'pack', assets: [{ id: 7, name: RELEASE_ASSET }] };
    const fetchImpl = async (url) =>
      url.includes('/releases/tags/')
        ? { ok: true, status: 200, json: async () => noExe, text: async () => JSON.stringify(noExe) }
        : { ok: true, status: 200, text: async () => good };

    await expect(
      checkLauncherUpdate({ source, currentVersion: '0.1.0', fetchImpl })
    ).rejects.toThrow(/LMPC-Launcher-0\.2\.0\.exe/);
  });
});

// Кавычки вокруг путей с пробелами («D:\LMPC LAUNCHER\...») — то, что
// ломается молча: cmd без них видит два аргумента и ничего не запускает.
describe('перезапуск после установки', () => {
  const cmd = restartCommand('C:\cache\LMPC-Launcher-0.3.0.exe', 'D:\LMPC LAUNCHER\LMPC Launcher.exe');
  const line = cmd.args.at(-1);

  it('ставит тихо', () => {
    expect(line).toContain('"C:\cache\LMPC-Launcher-0.3.0.exe" /S');
  });

  it('поднимает лаунчер после установщика, взяв путь в кавычки', () => {
    expect(line).toContain('start "" "D:\LMPC LAUNCHER\LMPC Launcher.exe"');
    expect(line.indexOf('/S')).toBeLessThan(line.indexOf('start'));
  });

  it('поднимает даже если установка сорвалась', () => {
    expect(line).not.toContain('&&');
    expect(line).toContain(' & ');
  });
});
