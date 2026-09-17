// Отпечаток содержимого решает, поедут ли по сети 262 МБ. Ошибка в нём
// молчалива в обе стороны: либо лишняя перезаливка каждый раз, либо
// игроки со старыми модами и правильным номером версии.

import { describe, it, expect } from 'vitest';

import { contentIdOf, planUpload, splitArchives, isOwnMod } from '../tools/pack.js';
import { zip } from '../src/main/archive.js';

const file = (path, body) => ({ path, sha256: body.repeat(64).slice(0, 64) });

const mods = [file('mods/create.jar', 'a'), file('mods/jei.jar', 'b')];

describe('отпечаток содержимого', () => {
  it('не зависит от порядка обхода папки', () => {
    expect(contentIdOf(mods)).toBe(contentIdOf([...mods].reverse()));
  });

  it('регистр хеша не влияет', () => {
    const upper = mods.map((f) => ({ ...f, sha256: f.sha256.toUpperCase() }));
    expect(contentIdOf(upper)).toBe(contentIdOf(mods));
  });

  it('меняется, когда меняется файл', () => {
    const changed = [file('mods/create.jar', 'c'), mods[1]];
    expect(contentIdOf(changed)).not.toBe(contentIdOf(mods));
  });

  it('меняется, когда файл переименован', () => {
    const renamed = [file('mods/create-6.0.11.jar', 'a'), mods[1]];
    expect(contentIdOf(renamed)).not.toBe(contentIdOf(mods));
  });

  it('меняется, когда файл добавили или убрали', () => {
    expect(contentIdOf([...mods, file('mods/jade.jar', 'd')])).not.toBe(contentIdOf(mods));
    expect(contentIdOf([mods[0]])).not.toBe(contentIdOf(mods));
  });

  it('это шестьдесят четыре шестнадцатеричных знака', () => {
    expect(contentIdOf(mods)).toMatch(/^[0-9a-f]{64}$/);
  });
});

describe('решение о перезаливке', () => {
  const dir = (name, id) => ({ dir: name, contentId: id });
  const was = (name, id, url = 'https://api.github.com/x/1') => ({
    dir: name,
    contentId: id,
    sha256: 'a'.repeat(64),
    size: 1,
    url,
  });

  it('прошлого манифеста нет — собираем всё', () => {
    const plan = planUpload([dir('mods', 'x'), dir('config', 'y')], null);

    expect(plan.reuse).toEqual([]);
    expect(plan.build).toHaveLength(2);
  });

  it('отпечаток совпал — переиспользуем запись целиком, вместе со ссылкой', () => {
    const before = was('mods', 'x');
    const plan = planUpload([dir('mods', 'x')], { archives: [before] });

    expect(plan.reuse).toEqual([before]);
    expect(plan.build).toEqual([]);
  });

  it('отпечаток разошёлся — собираем заново', () => {
    const plan = planUpload([dir('mods', 'новый')], { archives: [was('mods', 'старый')] });

    expect(plan.reuse).toEqual([]);
    expect(plan.build).toHaveLength(1);
  });

  // Манифест прошлой ревизии формата: отпечатка в нём нет, и молча
  // считать содержимое прежним нельзя.
  it('манифест без contentId не даёт переиспользовать', () => {
    const плоский = { dir: 'mods', sha256: 'a'.repeat(64), size: 1, url: 'https://x/1' };
    const plan = planUpload([dir('mods', 'x')], { archives: [плоский] });

    expect(plan.build).toHaveLength(1);
  });

  it('новая папка в паке собирается, старая просто исчезает из плана', () => {
    const plan = planUpload([dir('kubejs', 'z')], { archives: [was('mods', 'x')] });

    expect(plan.reuse).toEqual([]);
    expect(plan.build.map((d) => d.dir)).toEqual(['kubejs']);
  });
});

describe('сборка архива', () => {
  it('имя не из латиницы отвергается с перечислением файлов', async () => {
    await expect(
      zip({ sourceDir: '.', entries: ['mods/ok.jar', 'config/jei/world/local/Новый мир/x.json'], archive: 'x.zip' })
    ).rejects.toThrow(/Новый мир/);
  });
});

