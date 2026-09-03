// Контрольные значения не выдуманы: они получены прогоном
// java.util.UUID.nameUUIDFromBytes("OfflinePlayer:<ник>") на JDK —
// это ровно то, что вызывает сервер в offline-режиме
// (Player.createPlayerUUID). Совпадать надо с чужой реализацией,
// а не с нашим представлением о ней.
//
// Цена ошибки: игра стартует, игрок заходит, всё работает — и только
// на второй день выясняется, что каждый вход создаёт нового персонажа.

import { describe, it, expect } from 'vitest';

import { offlineUuid, offlineUuidCompact } from '../src/main/offline.js';

const REFERENCE = {
  Denthe: '45063c6a-9a85-3556-a5c6-22d3eccedbf9',
  Kuragane: '5c5ae9a6-e2d3-370c-bbde-c76d72265740',
  player1: '0cb1fa9b-846a-3cda-a1d9-5a9d6939ce14',
  abcdefghijklmnop: '9dc76558-520f-3560-86b0-b5c7c500848b',
  some_nick_16chr: '32102ff4-9d89-3bfd-9c3f-5b8234763098',
  a: '52428a0e-1e30-3cb1-976c-e728b2614047',
  Notch: 'b50ad385-829d-3141-a216-7e7d7539ba7f',
  'Игрок': '770c6db4-4435-39fa-8907-9b7f4fb309b1',
};

describe('UUID оффлайн-игрока', () => {
  for (const [nickname, expected] of Object.entries(REFERENCE)) {
    it(`совпадает с сервером для «${nickname}»`, () => {
      expect(offlineUuid(nickname)).toBe(expected);
    });
  }

  it('версия 3 и вариант RFC 4122', () => {
    for (const uuid of Object.values(REFERENCE)) {
      expect(uuid[14]).toBe('3');
      expect('89ab').toContain(uuid[19]);
    }
    expect(offlineUuid('кто угодно')[14]).toBe('3');
  });

  it('один ник — один UUID, каждый раз', () => {
    expect(offlineUuid('Denthe')).toBe(offlineUuid('Denthe'));
  });

  it('регистр ника значим — сервер его тоже различает', () => {
    expect(offlineUuid('Denthe')).not.toBe(offlineUuid('denthe'));
  });

  it('форма без дефисов — для аргументов запуска', () => {
    expect(offlineUuidCompact('Denthe')).toBe('45063c6a9a853556a5c622d3eccedbf9');
  });

  it('пустой ник — ошибка, а не UUID', () => {
    expect(() => offlineUuid('')).toThrow(/ник/);
    expect(() => offlineUuid('   ')).toThrow(/ник/);
    expect(() => offlineUuid(null)).toThrow(/ник/);
  });
});
