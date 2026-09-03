// Три особенности приватного релиза, из-за которых модуль вообще
// существует: адрес по id, а не по имени; переименование ассетов
// с «+» в имени; плоский список без папок.

import { describe, it, expect } from 'vitest';

import { apiHeaders, assetUrl, findAsset, releaseByTag, fetchPackManifestText, checkToken } from '../src/main/github.js';

const release = {
  tag_name: 'pack',
  assets: [
    { id: 101, name: 'pack.json', size: 12 },
    // GitHub переименовал: в исходнике было «curios-neoforge-9.5.1+1.21.1.jar»
    { id: 102, name: 'mods__curios-neoforge-9.5.1.1.21.1.jar', size: 100 },
  ],
};

const ok = (body) => ({
  ok: true,
  status: 200,
  json: async () => body,
  text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
});

describe('заголовки', () => {
  it('без токена Authorization не уходит', () => {
    expect(apiHeaders('').Authorization).toBe(undefined);
  });

  it('для файла нужен Accept: octet-stream', () => {
    expect(apiHeaders('t', 'application/octet-stream').Accept).toBe('application/octet-stream');
  });
});

describe('адрес ассета', () => {
  it('строится по id, а не по имени файла', () => {
    expect(assetUrl({ owner: 'o', repo: 'r', assetId: 102 })).toBe(
      'https://api.github.com/repos/o/r/releases/assets/102'
    );
  });
});

describe('поиск релиза и ассета', () => {
  it('релиз ищется по тегу — id релиза меняется, тег нет', async () => {
    let asked = '';
    const fetchImpl = async (url) => {
      asked = url;
      return ok(release);
    };

    await releaseByTag({ owner: 'o', repo: 'r', tag: 'pack', fetchImpl });
    expect(asked).toBe('https://api.github.com/repos/o/r/releases/tags/pack');
  });

  it('404 объясняет обе причины: нет релиза или токен его не видит', async () => {
    const fetchImpl = async () => ({ ok: false, status: 404 });
    await expect(releaseByTag({ owner: 'o', repo: 'r', tag: 'pack', fetchImpl })).rejects.toThrow(
      /нет в o\/r.*токен/s
    );
  });

  it('пропавший ассет — понятная ошибка, а не undefined дальше по коду', () => {
    expect(() => findAsset(release, 'нет-такого.json')).toThrow(/нет ассета/);
  });
});

describe('чтение манифеста из релиза', () => {
  it('идёт в два шага: тег → id ассета → файл', async () => {
    const asked = [];
    const fetchImpl = async (url, options) => {
      asked.push([url, options.headers.Accept]);
      if (url.endsWith('/tags/pack')) return ok(release);
      return ok('{"packVersion":3}');
    };

    const text = await fetchPackManifestText({ owner: 'o', repo: 'r', tag: 'pack', token: 't', fetchImpl });

    expect(text).toBe('{"packVersion":3}');
    expect(asked[0][0]).toContain('/releases/tags/pack');
    expect(asked[1][0]).toBe('https://api.github.com/repos/o/r/releases/assets/101');
    expect(asked[1][1]).toBe('application/octet-stream');
  });
});

describe('проверка токена перед запросом', () => {
  it('пустой токен — понятная ошибка', () => {
    expect(() => checkToken('')).toThrow(/пустой/);
  });

  it('обычный токен проходит', () => {
    expect(checkToken('github_pat_11AAAAA_bbbCCC')).toBe('github_pat_11AAAAA_bbbCCC');
  });

  // Без этой проверки fetch падает сообщением про ByteString и индекс
  // символа — по нему не догадаться, что виноват буфер обмена.
  it('кириллица в токене ловится до запроса, с указанием места', () => {
    expect(() => checkToken('github_pat_РУССКИЕ')).toThrow(/позиции 12/);
  });

  it('пробел и перевод строки в конце тоже ловятся', () => {
    expect(() => checkToken('github_pat_11AAA ')).toThrow(/посторонний символ/);
    expect(() => checkToken('github_pat_11AAA\n')).toThrow(/посторонний символ/);
  });
});
