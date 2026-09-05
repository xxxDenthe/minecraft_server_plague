// Отпечаток содержимого решает, поедут ли по сети 262 МБ. Ошибка в нём
// молчалива в обе стороны: либо лишняя перезаливка каждый раз, либо
// игроки со старыми модами и правильным номером версии.

import { describe, it, expect } from 'vitest';

import { contentIdOf, planUpload } from '../tools/pack.js';

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
