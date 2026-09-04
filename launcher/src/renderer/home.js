// Главный экран. Композиция по брифу: слева — узнаваемый пульт
// (никнейм → PLAY → состояние), справа — новости и модпак, снизу —
// техническая информация о версиях. Фокус: LMPC → никнейм → PLAY.

import { el, icon, logo } from './ui.js';
import { State } from './state.js';
import { createProgress } from './progress.js';
import { createNews } from './news.js';
import { createSkinBox } from './skin.js';

const MC_VERSION = '1.21.1'; // целевая версия проекта, фиксированная

// Ссылки рядом со скином. Видеогайд владелец добавит позже — пока его
// нет, кнопка выключена (без ложной активности, как остальные заготовки).
const ELY_REGISTER_URL = 'https://account.ely.by/register';
const SKIN_GUIDE_URL = 'https://youtu.be/LfZQScLoYj8';

export function createHome(store, actions) {
  const nickInput = el('input', {
    class: 'nick-input', id: 'nick', type: 'text', maxLength: 16,
    autocomplete: 'off', spellcheck: false, placeholder: 'Введите ваш никнейм',
    value: store.get().nickname || '',
    oninput: () => {
      playBtn.disabled = !canPlay();
      // В хранилище — чтобы окошко скина обновилось за никнеймом.
      // В конфиг ник по-прежнему пишется только при запуске.
      store.set({ nickname: nickInput.value });
    },
    onkeydown: (e) => { if (e.key === 'Enter' && canPlay()) startPlay(); },
  });

  const playBtn = el('button', {
    class: 'play', type: 'button', onclick: startPlay,
  }, icon('play', 15), el('span', { class: 'play-label', text: 'ИГРАТЬ' }));

  const progress = createProgress(store, { retry: startPlay });

  const versionInfo = el('div', { class: 'versions' },
    el('div', { class: 'v-item' }, el('span', { class: 'v-key', text: 'Minecraft' }), el('span', { class: 'v-val', text: MC_VERSION })),
    el('div', { class: 'v-item' }, el('span', { class: 'v-key', text: 'Модпак' }), el('span', { class: 'v-val v-pack' })),
  );

  const updateBtn = el('button', {
    class: 'btn btn-quiet', type: 'button', disabled: true,
  }, icon('refresh', 16), el('span', { text: 'Проверить обновления' }));

  const modpackPanel = el('section', { class: 'modpack', 'aria-label': 'Модпак' },
    el('div', { class: 'panel-head' }, el('h2', { text: 'Модпак' })),
    el('div', { class: 'modpack-body' },
      updateBtn,
      el('p', { class: 'hint', text: 'Пак сверяется и дособерётся сам при следующем запуске.' }),
    ),
  );

  const node = el('div', { class: 'home' },
    el('div', { class: 'home-main' },
      el('aside', { class: 'home-side' },
        createNews(),
        modpackPanel,
      ),
      el('div', { class: 'console' },
        logo(),
        el('label', { class: 'field-label', for: 'nick', text: 'Никнейм' }),
        nickInput,
        playBtn,
        progress,
      ),
      el('div', { class: 'home-skin' },
        createSkinBox(store),
        el('div', { class: 'skin-links' },
          // Порядок сверху вниз: видеогайд, затем регистрация ely.by
          // (её владелец хочет самой нижней).
          el('button', {
            class: 'skin-link', type: 'button',
            disabled: !SKIN_GUIDE_URL,
            title: SKIN_GUIDE_URL ? 'Видео: как поставить свой скин' : 'Видеогайд появится позже',
            'aria-label': 'Видеогайд по установке скина',
            onclick: () => SKIN_GUIDE_URL && window.launcher.openLink(SKIN_GUIDE_URL),
          }, icon('video', 17)),
          el('button', {
            class: 'skin-link', type: 'button',
            title: 'Регистрация на ely.by — чтобы поставить свой скин',
            'aria-label': 'Регистрация на ely.by',
            onclick: () => window.launcher.openLink(ELY_REGISTER_URL),
          }, icon('userPlus', 17)),
        ),
      ),
    ),
    el('footer', { class: 'home-foot' }, versionInfo),
  );

  function canPlay() {
    const s = store.get();
    const busy = s.state !== State.IDLE && s.state !== State.ERROR;
    return nickInput.value.trim().length > 0 && !busy;
  }

  function startPlay() {
    if (!canPlay()) return;
    actions.play(nickInput.value.trim());
  }

  store.subscribe((s) => {
    if (document.activeElement !== nickInput && (s.nickname || '') !== nickInput.value) {
      nickInput.value = s.nickname || '';
    }
    playBtn.disabled = !canPlay();
    const busy = s.state !== State.IDLE && s.state !== State.ERROR;
    nickInput.disabled = busy;

    const pack = node.querySelector('.v-pack');
    pack.textContent = s.packVersion > 0 ? 'основной' : 'не установлен';
  });

  return node;
}
