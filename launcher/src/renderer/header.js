// Верхняя панель: вторичные кнопки в правом верхнем углу. Логотип LMPC
// живёт на самом экране, по центру над никнеймом, а не здесь.

import { el, icon } from './ui.js';

export function createHeader(store, actions) {
  const backBtn = el('button', {
    class: 'hbtn hbtn-back', type: 'button', title: 'Назад', onclick: actions.back,
  }, icon('chevronLeft', 18), el('span', { text: 'Назад' }));

  const settingsBtn = el('button', {
    class: 'hbtn', type: 'button', title: 'Настройки', onclick: actions.settings,
  }, icon('gear', 18), el('span', { text: 'Настройки' }));

  const discordBtn = el('button', {
    class: 'hbtn', type: 'button', title: 'Discord-сервер', onclick: actions.discord,
  }, icon('discord', 18), el('span', { text: 'Discord' }));

  // Приманка: выглядит как «во весь экран», окно не разворачивает.
  const fsBtn = el('button', {
    class: 'hbtn hbtn-icon', type: 'button', title: 'Полноэкранный режим',
    'aria-label': 'Полноэкранный режим', onclick: actions.easterEgg,
  }, icon('expand', 18));

  const node = el('header', { class: 'header' },
    el('div', { class: 'header-actions' }, discordBtn, settingsBtn, backBtn, fsBtn),
  );

  store.subscribe((s) => {
    const onSettings = s.screen === 'settings';
    settingsBtn.hidden = onSettings;
    backBtn.hidden = !onSettings;
  });

  return node;
}
