// Верхняя панель: логотип LMPC слева, вторичные кнопки справа.
// Логотип — готовый пиксельный ассет, его не перерисовываем.

import { el, icon } from './ui.js';

// Без сборщика: путь к ассету — обычная строка относительно index.html.
const LOGO_SRC = 'assets/lmpc-logo.png';

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

  const fsBtn = el('button', {
    class: 'hbtn hbtn-icon', type: 'button', title: 'Полноэкранный режим',
    'aria-label': 'Полноэкранный режим', onclick: actions.fullscreen,
  }, icon('expand', 18));

  const right = el('div', { class: 'header-actions' }, discordBtn, settingsBtn, backBtn, fsBtn);

  const node = el('header', { class: 'header' },
    el('img', { class: 'logo', src: LOGO_SRC, alt: 'LMPC', draggable: false }),
    right,
  );

  store.subscribe((s) => {
    const onSettings = s.screen === 'settings';
    settingsBtn.hidden = onSettings;
    backBtn.hidden = !onSettings;
  });

  return node;
}
