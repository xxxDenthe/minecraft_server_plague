// Новости пака. Данные приходят из основного процесса: он ходит
// в релиз с токеном и держит кэш, поэтому окно про сеть не знает.
// Пустой список показывает спокойную заглушку.

import { el, clear } from './ui.js';

export function createNews({ source = () => window.launcher.loadNews() } = {}) {
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
    for (const item of items) {
      // Длинная запись свёрнута до двух строк и раскрывается кликом:
      // так изредка можно написать подробнее, не ломая вёрстку.
      const article = el('article', {
        class: `news-item${item.pinned ? ' news-pinned' : ''}`,
        onclick: () => article.classList.toggle('open'),
      },
        el('div', { class: 'news-meta' },
          el('span', { class: `news-kind news-kind-${item.kind}`, text: item.label }),
          el('time', { class: 'news-date', text: item.date }),
        ),
        el('h3', { class: 'news-title', text: item.title }),
        el('p', { class: 'news-body', text: item.body }),
      );
      list.append(article);
    }
  }

  Promise.resolve(source())
    .then((items) => (Array.isArray(items) && items.length ? renderItems(items) : renderEmpty()))
    .catch(renderEmpty);

  return node;
}
