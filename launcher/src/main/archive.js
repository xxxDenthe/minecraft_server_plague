// Распаковка zip. Своего распаковщика не пишем: ошибка в нём молчалива
// (архив «распакуется», а упадёт потом, при старте игры), а Windows
// с 2018 года несёт bsdtar в комплекте. Запасной путь — Expand-Archive
// из PowerShell, он есть везде, но заметно медленнее.

import fs from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const run = promisify(execFile);

const TAR = path.join(process.env.SystemRoot ?? 'C:\Windows', 'System32', 'tar.exe');

async function exists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function unzip(archive, destination) {
  await fs.mkdir(destination, { recursive: true });

  if (await exists(TAR)) {
    await run(TAR, ['-xf', archive, '-C', destination]);
    return destination;
  }

  await run('powershell.exe', [
    '-NoProfile',
    '-NonInteractive',
    '-Command',
    `Expand-Archive -LiteralPath '${archive}' -DestinationPath '${destination}' -Force`,
  ]);
  return destination;
}

// Сборка zip для выкладки пака. Идёт только на машине владельца,
// поэтому запасного пути на PowerShell тут нет: Compress-Archive
// не умеет брать точный список файлов, а нам он нужен — из архива
// исключаются пользовательские файлы.
//
// Список передаётся через -T, а не аргументами: девяносто три мода
// не влезают в командную строку Windows целиком.
export async function zip({ sourceDir, entries, archive }) {
  if (!(await exists(TAR))) {
    throw new Error(
      `не нашёлся ${TAR}. Он идёт в составе Windows с 2018 года; ` +
        'выкладка без него не соберётся.'
    );
  }
  if (entries.length === 0) throw new Error('нечего архивировать: список файлов пуст');

  await fs.mkdir(path.dirname(archive), { recursive: true });
  await fs.rm(archive, { force: true });

  // BOM в списке bsdtar принимает за часть первого имени и падает
  // с «Couldn't visit directory». Пишем чистый UTF-8.
  const list = `${archive}.files.txt`;
  await fs.writeFile(list, `${entries.join('\n')}\n`, { encoding: 'utf8' });

  try {
    await run(TAR, ['-c', '-f', archive, '--format=zip', '-C', sourceDir, '-T', list]);
  } finally {
    await fs.rm(list, { force: true });
  }

  return archive;
}

// Архивы JDK и NeoForge кладут всё в одну папку верхнего уровня
// (jdk-21.0.12.1+1-jre и подобные). Игроку эта папка не нужна:
// пути в лаунчере фиксированные.
export async function stripSingleRoot(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  if (entries.length !== 1 || !entries[0].isDirectory()) return dir;

  const inner = path.join(dir, entries[0].name);
  for (const name of await fs.readdir(inner)) {
    await fs.rename(path.join(inner, name), path.join(dir, name));
  }
  await fs.rmdir(inner);

  return dir;
}
