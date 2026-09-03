// Новости сервера. Источника данных пока нет — компонент устроен так,
// чтобы позже подменить `loadNews` на fetch(JSON/API) и ничего больше
// не трогать. Пустой список показывает спокойную заглушку.

import { el, clear } from './ui.js';

/**
 * @returns {Promise<Array<{ date?: string, title: string, body: string }>>}
 */
export async function loadNews() {
  // ponytail: заменить на реальный источник, когда появится —
  //   return (await fetch(NEWS_URL).then(r => r.json())).items;
  return [];
}

export function createNews({ source = loadNews } = {}) {
  const list = el('div', { class: 'news-list' });
  const node = el('section', { class: 'news', 'aria-label': 'Новости' },
    el('div', { class: 'panel-head' }, el('h2', { text: 'Новости' })),
    list,
  );

  function renderEmpty() {
    clear(list);
    list.append(el('p', { class: 'news-empty', text: 'Здесь будут появляться новости и объявления сервера.' }));
  }

  function renderItems(items) {
    clear(list);
    for (const item of items.slice(0, 4)) {
      list.append(el('article', { class: 'news-item' },
        item.date ? el('time', { class: 'news-date', text: item.date }) : null,
        el('h3', { class: 'news-title', text: item.title }),
        el('p', { class: 'news-body', text: item.body }),
      ));
    }
  }

  Promise.resolve(source())
    .then((items) => (Array.isArray(items) && items.length ? renderItems(items) : renderEmpty()))
    .catch(renderEmpty);

  return node;
}
