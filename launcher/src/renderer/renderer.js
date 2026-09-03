// Окно ничего не считает и ничего не качает. Оно рисует то, что ему
// присылает основной процесс, и сообщает о нажатии кнопки.

const nickname = document.getElementById('nickname');
const play = document.getElementById('play');
const barFill = document.getElementById('bar-fill');
const stage = document.getElementById('stage');
const log = document.getElementById('log');
const notice = document.getElementById('notice');

const MAX_LINES = 500;

function addLine(text) {
  const lines = log.textContent.split('\n');
  if (lines.length > MAX_LINES) log.textContent = lines.slice(-MAX_LINES).join('\n');

  log.textContent += text + '\n';
  log.scrollTop = log.scrollHeight;
}

// Прогресс показывается штуками там, где они есть: «1240 из 4100»
// понятнее, чем застывшие 30%.
function setProgress({ current = 0, total = 0, bytesDone = 0, bytesTotal = 0, message = '' }) {
  let fraction = 0;
  let counter = '';

  if (total > 0) {
    fraction = current / total;
    counter = ` — ${current} из ${total}`;
  } else if (bytesTotal > 0) {
    fraction = bytesDone / bytesTotal;
    counter = ` — ${(bytesDone / 1048576).toFixed(0)} из ${(bytesTotal / 1048576).toFixed(0)} МБ`;
  }

  barFill.style.width = Math.round(Math.min(1, fraction) * 100) + '%';
  stage.textContent = (message || 'Готов к запуску') + counter;
}

function setBusy(busy) {
  play.disabled = busy;
  nickname.disabled = busy;
  play.textContent = busy ? 'Идёт установка…' : 'Играть';
}

window.launcher.onProgress(setProgress);
window.launcher.onLog(addLine);

window.launcher.onUpdateAvailable((version) => {
  notice.hidden = false;
  notice.textContent = `Есть обновление пака (версия ${version}). Перезайдите, когда будет удобно.`;
});

window.launcher.onGameClosed((code) => {
  setBusy(false);
  stage.textContent = code === 0 ? 'Игра закрыта' : `Игра закрылась с кодом ${code}`;
  addLine('— игра завершилась —');
});

window.launcher.readConfig().then((config) => {
  nickname.value = config.nickname ?? '';
  if (config.lastPlayed) addLine(`Последний запуск: ${new Date(config.lastPlayed).toLocaleString('ru')}`);
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

  const result = await window.launcher.play({ nickname: name });

  if (!result.started) {
    setBusy(false);
    stage.textContent = 'Запустить не удалось';
    addLine(result.reason ?? 'причина неизвестна');
  }
});

nickname.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') play.click();
});

addLine('Первый запуск ставит Java, клиент и ассеты — около гигабайта. Дальше быстрее.');
