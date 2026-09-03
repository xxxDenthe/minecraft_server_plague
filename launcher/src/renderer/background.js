// Атмосферный фон целиком в коде: слоёная SVG-сцена + туман + редкие
// частицы пепла. Внешних картинок нет — по брифу и чтобы сборка не
// таскала мегабайты. ponytail: если однажды появится настоящий арт,
// он заменит .bg-scene, остальные слои остаются.

import { el } from './ui.js';

const SCENE = `
<svg class="bg-scene" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
  <defs>
    <linearGradient id="bg-sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#0e1116"/>
      <stop offset="0.38" stop-color="#141922"/>
      <stop offset="0.64" stop-color="#1e2a34"/>
      <stop offset="0.80" stop-color="#33454f"/>
      <stop offset="0.90" stop-color="#26343d"/>
      <stop offset="1" stop-color="#131a20"/>
    </linearGradient>
    <radialGradient id="bg-glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#5a6f7d" stop-opacity="0.75"/>
      <stop offset="0.4" stop-color="#4a5d69" stop-opacity="0.3"/>
      <stop offset="1" stop-color="#455663" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="bg-rim" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#5f7480" stop-opacity="0.55"/>
      <stop offset="1" stop-color="#5f7480" stop-opacity="0"/>
    </linearGradient>
  </defs>

  <rect width="1600" height="900" fill="url(#bg-sky)"/>
  <ellipse class="bg-halo" cx="830" cy="820" rx="960" ry="300" fill="url(#bg-glow)"/>

  <!-- дальние холмы: светлее и в дымке -->
  <path fill="#2b333d" opacity="0.6" d="M0 640 C 240 596 380 612 600 606 C 880 598 1060 632 1280 610 C 1430 596 1530 610 1600 604 L1600 900 L0 900 Z"/>
  <path fill="#1e2831" d="M0 706 C 280 664 460 688 720 680 C 980 672 1160 704 1400 686 C 1490 680 1550 690 1600 686 L1600 900 L0 900 Z"/>

  <!-- средневековые руины низкой полосой на фоне холодного света -->
  <g>
    <!-- разрушенная куртинная стена с зубцами -->
    <path fill="#090c10" d="M0 900 V782
      h22 v-20 h22 v20 h30 v-20 h22 v20 h34 v-46 h18 v-14 h20 v14 h18 v46
      h50 v-20 h22 v20 h26 v-20 h22 v20 h40 V900 Z"/>
    <!-- надвратная башня с остроконечной аркой -->
    <path fill="#070a0d" d="M420 900 V736
      h16 v-18 h18 v18 h16 v-18 h18 v18 h16 v-56 h60 v56
      h16 v-18 h18 v18 h16 v-18 h18 v18 h16 V900
      h-92 V812 q0 -44 -46 -44 q-46 0 -46 44 V900 Z"/>
    <!-- главная башня, верх обвалился -->
    <path fill="#05080b" d="M690 900 V624
      l6 -26 l14 18 l10 -34 l16 30 l12 -20 l14 26 l16 -14 l10 22 l18 -8 l8 18 h4 V900 Z"/>
    <!-- накренившаяся башня -->
    <path fill="#080b0f" d="M900 900 V704 l4 -30 h40 l4 30
      h10 v-14 h14 v14 h10 v-22 h12 v22 h8 V900 Z" transform="rotate(3 940 900)"/>
    <!-- обломок арки и груда камня -->
    <path fill="#06090c" d="M1120 900 V800 q40 -70 92 0 V900 h-30 V824 q-16 -34 -32 0 V900 Z"/>
    <path fill="#070a0e" d="M1280 900 C 1300 840 1360 828 1420 848 C 1470 864 1520 852 1560 872 V900 Z"/>
    <!-- холодный контровой свет по верхним кромкам -->
    <path fill="url(#bg-rim)" opacity="0.85" d="M420 682 h60 v56 h16 v-18 h18 v18 h16 v-18 h18 v18 h16 v10 h-186 Z
      M690 624 l6 -26 l14 18 l10 -34 l16 30 l12 -20 l14 26 l16 -14 l10 22 l18 -8 l8 18 h4 v14 h-184 Z"/>
  </g>

  <!-- мёртвый лес: почти чёрные силуэты по краям -->
  <g stroke="#05070a" fill="none" stroke-linecap="round">
    <path stroke-width="8" d="M56 900 V612 M56 674 l-34 -44 M56 706 l44 -38 M56 642 l26 -24"/>
    <path stroke-width="6" d="M150 900 V672 M150 720 l-30 -34 M150 704 l34 -30"/>
    <path stroke-width="7" d="M1540 900 V628 M1540 690 l-34 -40 M1540 720 l42 -34 M1540 660 l24 -22"/>
    <path stroke-width="5" d="M1440 900 V704 M1440 748 l-28 -32 M1440 732 l32 -26"/>
    <path stroke-width="4" d="M1360 900 V750 M1360 782 l-22 -24 M1360 768 l24 -20"/>
  </g>
</svg>`;

const GRAIN = `
<svg class="bg-grain" preserveAspectRatio="none" aria-hidden="true">
  <filter id="bg-grain-f">
    <feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="2" seed="11" stitchTiles="stitch"/>
    <feColorMatrix type="saturate" values="0"/>
  </filter>
  <rect width="100%" height="100%" filter="url(#bg-grain-f)"/>
</svg>`;

function startParticles(canvas) {
  const ctx = canvas.getContext('2d');
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let raf = 0;
  let motes = [];

  const seed = () => {
    const { width: w, height: h } = canvas;
    motes = Array.from({ length: 46 }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      r: Math.random() * 1.3 + 0.3,
      // очень медленно: вверх-вбок, как пыль в холодном воздухе
      vx: (Math.random() - 0.35) * 0.14,
      vy: -(Math.random() * 0.12 + 0.03),
      a: Math.random() * 0.35 + 0.05,
    }));
  };

  const resize = () => {
    canvas.width = canvas.offsetWidth;
    canvas.height = canvas.offsetHeight;
    seed();
  };

  const frame = () => {
    const { width: w, height: h } = canvas;
    ctx.clearRect(0, 0, w, h);
    for (const m of motes) {
      m.x += m.vx;
      m.y += m.vy;
      if (m.y < -4) { m.y = h + 4; m.x = Math.random() * w; }
      if (m.x < -4) m.x = w + 4;
      if (m.x > w + 4) m.x = -4;
      ctx.fillStyle = `rgba(180,190,200,${m.a})`;
      ctx.beginPath();
      ctx.arc(m.x, m.y, m.r, 0, Math.PI * 2);
      ctx.fill();
    }
    raf = requestAnimationFrame(frame);
  };

  resize();
  window.addEventListener('resize', resize);
  if (reduced) frame(); // один кадр — пылинки на месте
  else raf = requestAnimationFrame(frame);

  return () => { cancelAnimationFrame(raf); window.removeEventListener('resize', resize); };
}

export function createBackground() {
  const node = el('div', { class: 'bg', html:
    SCENE +
    '<div class="bg-fog bg-fog-1"></div>' +
    '<div class="bg-fog bg-fog-2"></div>' +
    '<div class="bg-fog bg-fog-3"></div>' +
    '<canvas class="bg-particles" aria-hidden="true"></canvas>' +
    '<div class="bg-vignette"></div>' +
    GRAIN,
  });

  // Канвас частиц запускается, когда узел уже в документе.
  queueMicrotask(() => {
    const canvas = node.querySelector('.bg-particles');
    if (canvas) startParticles(canvas);
  });

  return node;
}
