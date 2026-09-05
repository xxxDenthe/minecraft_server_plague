// Чистая часть выкладки: чем описывается содержимое папки и когда его
// можно не перезаливать. Без сети и без диска — потому и проверяется
// обычными тестами.

import { createHash } from 'node:crypto';

/**
 * Отпечаток содержимого папки: хеш от отсортированных строк
 * «путь пробел sha256».
 *
 * Зачем он, если у архива есть свой sha256: побайтовой
 * воспроизводимости zip `tar` не обещает. Опирайся мы на хеш архива,
 * любая пересборка выглядела бы изменением и гнала бы 262 МБ вхолостую.
 */
export function contentIdOf(files) {
  const lines = files
    .map((f) => `${f.path} ${f.sha256.toLowerCase()}`)
    .sort()
    .join('\n');

  return createHash('sha256').update(lines, 'utf8').digest('hex');
}

/**
 * Что переиспользовать из прошлого манифеста, а что собирать заново.
 *
 * Переиспользуем только при полном совпадении отпечатка и при живой
 * ссылке: манифест без contentId — от прошлой ревизии формата, ему
 * верить нельзя.
 */
export function planUpload(dirs, previous = null) {
  const old = new Map((previous?.archives ?? []).map((a) => [a.dir, a]));

  const reuse = [];
  const build = [];

  for (const dir of dirs) {
    const before = old.get(dir.dir);

    if (before?.contentId && before.contentId === dir.contentId && before.url) {
      reuse.push(before);
    } else {
      build.push(dir);
    }
  }

  return { reuse, build };
}
