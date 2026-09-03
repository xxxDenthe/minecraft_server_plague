// Манифест мы пишем сами, но разбирает его код на чужой машине,
// с правами этого игрока. Проверки на выход за пределы папки стоят
// десяти строк, а их отсутствие — чужой Windows.

import { describe, it, expect } from 'vitest';

import { parseManifest, fetchManifest } from '../src/main/manifest.js';

// Обратный слэш через код символа: в исходнике теста он иначе
// теряется при любом переносе файла между инструментами.
const BS = String.fromCharCode(92);

const good = {
  packVersion: 12,
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  java: { major: 21 },
  launch: {
    maxRamMb: 6144,
    jvmArgs: ['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50'],
  },
  server: { host: 'plague.example.net', port: 25565 },
  managedDirs: ['mods', 'config', 'defaultconfigs', 'kubejs', 'resourcepacks', 'shaderpacks'],
  files: [
    {
      path: 'mods/create-1.21.1-6.0.10.jar',
      sha256: 'a'.repeat(64),
      size: 18234567,
      url: 'https://example.net/releases/download/pack-12/create-1.21.1-6.0.10.jar',
    },
  ],
};

const withFile = (patch) => ({ ...good, files: [{ ...good.files[0], ...patch }] });
const text = (obj) => JSON.stringify(obj);

describe('разбор манифеста: битые данные', () => {
  it('не JSON', () => {
    expect(() => parseManifest('<html>404</html>')).toThrow(/не JSON/);
  });

  it('JSON, но не объект', () => {
    expect(() => parseManifest('[1, 2, 3]')).toThrow(/объект/);
  });

  it('нет packVersion', () => {
    const { packVersion, ...rest } = good;
    expect(() => parseManifest(text(rest))).toThrow(/packVersion/);
  });

  it('packVersion не целое', () => {
    expect(() => parseManifest(text({ ...good, packVersion: 1.5 }))).toThrow(/packVersion/);
    expect(() => parseManifest(text({ ...good, packVersion: '12' }))).toThrow(/packVersion/);
  });

  it('нет версии Minecraft или NeoForge', () => {
    expect(() => parseManifest(text({ ...good, minecraft: '' }))).toThrow(/minecraft/);
    expect(() => parseManifest(text({ ...good, neoforge: 21 }))).toThrow(/neoforge/);
  });

  it('java.major не целое', () => {
    expect(() => parseManifest(text({ ...good, java: { major: '21' } }))).toThrow(/java\.major/);
  });

  it('files не массив', () => {
    expect(() => parseManifest(text({ ...good, files: {} }))).toThrow(/files/);
  });

  it('у файла нет path, sha256 или url', () => {
    expect(() => parseManifest(text(withFile({ path: undefined })))).toThrow(/path/);
    expect(() => parseManifest(text(withFile({ sha256: undefined })))).toThrow(/sha256/);
    expect(() => parseManifest(text(withFile({ url: undefined })))).toThrow(/url/);
  });

  it('sha256 не 64 шестнадцатеричных символа', () => {
    expect(() => parseManifest(text(withFile({ sha256: 'a'.repeat(63) })))).toThrow(/sha256/);
    expect(() => parseManifest(text(withFile({ sha256: 'z'.repeat(64) })))).toThrow(/sha256/);
  });

  it('размер файла не целое неотрицательное', () => {
    expect(() => parseManifest(text(withFile({ size: -1 })))).toThrow(/size/);
  });

  it('url не http и не https', () => {
    expect(() => parseManifest(text(withFile({ url: 'file:///C:/windows/system32' })))).toThrow(/url/);
  });

  // Главное, ради чего вообще писалась валидация.
  it('path с .. отвергается', () => {
    expect(() => parseManifest(text(withFile({ path: '../../windows/system32/evil.dll' })))).toThrow(/path/);
    expect(() => parseManifest(text(withFile({ path: 'mods/../../evil.jar' })))).toThrow(/path/);
  });

  it('абсолютный path отвергается', () => {
    expect(() => parseManifest(text(withFile({ path: '/etc/passwd' })))).toThrow(/path/);
    expect(() => parseManifest(text(withFile({ path: `C:${BS}windows${BS}evil.dll` })))).toThrow(/path/);
    expect(() => parseManifest(text(withFile({ path: `${BS}${BS}server${BS}share${BS}evil.dll` })))).toThrow(/path/);
  });

  it('managedDirs с .. или абсолютным путём отвергается', () => {
    expect(() => parseManifest(text({ ...good, managedDirs: ['mods', '../..'] }))).toThrow(/managedDirs/);
    expect(() => parseManifest(text({ ...good, managedDirs: [`C:${BS}`] }))).toThrow(/managedDirs/);
    expect(() => parseManifest(text({ ...good, managedDirs: 'mods' }))).toThrow(/managedDirs/);
  });

  it('два файла с одним path — ошибка сборки манифеста, а не выбор наугад', () => {
    const dup = { ...good, files: [good.files[0], { ...good.files[0] }] };
    expect(() => parseManifest(text(dup))).toThrow(/дубл/i);
  });

  it('порт сервера вне диапазона', () => {
    expect(() => parseManifest(text({ ...good, server: { host: 'a.net', port: 70000 } }))).toThrow(/port/);
  });
});

describe('разбор манифеста: корректные данные', () => {
  it('пример из спека разбирается целиком', () => {
    const m = parseManifest(text(good));

    expect(m.packVersion).toBe(12);
    expect(m.minecraft).toBe('1.21.1');
    expect(m.neoforge).toBe('21.1.249');
    expect(m.java.major).toBe(21);
    expect(m.launch.maxRamMb).toBe(6144);
    expect(m.launch.jvmArgs).toEqual(['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50']);
    expect(m.server).toEqual({ host: 'plague.example.net', port: 25565 });
    expect(m.managedDirs).toEqual(good.managedDirs);
    expect(m.files).toHaveLength(1);
    expect(m.files[0]).toEqual(good.files[0]);
  });

  it('необязательные разделы можно опустить', () => {
    const { launch, server, ...rest } = good;
    const m = parseManifest(text(rest));

    expect(m.launch.jvmArgs).toEqual([]);
    expect(m.server).toBe(null);
  });

  it('пустой files разбирается — решение, что с ним делать, принимает sync', () => {
    const m = parseManifest(text({ ...good, files: [] }));
    expect(m.files).toEqual([]);
  });

  it('разделители пути приводятся к прямому слэшу', () => {
    const m = parseManifest(text(withFile({ path: `mods${BS}create.jar` })));
    expect(m.files[0].path).toBe('mods/create.jar');
  });

  it('sha256 приводится к нижнему регистру', () => {
    const m = parseManifest(text(withFile({ sha256: 'A'.repeat(64) })));
    expect(m.files[0].sha256).toBe('a'.repeat(64));
  });
});

describe('загрузка манифеста', () => {
  it('разбирает ответ сервера', async () => {
    const fetchImpl = async () => ({ ok: true, status: 200, text: async () => text(good) });
    const m = await fetchManifest('https://example.net/pack.json', { fetchImpl });
    expect(m.packVersion).toBe(12);
  });

  it('на не-200 бросает понятную ошибку, а не отдаёт мусор', async () => {
    const fetchImpl = async () => ({ ok: false, status: 404, text: async () => 'Not Found' });
    await expect(fetchManifest('https://example.net/pack.json', { fetchImpl })).rejects.toThrow(/404/);
  });
});
