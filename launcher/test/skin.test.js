// Превью скина косметическое: любой сбой сети или сервиса должен
// превращаться в null (заглушку), а не ронять запуск. Здесь это и
// проверяется — плюс что пустой ник не порождает запрос.

import { describe, it, expect } from 'vitest';

import { fetchSkinPng } from '../src/main/skin.js';

const PNG = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4]);
const okWith = (bytes) => async () => ({ ok: true, arrayBuffer: async () => bytes.buffer });

describe('fetchSkinPng', () => {
  it('отдаёт base64, когда сервис вернул PNG', async () => {
    const b64 = await fetchSkinPng('Kuragane', { fetchImpl: okWith(PNG) });
    expect(Uint8Array.from(Buffer.from(b64, 'base64'))).toEqual(PNG);
  });

  it('404 (нет аккаунта ely.by) → null', async () => {
    expect(await fetchSkinPng('nobody', { fetchImpl: async () => ({ ok: false, status: 404 }) })).toBeNull();
  });

  it('сбой сети → null', async () => {
    expect(await fetchSkinPng('Kuragane', { fetchImpl: async () => { throw new Error('ENOTFOUND'); } })).toBeNull();
  });

  it('ответ не PNG (HTML-заглушка) → null', async () => {
    const html = Uint8Array.from(Buffer.from('<!doctype html><body>404'));
    expect(await fetchSkinPng('x', { fetchImpl: okWith(html) })).toBeNull();
  });

  it('пустой ник → null и без запроса', async () => {
    let called = false;
    const fetchImpl = async () => { called = true; return { ok: true, arrayBuffer: async () => PNG.buffer }; };
    expect(await fetchSkinPng('   ', { fetchImpl })).toBeNull();
    expect(called).toBe(false);
  });

  it('ник уходит в URL закодированным', async () => {
    let seen;
    await fetchSkinPng('Игрок 1', { fetchImpl: async (url) => { seen = url; return { ok: false }; } });
    expect(seen).toBe(`https://skinsystem.ely.by/skins/${encodeURIComponent('Игрок 1')}.png`);
  });
});
