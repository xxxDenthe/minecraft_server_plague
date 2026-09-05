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
  archives: [
    {
      dir: 'mods',
      sha256: 'a'.repeat(64),
      contentId: 'c'.repeat(64),
      size: 274513920,
      url: 'https://api.github.com/repos/o/r/releases/assets/12345',
    },
  ],
};

const withArchive = (patch) => ({ ...good, archives: [{ ...good.archives[0], ...patch }] });
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

  it('archives не массив', () => {
    expect(() => parseManifest(text({ ...good, archives: {} }))).toThrow(/archives/);
  });

  it('манифест старого образца с files не разбирается молча', () => {
    const { archives, ...rest } = good;
    expect(() => parseManifest(text({ ...rest, files: [] }))).toThrow(/archives/);
  });

  it('у архива нет dir, sha256 или url', () => {
    expect(() => parseManifest(text(withArchive({ dir: undefined })))).toThrow(/dir/);
    expect(() => parseManifest(text(withArchive({ sha256: undefined })))).toThrow(/sha256/);
    expect(() => parseManifest(text(withArchive({ url: undefined })))).toThrow(/url/);
  });

  it('sha256 не 64 шестнадцатеричных символа', () => {
    expect(() => parseManifest(text(withArchive({ sha256: 'a'.repeat(63) })))).toThrow(/sha256/);
    expect(() => parseManifest(text(withArchive({ sha256: 'z'.repeat(64) })))).toThrow(/sha256/);
  });

  it('contentId не строка', () => {
    expect(() => parseManifest(text(withArchive({ contentId: 42 })))).toThrow(/contentId/);
  });

  it('размер архива не целое неотрицательное', () => {
    expect(() => parseManifest(text(withArchive({ size: -1 })))).toThrow(/size/);
  });

  it('url не http и не https', () => {
    expect(() => parseManifest(text(withArchive({ url: 'file:///C:/windows/system32' })))).toThrow(/url/);
  });

  // Главное, ради чего вообще писалась валидация.
  it('dir с .. отвергается', () => {
    expect(() => parseManifest(text(withArchive({ dir: '../../windows/system32' })))).toThrow(/dir/);
    expect(() => parseManifest(text(withArchive({ dir: 'mods/../..' })))).toThrow(/dir/);
  });

  it('абсолютный dir отвергается', () => {
    expect(() => parseManifest(text(withArchive({ dir: '/etc' })))).toThrow(/dir/);
    expect(() => parseManifest(text(withArchive({ dir: `C:${BS}windows` })))).toThrow(/dir/);
    expect(() => parseManifest(text(withArchive({ dir: `${BS}${BS}server${BS}share` })))).toThrow(/dir/);
  });

  it('dir из нескольких сегментов отвергается: чистилась бы не та папка', () => {
    const m = { ...good, managedDirs: [...good.managedDirs, 'config/create'] };
    expect(() => parseManifest(text({ ...m, archives: [{ ...good.archives[0], dir: 'config/create' }] }))).toThrow(
      /dir/
    );
  });

  it('dir вне managedDirs отвергается: чистить папку, которой мы не владеем, нельзя', () => {
    expect(() => parseManifest(text(withArchive({ dir: 'journeymap' })))).toThrow(/managedDirs/);
  });

  it('managedDirs с .. или абсолютным путём отвергается', () => {
    expect(() => parseManifest(text({ ...good, managedDirs: ['mods', '../..'] }))).toThrow(/managedDirs/);
    expect(() => parseManifest(text({ ...good, managedDirs: [`C:${BS}`] }))).toThrow(/managedDirs/);
    expect(() => parseManifest(text({ ...good, managedDirs: 'mods' }))).toThrow(/managedDirs/);
  });

  it('два архива на одну папку — ошибка сборки манифеста, а не выбор наугад', () => {
    const dup = { ...good, archives: [good.archives[0], { ...good.archives[0] }] };
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
    expect(m.archives).toHaveLength(1);
    expect(m.archives[0]).toEqual(good.archives[0]);
  });

  it('необязательные разделы можно опустить', () => {
    const { launch, server, ...rest } = good;
    const m = parseManifest(text(rest));

    expect(m.launch.jvmArgs).toEqual([]);
    expect(m.server).toBe(null);
  });

  it('contentId можно опустить: лаунчер его не читает', () => {
    const { contentId, ...archive } = good.archives[0];
    const m = parseManifest(text({ ...good, archives: [archive] }));

    expect(m.archives[0].contentId).toBe('');
  });

  it('пустой archives разбирается — решение, что с ним делать, принимает sync', () => {
    const m = parseManifest(text({ ...good, archives: [] }));
    expect(m.archives).toEqual([]);
  });

  it('sha256 приводится к нижнему регистру', () => {
    const m = parseManifest(text(withArchive({ sha256: 'A'.repeat(64) })));
    expect(m.archives[0].sha256).toBe('a'.repeat(64));
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
