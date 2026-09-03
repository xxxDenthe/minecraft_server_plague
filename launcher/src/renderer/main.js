// Оболочка приложения: хранилище, фон, шапка, переключение экранов
// и вся связь с основным процессом. Компоненты общаются только через
// хранилище и через переданные им действия.

import { el, clear } from './ui.js';
import { State, createStore, stateForStage } from './state.js';
import { createBackground } from './background.js';
import { createHeader } from './header.js';
import { createHome } from './home.js';
import { createSettings } from './settings.js';

const store = createStore({
  screen: 'home',
  state: State.IDLE,
  nickname: '',
  minRamMb: 1024,
  maxRamMb: 6144,
  jvmArgs: '',
  packVersion: 0,
  progressMsg: '',
  progressFrac: null,
  progressCount: '',
  errorMsg: '',
  errorDetail: '',
});

let logTail = '';

const actions = {
  play: runPlay,
  saveConfig(patch) {
    window.launcher.writeConfig(patch);
    store.set(patch);
  },
  discord: () => window.launcher.openDiscord(),
  settings: () => store.set({ screen: 'settings' }),
  back: () => store.set({ screen: 'home' }),
  easterEgg: showEgg,
};

async function runPlay(nickname) {
  logTail = '';
  const s = store.get();
  store.set({
    state: State.CHECKING, nickname,
    progressMsg: 'Проверка файлов', progressFrac: null, progressCount: '',
    errorMsg: '', errorDetail: '',
  });
  window.launcher.writeConfig({ nickname });

  const result = await window.launcher.play({
    nickname, maxRamMb: s.maxRamMb, minRamMb: s.minRamMb,
  });

  if (result.started) {
    store.set({ state: State.RUNNING, progressMsg: 'Игра запущена' });
  } else {
    store.set({
      state: State.ERROR,
      errorMsg: humanError(result.reason),
      errorDetail: logTail.slice(-4000),
    });
  }
}

function humanError(reason) {
  if (!reason) return 'Не удалось запустить Minecraft';
  if (/ EPERM|EACCES|denied/i.test(reason)) return 'Нет доступа к файлам игры';
  if (/ENOTFOUND|ETIMEDOUT|network|fetch failed/i.test(reason)) return 'Не удалось связаться с сервером обновлений';
  return `Не удалось запустить: ${reason}`;
}

// --- события основного процесса ---
window.launcher.onProgress((e) => {
  let frac = null;
  let count = '';
  if (e.total > 0) { frac = e.current / e.total; count = `${e.current} / ${e.total}`; }
  else if (e.bytesTotal > 0) {
    frac = e.bytesDone / e.bytesTotal;
    count = `${(e.bytesDone / 1048576).toFixed(0)} / ${(e.bytesTotal / 1048576).toFixed(0)} МБ`;
  }
  store.set({
    state: stateForStage(e.stage),
    progressMsg: e.message || store.get().progressMsg,
    progressFrac: frac,
    progressCount: count,
  });
});

window.launcher.onLog((line) => { logTail += line + '\n'; });

window.launcher.onGameClosed((code) => {
  store.set({
    state: code === 0 || code == null ? State.IDLE : State.ERROR,
    progressMsg: code === 0 || code == null ? 'Готов к запуску' : `Игра закрылась с кодом ${code}`,
    errorMsg: code === 0 || code == null ? '' : `Игра закрылась с кодом ${code}`,
    errorDetail: code === 0 || code == null ? '' : logTail.slice(-4000),
    progressFrac: null, progressCount: '',
  });
});

window.launcher.onUpdateAvailable((v) => store.set({ packVersion: Math.max(store.get().packVersion, v) }));

// --- пасхалка ---
// Кнопка «во весь экран» окно не разворачивает (его и нельзя). Вместо
// этого — крупный текст на всё окно. Гасится кликом или Esc.
let egg = null;
function showEgg() {
  if (egg) return;
  egg = el('div', { class: 'egg', onclick: hideEgg },
    el('span', { class: 'egg-text', text: 'ТЫ ПИДОРАС' }));
  document.getElementById('app').append(egg);
  document.addEventListener('keydown', onEggKey);
}
function hideEgg() {
  if (!egg) return;
  egg.remove();
  egg = null;
  document.removeEventListener('keydown', onEggKey);
}
function onEggKey(e) { if (e.key === 'Escape') hideEgg(); }

// --- сборка ---
const screenHost = el('div', { class: 'screen' });
const shell = el('div', { class: 'shell' }, createHeader(store, actions), screenHost);

let current = null;
store.subscribe((s) => {
  if (current === s.screen) return;
  current = s.screen;
  clear(screenHost);
  screenHost.append(s.screen === 'settings' ? createSettings(store, actions) : createHome(store, actions));
  screenHost.dataset.screen = s.screen;
});

async function boot() {
  const app = document.getElementById('app');
  app.append(createBackground(), shell);

  const cfg = await window.launcher.readConfig();
  store.set({
    nickname: cfg.nickname || '',
    minRamMb: cfg.minRamMb,
    maxRamMb: cfg.maxRamMb,
    jvmArgs: cfg.jvmArgs || '',
    packVersion: cfg.packVersion || 0,
  });

  requestAnimationFrame(() => document.body.classList.add('ready'));
}

boot();
