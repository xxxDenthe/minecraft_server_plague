// Раздача пака через приватный релиз GitHub (спек, раздел 12).
//
// Три особенности, из-за которых нельзя обойтись обычными адресами:
//
// 1. У приватного релиза browser_download_url не работает — качать можно
//    только через API, по id ассета, с токеном и Accept: octet-stream.
// 2. GitHub переименовывает ассеты со спецсимволами: «+» превращается
//    в «.». У 21 мода нашего пака «+» в имени, так что опираться
//    на имена нельзя.
// 3. Список ассетов плоский, папок в нём нет.
//
// Поэтому путь файла живёт в манифесте, а адресом служит id ассета.

const API = 'https://api.github.com';

// Токен, скопированный с лишним символом, роняет fetch на попытке
// собрать заголовок — сообщение получается про ByteString и индекс,
// по нему не догадаться, что виноват буфер обмена.
export function checkToken(token) {
  if (!token) throw new Error('токен пустой');

  const bad = [...token].findIndex((ch) => ch.charCodeAt(0) > 127 || ch.charCodeAt(0) < 33);
  if (bad >= 0) {
    throw new Error(
      `в токене есть посторонний символ на позиции ${bad + 1} — похоже, ` +
        'при копировании прихватились лишние знаки. Скопируйте токен заново.'
    );
  }

  return token;
}

export function apiHeaders(token, accept = 'application/vnd.github+json') {
  const headers = {
    Accept: accept,
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'LMPCLauncher',
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

export const assetUrl = ({ owner, repo, assetId }) =>
  `${API}/repos/${owner}/${repo}/releases/assets/${assetId}`;

// Тег стабилен, id релиза — нет. Поэтому вход всегда через тег:
// адрес, который можно зашить в сборку и не менять.
export async function releaseByTag({ owner, repo, tag, token = '', fetchImpl = fetch }) {
  const url = `${API}/repos/${owner}/${repo}/releases/tags/${encodeURIComponent(tag)}`;
  const response = await fetchImpl(url, { headers: apiHeaders(token), redirect: 'follow' });

  if (response.status === 404) {
    // Четыре причины, и по коду ответа они неразличимы. Самая частая —
    // релиз сохранён черновиком: тега у черновика ещё нет, по тегу он
    // не ищется, и лаунчеру игрока он тоже не виден.
    throw new Error(
      `релиза с тегом «${tag}» нет в ${owner}/${repo}.\n` +
        '  Причина — одна из четырёх: релиз не создан; сохранён черновиком,\n' +
        '  а не опубликован; тег написан иначе; токен не видит репозиторий.\n' +
        '  Что именно — покажет: node tools/check-access.js --repo ' +
        `${owner}/${repo} --tag ${tag} --token <токен>`
    );
  }
  if (!response.ok) {
    throw new Error(`GitHub ответил ${response.status} на запрос релиза «${tag}»`);
  }

  return response.json();
}

export function findAsset(release, name) {
  const asset = (release.assets ?? []).find((a) => a.name === name);
  if (!asset) {
    throw new Error(`в релизе «${release.tag_name}» нет ассета «${name}»`);
  }
  return asset;
}

// Манифест лежит в том же релизе. Читаем его через тег, а не по
// прямому адресу: при перезаливке id ассета меняется, а тег — нет.
export async function fetchPackManifestText({ owner, repo, tag, token = '', fetchImpl = fetch, name = 'pack.json' }) {
  const release = await releaseByTag({ owner, repo, tag, token, fetchImpl });
  const asset = findAsset(release, name);

  const response = await fetchImpl(assetUrl({ owner, repo, assetId: asset.id }), {
    headers: apiHeaders(token, 'application/octet-stream'),
    redirect: 'follow',
  });

  if (!response.ok) {
    throw new Error(`GitHub ответил ${response.status} на скачивание ${name}`);
  }

  return response.text();
}
