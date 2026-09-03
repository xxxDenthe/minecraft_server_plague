// Сборка всего вместе. Живая установка проверяется руками (см. коммиты
// задач 8–11), здесь — то, что решается без сети и без диска.

import { describe, it, expect, vi } from 'vitest';

import { manifestHeaders, loadManifest, watchForUpdates } from '../src/main/install.js';

const manifestText = (packVersion) =>
  JSON.stringify({
    packVersion,
    minecraft: '1.21.1',
    neoforge: '21.1.249',
    managedDirs: ['mods'],
    files: [
      { path: 'mods/a.jar', sha256: 'a'.repeat(64), size: 1, url: 'https://example.net/a.jar' },
    ],
  });

const answer = (text) => async () => ({ ok: true, status: 200, text: async () => text });

describe('доступ к приватному релизу', () => {
  it('без токена Authorization не уходит', () => {
    expect(manifestHeaders('').Authorization).toBe(undefined);
  });

  it('с токеном уходит Authorization', () => {
    expect(manifestHeaders('ghp_x').Authorization).toBe('Bearer ghp_x');
  });

  it('Accept — octet-stream: иначе GitHub отдаст описание ассета, а не файл', () => {
    expect(manifestHeaders('ghp_x').Accept).toBe('application/octet-stream');
  });
});

describe('манифест до первой раздачи', () => {
  it('без адреса лаунчер работает без пака, а не падает', async () => {
    await expect(loadManifest({ source: { kind: 'none' } })).resolves.toBe(null);
  });

  it('с адресом манифест разбирается', async () => {
    const m = await loadManifest({ source: { kind: 'url', url: 'https://example.net/pack.json' }, fetchImpl: answer(manifestText(7)) });
    expect(m.packVersion).toBe(7);
  });
});

describe('слежение за обновлениями', () => {
  it('без адреса ничего не опрашивает', () => {
    const stop = watchForUpdates({ knownVersion: 1, source: { kind: 'none' } });
    expect(typeof stop).toBe('function');
    stop();
  });

  it('замечает рост packVersion один раз, а не на каждом опросе', async () => {
    vi.useFakeTimers();
    const seen = [];

    const stop = watchForUpdates({
      knownVersion: 5,
      intervalMs: 1000,
      source: { kind: 'url', url: 'https://example.net/pack.json' },
      fetchImpl: answer(manifestText(6)),
      onUpdate: (v) => seen.push(v),
    });

    await vi.advanceTimersByTimeAsync(1000);
    await vi.advanceTimersByTimeAsync(1000);
    stop();
    vi.useRealTimers();

    expect(seen).toEqual([6]);
  });

  it('та же версия — молчит', async () => {
    vi.useFakeTimers();
    const seen = [];

    const stop = watchForUpdates({
      knownVersion: 6,
      intervalMs: 1000,
      source: { kind: 'url', url: 'https://example.net/pack.json' },
      fetchImpl: answer(manifestText(6)),
      onUpdate: (v) => seen.push(v),
    });

    await vi.advanceTimersByTimeAsync(3000);
    stop();
    vi.useRealTimers();

    expect(seen).toEqual([]);
  });

  it('пропавшая сеть не роняет лаунчер посреди игры', async () => {
    vi.useFakeTimers();
    const errors = [];

    const stop = watchForUpdates({
      knownVersion: 1,
      intervalMs: 1000,
      source: { kind: 'url', url: 'https://example.net/pack.json' },
      fetchImpl: async () => {
        throw new Error('сеть отвалилась');
      },
      onError: (e) => errors.push(e.message),
    });

    await vi.advanceTimersByTimeAsync(2000);
    stop();
    vi.useRealTimers();

    expect(errors.length).toBeGreaterThan(0);
  });

  it('остановка прекращает опрос', async () => {
    vi.useFakeTimers();
    let calls = 0;

    const stop = watchForUpdates({
      knownVersion: 1,
      intervalMs: 1000,
      source: { kind: 'url', url: 'https://example.net/pack.json' },
      fetchImpl: async () => {
        calls += 1;
        return { ok: true, status: 200, text: async () => manifestText(1) };
      },
    });

    await vi.advanceTimersByTimeAsync(1000);
    stop();
    await vi.advanceTimersByTimeAsync(5000);
    vi.useRealTimers();

    expect(calls).toBe(1);
  });
});
