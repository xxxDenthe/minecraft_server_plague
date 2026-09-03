// Окно ничего не считает и ничего не качает. Оно рисует то, что ему
// присылает основной процесс, сохраняет выбор игрока в конфиг и
// сообщает о нажатии кнопки.

const el = (id) => document.getElementById(id);

const viewMain = el('view-main');
const viewSettings = el('view-settings');
const nickname = el('nickname');
const play = el('play');
const barFill = el('bar-fill');
const stage = el('stage');
const log = el('log');
const notice = el('notice');
const details = el('details');
const ram = el('ram');
const ramValue = el('ram-value');
const version = el('version');

const MAX_LINES = 500;

// --- переключение экранов ---
function show(view) {
  viewMain.hidden = view !== 'main';
  viewSettings.hidden = view !== 'settings';
}

el('to-settings').addEventListener('click', () => show('settings'));
el('to-main').addEventListener('click', () => show('main'));

// --- лог ---
function addLine(text) {
  const lines = log.textContent.split('\n');
  if (lines.length > MAX_LINES) log.textContent = lines.slice(-MAX_LINES).join('\n');

  log.textContent += text + '\n';
  log.scrollTop = log.scrollHeight;
}

// --- прогресс ---
// Штуки понятнее процентов: «1240 из 4100» лучше застывших 30%. Когда
// счётчика нет (распаковка, установщик NeoForge) — полоса «дышит», чтобы
// окно не выглядело зависшим.
function setProgress({ current = 0, total = 0, bytesDone = 0, bytesTotal = 0, message = '' }) {
  let fraction = 0;
  let counter = '';
  let determinate = false;

  if (total > 0) {
    fraction = current / total;
    counter = ` — ${current} из ${total}`;
    determinate = true;
  } else if (bytesTotal > 0) {
    fraction = bytesDone / bytesTotal;
    counter = ` — ${(bytesDone / 1048576).toFixed(0)} из ${(bytesTotal / 1048576).toFixed(0)} МБ`;
    determinate = true;
  }

  barFill.classList.toggle('pulse', !determinate);
  barFill.style.width = determinate ? Math.round(Math.min(1, fraction) * 100) + '%' : '100%';
  stage.textContent = (message || 'Готов к запуску') + counter;
}

function setBusy(busy) {
  play.disabled = busy;
  nickname.disabled = busy;
  play.textContent = busy ? 'Идёт установка' : 'Играть';
  stage.classList.toggle('working', busy);
  if (busy) {
    details.open = true;
  } else {
    barFill.classList.remove('pulse');
  }
}

// --- настройки ---
function ramGb(mb) {
  return `${Math.round(mb / 1024)} ГБ`;
}

ram.addEventListener('input', () => {
  ramValue.textContent = ramGb(Number(ram.value));
});

// Пишем в конфиг по отпусканию ползунка, а не на каждый пиксель.
ram.addEventListener('change', () => {
  window.launcher.writeConfig({ maxRamMb: Number(ram.value) });
});

el('open-folder').addEventListener('click', () => window.launcher.openFolder());
el('open-log').addEventListener('click', () => window.launcher.openLog());

// --- события от основного процесса ---
window.launcher.onProgress(setProgress);
window.launcher.onLog(addLine);

window.launcher.onUpdateAvailable((v) => {
  notice.hidden = false;
  notice.textContent = `Есть обновление пака (версия ${v}). Перезайдите, когда будет удобно.`;
});

window.launcher.onGameClosed((code) => {
  setBusy(false);
  stage.textContent = code === 0 ? 'Игра закрыта' : `Игра закрылась с кодом ${code}`;
  addLine('— игра завершилась —');
});

// --- старт: подтянуть конфиг ---
window.launcher.readConfig().then((config) => {
  nickname.value = config.nickname ?? '';

  const mb = Number.isInteger(config.maxRamMb) && config.maxRamMb > 0 ? config.maxRamMb : 6144;
  ram.value = String(Math.min(12288, Math.max(2048, mb)));
  ramValue.textContent = ramGb(Number(ram.value));

  version.textContent = config.packVersion > 0
    ? `пак версии ${config.packVersion}`
    : 'пак не установлен';

  if (config.lastPlayed) {
    addLine(`Последний запуск: ${new Date(config.lastPlayed).toLocaleString('ru')}`);
  }
});

play.addEventListener('click', async () => {
  const name = nickname.value.trim();

  if (!name) {
    addLine('Введите ник — сервер работает без авторизации, ник и есть личность.');
    nickname.focus();
    return;
  }

  setBusy(true);
  addLine(`Готовлю запуск для ника ${name}.`);

  const maxRamMb = Number(ram.value);
  const result = await window.launcher.play({ nickname: name, maxRamMb });

  if (result.started) {
    window.launcher.writeConfig({ nickname: name, maxRamMb, lastPlayed: new Date().toISOString() });
  } else {
    setBusy(false);
    stage.textContent = 'Запустить не удалось';
    addLine(result.reason ?? 'причина неизвестна');
  }
});

nickname.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') play.click();
});

addLine('Первый запуск ставит Java, клиент и ассеты — около гигабайта. Дальше быстрее.');
