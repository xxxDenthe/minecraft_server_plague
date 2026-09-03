// Манифест — единственный источник истины о том, как выглядит клиент
// (спек, раздел 4). Разбор без сторонних схем: проверок полтора десятка,
// и явный код читается лучше, чем декларация плюс чужая библиотека.

const SHA256 = /^[0-9a-f]{64}$/i;

// Обратный слэш через код символа: строковый литерал с ним переживает
// не всякий перенос файла между инструментами, а ошибка тут молчаливая.
const BACKSLASH = String.fromCharCode(92);

function fail(message) {
  throw new Error(`манифест: ${message}`);
}

const isInt = (v) => Number.isInteger(v);
const isText = (v) => typeof v === 'string' && v.trim() !== '';

// Путь из манифеста склеивается с игровой папкой на чужой машине.
// Всё, что может увести наружу, отсекается здесь.
function checkRelativePath(value, field) {
  if (!isText(value)) fail(`${field}: путь пустой или не строка`);

  const normalized = value.replaceAll(BACKSLASH, '/');

  if (normalized.startsWith('/')) fail(`${field}: абсолютный путь «${value}»`);
  if (/^[a-zA-Z]:/.test(normalized)) fail(`${field}: путь с буквой диска «${value}»`);
  if (normalized.split('/').includes('..')) fail(`${field}: путь выходит наружу «${value}»`);
  if (normalized.includes('\0')) fail(`${field}: путь с нулевым байтом`);

  return normalized;
}

function parseFile(raw, index) {
  const at = `files[${index}]`;
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) fail(`${at}: не объект`);

  const path = checkRelativePath(raw.path, `${at}.path`);

  if (!isText(raw.sha256) || !SHA256.test(raw.sha256)) {
    fail(`${at}.sha256: не 64 шестнадцатеричных символа`);
  }

  if (!isText(raw.url)) fail(`${at}.url: пустой или не строка`);
  let url;
  try {
    url = new URL(raw.url);
  } catch {
    fail(`${at}.url: не разбирается как адрес`);
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    fail(`${at}.url: протокол «${url.protocol}» не поддерживается`);
  }

  if (raw.size !== undefined && (!isInt(raw.size) || raw.size < 0)) {
    fail(`${at}.size: не целое неотрицательное`);
  }

  return {
    path,
    sha256: raw.sha256.toLowerCase(),
    size: raw.size ?? 0,
    url: raw.url,
  };
}

export function parseManifest(text) {
  let raw;
  try {
    raw = JSON.parse(text);
  } catch (err) {
    fail(`ответ не JSON (${err.message})`);
  }

  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) fail('не объект');

  if (!isInt(raw.packVersion) || raw.packVersion < 0) fail('packVersion: не целое неотрицательное');
  if (!isText(raw.minecraft)) fail('minecraft: пустая версия');
  if (!isText(raw.neoforge)) fail('neoforge: пустая версия');

  const javaMajor = raw.java?.major ?? 21;
  if (!isInt(javaMajor)) fail('java.major: не целое');

  const launchRaw = raw.launch ?? {};
  if (launchRaw === null || typeof launchRaw !== 'object' || Array.isArray(launchRaw)) fail('launch: не объект');
  if (launchRaw.maxRamMb !== undefined && (!isInt(launchRaw.maxRamMb) || launchRaw.maxRamMb <= 0)) {
    fail('launch.maxRamMb: не целое положительное');
  }
  const jvmArgs = launchRaw.jvmArgs ?? [];
  if (!Array.isArray(jvmArgs) || jvmArgs.some((a) => typeof a !== 'string')) {
    fail('launch.jvmArgs: не массив строк');
  }

  let server = null;
  if (raw.server != null) {
    if (typeof raw.server !== 'object' || Array.isArray(raw.server)) fail('server: не объект');
    if (!isText(raw.server.host)) fail('server.host: пустой');
    if (!isInt(raw.server.port) || raw.server.port < 1 || raw.server.port > 65535) {
      fail('server.port: не порт');
    }
    server = { host: raw.server.host, port: raw.server.port };
  }

  if (!Array.isArray(raw.managedDirs)) fail('managedDirs: не массив');
  const managedDirs = raw.managedDirs.map((dir, i) =>
    checkRelativePath(dir, `managedDirs[${i}]`)
  );

  if (!Array.isArray(raw.files)) fail('files: не массив');
  const files = raw.files.map(parseFile);

  // Два файла с одним путём — не выбор наугад, а ошибка сборки манифеста.
  const seen = new Set();
  for (const file of files) {
    if (seen.has(file.path)) fail(`дубликат пути «${file.path}»`);
    seen.add(file.path);
  }

  return {
    packVersion: raw.packVersion,
    minecraft: raw.minecraft,
    neoforge: raw.neoforge,
    java: { major: javaMajor },
    launch: {
      maxRamMb: launchRaw.maxRamMb ?? null,
      jvmArgs: [...jvmArgs],
    },
    server,
    managedDirs,
    files,
  };
}

export async function fetchManifest(url, { fetchImpl = fetch, headers = {} } = {}) {
  const response = await fetchImpl(url, { headers, redirect: 'follow' });

  if (!response.ok) {
    throw new Error(`манифест: сервер ответил ${response.status} на ${url}`);
  }

  return parseManifest(await response.text());
}
