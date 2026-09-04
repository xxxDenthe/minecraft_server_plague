// Окошко со скином игрока — справа от пульта, зеркально новостям слева.
// Скин приходит из основного процесса как base64 PNG (строгий CSP не
// пускает renderer в сеть), тут раскладка скина 64×64 режется на canvas
// в фас. Два слоя: база и «одежда» (шапка, куртка) поверх.
//
// Левую и правую конечность не различаем — для превью 200 px это лишнее:
// обе руки рисуются из правой, обе ноги из правой. Так один и тот же код
// работает и для нового формата 64×64, и для старого 64×32.
// ponytail: без left/right-частей; добавить, если кому-то важен
// асимметричный скин в превью.

import { el } from './ui.js';

const SCALE = 6; // один пиксель скина → 6×6 на экране; поле 16×32

// [sx, sy, w, h, dx, dy] — источник в пикселях скина, приёмник в «пикселях»
// поля 16×32 (умножаются на SCALE при отрисовке).
const BASE = [
  [8, 8, 8, 8, 4, 0],     // голова
  [20, 20, 8, 12, 4, 8],  // корпус
  [44, 20, 4, 12, 0, 8],  // рука зрителя слева
  [44, 20, 4, 12, 12, 8], // рука зрителя справа
  [4, 20, 4, 12, 4, 20],  // нога
  [4, 20, 4, 12, 8, 20],  // нога
];
const OVERLAY = [
  [40, 8, 8, 8, 4, 0],
  [20, 36, 8, 12, 4, 8],
  [44, 36, 4, 12, 0, 8],
  [44, 36, 4, 12, 12, 8],
  [4, 36, 4, 12, 4, 20],
  [4, 36, 4, 12, 8, 20],
];

const bytesOf = (b64) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));

export function createSkinBox(store) {
  const canvas = el('canvas', {
    class: 'skin-canvas', width: 16 * SCALE, height: 32 * SCALE,
  });
  const caption = el('p', { class: 'skin-caption' });
  const node = el('section', { class: 'skin-box', 'aria-label': 'Скин персонажа' },
    el('div', { class: 'panel-head' }, el('h2', { text: 'Скин' })),
    el('div', { class: 'skin-body' }, canvas, caption),
  );

  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;

  const clearCanvas = () => ctx.clearRect(0, 0, canvas.width, canvas.height);

  function drawLayers(img) {
    clearCanvas();
    for (const layer of [BASE, OVERLAY]) {
      for (const [sx, sy, w, h, dx, dy] of layer) {
        if (sy >= img.height) continue; // старый формат 64×32 — слоя одежды снизу нет
        ctx.drawImage(img, sx, sy, w, h, dx * SCALE, dy * SCALE, w * SCALE, h * SCALE);
      }
    }
  }

  function drawPlaceholder() {
    clearCanvas();
    ctx.fillStyle = '#3a424b'; // холодный камень, как остальной интерфейс
    for (const [, , w, h, dx, dy] of BASE) {
      ctx.fillRect(dx * SCALE, dy * SCALE, w * SCALE, h * SCALE);
    }
  }

  let token = 0;
  let shownNick = null;
  let timer = null;

  async function load(nick) {
    const mine = ++token;

    if (!nick) {
      shownNick = null;
      clearCanvas();
      caption.textContent = 'Введите никнейм';
      return;
    }

    caption.textContent = 'Загрузка…';
    const b64 = await window.launcher.fetchSkin(nick);
    if (mine !== token) return;

    if (!b64) {
      shownNick = nick;
      drawPlaceholder();
      caption.textContent = 'Скин по нику не найден';
      return;
    }

    let bitmap;
    try {
      bitmap = await createImageBitmap(new Blob([bytesOf(b64)], { type: 'image/png' }));
    } catch {
      if (mine === token) { drawPlaceholder(); caption.textContent = 'Скин не разобрать'; }
      return;
    }
    if (mine !== token) return;

    shownNick = nick;
    drawLayers(bitmap);
    caption.textContent = nick;
  }

  let scheduledFor = null;
  store.subscribe((s) => {
    const nick = (s.nickname || '').trim();
    if (nick === scheduledFor) return;
    scheduledFor = nick;
    if (nick === shownNick) return;

    clearTimeout(timer);
    // Пауза, чтобы не дёргать сеть на каждой букве ника.
    timer = setTimeout(() => load(nick), 350);
  });

  return node;
}
