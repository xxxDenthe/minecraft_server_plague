// Вход в offline-режиме (спек, раздел 11). Сервер считает UUID игрока
// как UUID версии 3 от строки «OfflinePlayer:<ник>» — повторяем ровно
// тот же расчёт, иначе игрок при каждом входе получает нового
// персонажа и теряет инвентарь, дом и прогресс класса.
//
// Если позже понадобится вход через Microsoft, меняется только этот
// модуль: остальные о способе входа не знают.

import { createHash } from 'node:crypto';

export function offlineUuidCompact(nickname) {
  if (typeof nickname !== 'string' || nickname.trim() === '') {
    throw new Error('ник пустой: UUID считать не от чего');
  }

  const bytes = createHash('md5')
    .update(Buffer.from(`OfflinePlayer:${nickname}`, 'utf8'))
    .digest();

  // Версия 3 в старшие четыре бита седьмого байта, вариант RFC 4122 —
  // в старшие два бита девятого. Так же делает java.util.UUID.
  bytes[6] = (bytes[6] & 0x0f) | 0x30;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  return bytes.toString('hex');
}

export function offlineUuid(nickname) {
  const hex = offlineUuidCompact(nickname);

  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join('-');
}
