// Окно ничего не считает и ничего не качает. Оно рисует то, что ему
// присылает основной процесс, и сообщает о нажатии кнопки.

const nickname = document.getElementById('nickname');
const play = document.getElementById('play');
const barFill = document.getElementById('bar-fill');
const stage = document.getElementById('stage');
const log = document.getElementById('log');

function addLine(text) {
  log.textContent += text + '\n';
  log.scrollTop = log.scrollHeight;
}

function setProgress({ current = 0, total = 0, message = '' }) {
  const percent = total > 0 ? Math.round((current / total) * 100) : 0;
  barFill.style.width = percent + '%';
  stage.textContent = message || 'Готов к запуску';
}

window.launcher?.onProgress?.(setProgress);

play.addEventListener('click', () => {
  const name = nickname.value.trim();
  if (!name) {
    addLine('Введите ник — сервер работает без авторизации, ник и есть личность.');
    nickname.focus();
    return;
  }
  addLine(`Запуск ещё не подключён. Ник принят: ${name}`);
});

addLine('Лаунчер собран. Установка и запуск появятся по мере выполнения плана.');