// Наши моды меняются по нескольку раз за вечер, чужие — раз в месяц.
// Пока они ехали одним архивом, правка на 3 МБ стоила 369 МБ заливки
// при канале 0.25 МБ/с. Заметка `2026-09-17-mods-dvumya-arhivami.md`.
describe('разделение модов на свои и чужие', () => {
  const f = (path) => ({ path, sha256: 'a'.repeat(64), size: 1 });

  it('наши джарники узнаются по имени', () => {
    expect(isOwnMod('mods/plaguecore-0.6.0.jar')).toBe(true);
    expect(isOwnMod('mods/lmpc_classes-0.20.0.jar')).toBe(true);
    expect(isOwnMod('mods/lmpc_gmtools-0.21.0.jar')).toBe(true);
    expect(isOwnMod('mods/create-1.21.1-6.0.10.jar')).toBe(false);
    expect(isOwnMod('mods/jei-1.21.1.jar')).toBe(false);
  });

  it('mods распадается на два архива, остальные папки не трогаются', () => {
    const файлы = [f('mods/create.jar'), f('mods/plaguecore-0.6.0.jar'), f('mods/jei.jar')];
    const части = splitArchives('mods', файлы, true);

    expect(части.map((ч) => ч.name).sort()).toEqual(['mods-core', 'mods-lmpc']);
    expect(части.every((ч) => ч.dir === 'mods')).toBe(true);

    const наши = части.find((ч) => ч.name === 'mods-lmpc');
    expect(наши.files.map((x) => x.path)).toEqual(['mods/plaguecore-0.6.0.jar']);
    expect(части.find((ч) => ч.name === 'mods-core').files).toHaveLength(2);
  });

  // Пока у игроков стоят лаунчеры до 0.2.4, два архива на папку `mods`
  // они читают как испорченный манифест, поэтому деление выключено.
  it('при выключенном делении mods едет одним архивом', () => {
    const части = splitArchives('mods', [f('mods/create.jar'), f('mods/plaguecore-0.6.0.jar')]);

    expect(части).toHaveLength(1);
    expect(части[0].name).toBe('mods');
    expect(части[0].dir).toBe('mods');
  });

  it('другая папка остаётся одним архивом со своим именем', () => {
    const части = splitArchives('config', [f('config/create.toml')]);

    expect(части).toHaveLength(1);
    expect(части[0].name).toBe('config');
    expect(части[0].dir).toBe('config');
  });

  // Пустой архив в релизе — мусор: лаунчер вычистит папку и положит
  // в неё ничего. Такой группы быть не должно вовсе.
  it('пустая половина не превращается в архив', () => {
    const части = splitArchives('mods', [f('mods/create.jar')], true);

    expect(части).toHaveLength(1);
    expect(части[0].name).toBe('mods-core');
  });
});

describe('перезаливка различает архивы одной папки', () => {
  const части = [
    { name: 'mods-core', dir: 'mods', contentId: 'core-1' },
    { name: 'mods-lmpc', dir: 'mods', contentId: 'lmpc-2' },
  ];
  const было = (name, id) => ({
    name, dir: 'mods', contentId: id, sha256: 'a'.repeat(64), size: 1, url: 'https://x/1',
  });

  it('изменился только наш архив — чужой не перезаливается', () => {
    const план = planUpload(части, { archives: [было('mods-core', 'core-1'), было('mods-lmpc', 'старый')] });

    expect(план.reuse.map((a) => a.name)).toEqual(['mods-core']);
    expect(план.build.map((a) => a.name)).toEqual(['mods-lmpc']);
  });

  it('манифест старого образца адресован папкой — имя берётся из неё', () => {
    const плоский = { dir: 'config', contentId: 'c-1', sha256: 'a'.repeat(64), size: 1, url: 'https://x/2' };
    const план = planUpload([{ name: 'config', dir: 'config', contentId: 'c-1' }], { archives: [плоский] });

    expect(план.reuse).toHaveLength(1);
  });
});
