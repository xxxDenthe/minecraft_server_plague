// Полоса состояния запуска. Одна на все стадии: IDLE / CHECKING /
// LOADING / UPDATING / LAUNCHING / RUNNING / ERROR. Тянет всё из
// хранилища и перерисовывает только свою часть.

import { el, icon, clear } from './ui.js';
import { State } from './state.js';

const LABEL = {
  [State.IDLE]: 'Готов к запуску',
  [State.CHECKING]: 'Проверка файлов',
  [State.LOADING]: 'Загрузка',
  [State.UPDATING]: 'Обновление модпака',
  [State.LAUNCHING]: 'Запуск Minecraft',
  [State.RUNNING]: 'Игра запущена',
  [State.ERROR]: 'Не удалось запустить',
};

export function createProgress(store, actions) {
  const statusText = el('span', { class: 'progress-label' });
  const countText = el('span', { class: 'progress-count' });
  const fill = el('div', { class: 'progress-fill' });
  const bar = el('div', { class: 'progress-bar' }, fill);

  const errorBox = el('div', { class: 'progress-error', hidden: true });

  const node = el('div', { class: 'progress' },
    el('div', { class: 'progress-head' }, statusText, countText),
    bar,
    errorBox,
  );

  function renderError(s) {
    clear(errorBox);
    const details = el('details', { class: 'error-details' },
      el('summary', { text: 'Подробности' }),
      el('pre', { class: 'error-pre', text: s.errorDetail || 'нет технических данных' }),
    );
    errorBox.append(
      el('div', { class: 'error-row' },
        icon('alert', 18),
        el('span', { class: 'error-msg', text: s.errorMsg || 'Неизвестная ошибка' }),
        el('button', { class: 'btn btn-quiet', type: 'button', text: 'Повторить', onclick: actions.retry }),
      ),
      s.errorDetail ? details : null,
    );
  }

  store.subscribe((s) => {
    const st = s.state;
    node.dataset.state = st;

    statusText.textContent = s.progressMsg || LABEL[st] || '';

    const active = st === State.CHECKING || st === State.LOADING
      || st === State.UPDATING || st === State.LAUNCHING;

    bar.hidden = st === State.IDLE || st === State.RUNNING || st === State.ERROR;
    countText.textContent = active ? (s.progressCount || '') : '';

    const determinate = typeof s.progressFrac === 'number' && s.progressFrac > 0;
    fill.classList.toggle('indeterminate', active && !determinate);
    fill.style.width = determinate ? `${Math.round(Math.min(1, s.progressFrac) * 100)}%` : '';

    errorBox.hidden = st !== State.ERROR;
    if (st === State.ERROR) renderError(s);
  });

  return node;
}
