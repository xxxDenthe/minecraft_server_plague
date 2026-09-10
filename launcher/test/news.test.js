// Новости пака. Проверяется то, что ломает панель молча: длинный
// заголовок, две закреплённые записи, черновик, уехавший в релиз.

import { describe, it, expect } from 'vitest';

import { parseNews, LIMITS } from '../src/main/news.js';

const item = {
  id: '2026-09-09-purifier',
  date: '2026-09-09',
  kind: 'add',
  title: 'Очиститель воздуха',
  body: 'Новый блок Кузнеца: снимает заражение вокруг себя.',
};
const news = (items) => JSON.stringify({ items });

describe('разбор новостей', () => {
  it('читает нормальную ленту', () => {
    expect(parseNews(news([item]))[0].title).toBe('Очиститель воздуха');
  });

  it('вид записи превращается в человеческую метку', () => {
    expect(parseNews(news([item]))[0].label).toBe('Добавлено');
  });

  it('закреплённая запись идёт первой', () => {
    const items = parseNews(news([item, { ...item, id: 'x', title: 'Сервер переедет', pinned: true }]));
    expect(items[0].title).toBe('Сервер переедет');
  });

  it('двух закреплённых не бывает', () => {
    expect(() => parseNews(news([
      { ...item, pinned: true },
      { ...item, id: 'y', pinned: true },
    ]))).toThrow(/закреплённ/);
  });

  it('слишком длинный заголовок — ошибка сборки, а не кривая вёрстка', () => {
    expect(() => parseNews(news([{ ...item, title: 'я'.repeat(LIMITS.title + 1) }]))).toThrow(/title/);
  });

  it('слишком длинный текст тоже не пропускается', () => {
    expect(() => parseNews(news([{ ...item, body: 'я'.repeat(LIMITS.body + 1) }]))).toThrow(/body/);
  });

  it('неизвестный вид записи не пропускается', () => {
    expect(() => parseNews(news([{ ...item, kind: 'секрет' }]))).toThrow(/kind/);
  });

  it('дата не вида 2026-09-09 — ошибка', () => {
    expect(() => parseNews(news([{ ...item, date: '9 сентября' }]))).toThrow(/date/);
  });

  it('черновик игрокам не показывается', () => {
    expect(parseNews(news([{ ...item, draft: true }]))).toHaveLength(0);
  });

  it('ответ не JSON — понятная ошибка, а не падение разбора', () => {
    expect(() => parseNews('<html>404</html>')).toThrow(/не JSON/);
  });
});
