// Своя Java 21 (спек, раздел 7). На системную Java не полагаемся:
// на машине владельца их четыре, и команда `java` отвечает не той.
//
// Берём JDK, а не JRE. Минимальный JRE от Adoptium собран jlink'ом без
// модуля `jdk.random` — а `plaguecore` на ночном тике вызывает
// `RandomGeneratorFactory.of("Xoshiro256PlusPlus")`, которого без этого
// модуля нет, и сервер падает при первой ночи (crash 2026-09-04).
// JDK тяжелее (~200 МБ против ~45), но это единственная сборка Adoptium
// со всеми стандартными модулями.

import fs from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import * as paths from './paths.js';
import { downloadFile } from './download.js';
import { unzip, stripSingleRoot } from './archive.js';
import { progressEvent, STAGES } from './progress.js';

const run = promisify(execFile);

// Путь из плана (…/jre/hotspot?project=jdk) отвечает 404: API требует
// ещё двух сегментов — размер кучи и поставщика. Проверено 2026-09-03.
// Берём не сам бинарник, а карточку релиза: в ней есть SHA-256, и архив
// проверяется тем же способом, что и всё остальное.
const ASSETS_URL = (major) =>
  `https://api.adoptium.net/v3/assets/latest/${major}/hotspot` +
  '?os=windows&architecture=x64&image_type=jdk';

export async function fetchJavaRelease(major = 21, { fetchImpl = fetch } = {}) {
  const response = await fetchImpl(ASSETS_URL(major), { redirect: 'follow' });
  if (!response.ok) throw new Error(`Adoptium ответил ${response.status}`);

  const list = await response.json();
  const pkg = list?.[0]?.binary?.package;

  if (!pkg?.link || !pkg?.checksum) {
    throw new Error('Adoptium вернул запись без ссылки или контрольной суммы');
  }

  return {
    url: pkg.link,
    sha256: pkg.checksum,
    size: pkg.size ?? 0,
    name: pkg.name ?? `temurin-${major}.zip`,
    release: list[0].release_name ?? '',
  };
}

// Разбор вывода `java -version` отделён от запуска: так его можно
// проверить тестом, не заводя на диске поддельную Java.
export function majorFromVersionOutput(text) {
  // `openjdk version "21.0.12.1" 2026-08-18 LTS` — версия в кавычках.
  // Без регулярных выражений: их обратные слэши переживают не всякий
  // перенос файла, а ошибка здесь молчалива.
  const quoted = String(text).split('"')[1] ?? '';
  const major = Number.parseInt(quoted, 10);
  return Number.isNaN(major) ? null : major;
}

// Битый архив распакуется молча, а упадёт потом, при старте игры,
// с сообщением, по которому ничего не понять. Поэтому — запуск.
export async function checkJava(exe, major = 21) {
  let text;
  try {
    const { stdout, stderr } = await run(exe, ['-version']);
    text = `${stdout}${stderr}`;
  } catch (err) {
    throw new Error(`не удалось запустить ${exe}: ${err.message}`, { cause: err });
  }

  if (majorFromVersionOutput(text) !== major) {
    throw new Error(`ожидали Java ${major}, а ${exe} отвечает: ${text.split('\n')[0]}`);
  }
  return text.trim().split('\n')[0];
}

export async function ensureJava({ major = 21, onProgress = null, fetchImpl = fetch, ...rest } = {}) {
  const exe = paths.javaExe();
  const consoleExe = paths.javaConsoleExe();

  try {
    await fs.access(exe);
    // `javac` есть только в JDK. У кого от старой версии лаунчера остался
    // урезанный JRE (без jdk.random — см. заметку про краш при ночи), этой
    // проверки не пройдёт и рантайм перекачается на полный JDK.
    await fs.access(path.join(paths.runtime(), 'bin', 'javac.exe'));
    await checkJava(consoleExe, major);
    return exe;
  } catch {
    // Нет, битая, не та версия или урезанный JRE — ставим заново.
  }

  onProgress?.(progressEvent({ stage: STAGES.JAVA, message: 'ищу Java 21' }));

  const release = await fetchJavaRelease(major, { fetchImpl });
  const archive = path.join(paths.root(), 'cache', release.name);

  let bytesDone = 0;
  await downloadFile({
    ...rest,
    url: release.url,
    dest: archive,
    sha256: release.sha256,
    onBytes: (n) => {
      bytesDone += n;
      onProgress?.(
        progressEvent({
          stage: STAGES.JAVA,
          bytesDone,
          bytesTotal: release.size,
          message: `Java ${release.release}`,
        })
      );
    },
  });

  onProgress?.(progressEvent({ stage: STAGES.JAVA, message: 'распаковываю Java' }));

  await fs.rm(paths.runtime(), { recursive: true, force: true });
  await unzip(archive, paths.runtime());
  await stripSingleRoot(paths.runtime());

  const version = await checkJava(consoleExe, major);
  onProgress?.(progressEvent({ stage: STAGES.JAVA, message: version }));

  // Архив больше не нужен: ~200 МБ на диске игрока за просто так.
  await fs.rm(archive, { force: true });

  return exe;
}
