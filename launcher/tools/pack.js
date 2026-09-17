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
 * Имена наших модов. Они меняются по нескольку раз за вечер, тогда как
 * чужие сто пятьдесят пять — раз в месяц, и ехать вместе им незачем.
 */
const OWN_MOD_PREFIXES = ['plaguecore', 'lmpc_'];

/** Наш ли это джарник. Решает по имени файла, а не по пути. */
export function isOwnMod(relative) {
  const name = relative.split('/').pop() ?? '';
  return OWN_MOD_PREFIXES.some((prefix) => name.startsWith(prefix));
}

/**
 * На какие архивы распадается папка.
 *
 * Все папки, кроме `mods`, остаются одним архивом со своим именем.
 * `mods` делится надвое: `mods-core` — чужие моды, `mods-lmpc` — наши.
 * Выигрыш обоюдный: выкладка правки мода при канале 0.25 МБ/с стоит
 * десять секунд вместо получаса, и игрок качает 3 МБ вместо 369.
 *
 * Пустая половина архивом не становится: лаунчер вычистил бы под неё
 * папку и распаковал туда ничего.
 */
export function splitArchives(dir, files) {
  if (dir !== 'mods') return [{ name: dir, dir, files }];

  const own = files.filter((f) => isOwnMod(f.path));
  const rest = files.filter((f) => !isOwnMod(f.path));

  return [
    { name: 'mods-core', dir, files: rest },
    { name: 'mods-lmpc', dir, files: own },
  ].filter((part) => part.files.length > 0);
}

/**
 * Что переиспользовать из прошлого манифеста, а что собирать заново.
 *
 * Переиспользуем только при полном совпадении отпечатка и при живой
 * ссылке: манифест без contentId — от прошлой ревизии формата, ему
 * верить нельзя.
 */
export function planUpload(dirs, previous = null) {
  // Ключ — имя архива, а не папка: в `mods` их теперь два. У манифеста
  // старого образца имени нет, и там имя равно папке.
  const old = new Map((previous?.archives ?? []).map((a) => [a.name ?? a.dir, a]));

  const reuse = [];
  const build = [];

  for (const dir of dirs) {
    const before = old.get(dir.name ?? dir.dir);

    if (before?.contentId && before.contentId === dir.contentId && before.url) {
      reuse.push(before);
    } else {
      build.push(dir);
    }
  }

  return { reuse, build };
}
