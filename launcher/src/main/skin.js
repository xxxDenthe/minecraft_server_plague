// Скин игрока для превью на главном экране. Берётся у ely.by по нику —
// тем же источником, что и мод CustomSkinLoader в паке, поэтому в
// лаунчере и в игре игрок видит один и тот же скин. Аккаунта ely.by с
// таким ником нет → сервис отвечает 404 → лаунчер рисует заглушку.
// Скин косметический: любой сбой здесь — это заглушка, а не ошибка
// запуска, поэтому всё гасится в null.
//
// Появится своя раздача скинов (папка в релизе, свой сервер) — меняется
// только SKIN_ENDPOINT, окно про источник не знает.

const SKIN_ENDPOINT = 'https://skinsystem.ely.by/skins';
const PNG_MAGIC = 0x89504e47; // первые 4 байта любого PNG

export async function fetchSkinPng(nickname, { fetchImpl = fetch } = {}) {
  const nick = String(nickname ?? '').trim();
  if (!nick) return null;

  const url = `${SKIN_ENDPOINT}/${encodeURIComponent(nick)}.png`;

  let response;
  try {
    response = await fetchImpl(url, {
      redirect: 'follow',
      // Зависший запрос не должен держать превью в «Загрузка…» вечно.
      signal: AbortSignal.timeout?.(8000),
    });
  } catch {
    return null; // нет сети или таймаут
  }

  if (!response.ok) return null;

  const bytes = Buffer.from(await response.arrayBuffer());
  // Пустой ответ или не-PNG (HTML-заглушка сервиса) — тоже заглушка у нас.
  if (bytes.length < 8 || bytes.readUInt32BE(0) !== PNG_MAGIC) return null;

  return bytes.toString('base64');
}
