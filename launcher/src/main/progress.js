// Единый формат событий прогресса. Окно не знает ничего, кроме этих
// полей, — иначе каждый новый шаг установки тянул бы за собой правку
// интерфейса.

export const STAGES = Object.freeze({
  MANIFEST: 'manifest',
  JAVA: 'java',
  MINECRAFT: 'minecraft',
  ASSETS: 'assets',
  NEOFORGE: 'neoforge',
  PACK: 'pack',
  LAUNCH: 'launch',
});

export function progressEvent({
  stage,
  current = 0,
  total = 0,
  bytesDone = 0,
  bytesTotal = 0,
  message = '',
} = {}) {
  return { stage, current, total, bytesDone, bytesTotal, message };
}

// Доля выполненного там, где её можно посчитать. На ассетах полезнее
// «1240 из 4100», чем застывшие 30% — но полоску всё же чем-то надо
// заполнять.
export function fraction({ current, total, bytesDone, bytesTotal }) {
  if (bytesTotal > 0) return Math.min(1, bytesDone / bytesTotal);
  if (total > 0) return Math.min(1, current / total);
  return 0;
}
