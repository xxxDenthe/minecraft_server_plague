// Крошечные помощники для сборки DOM без innerHTML и без строковых
// шаблонов: узлы строятся деревом, текст всегда через textContent —
// чужие строки (ник, лог игры) не могут стать разметкой.

export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);

  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key === 'html') node.innerHTML = value; // только для доверенных SVG-строк
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key in node && key !== 'list') {
      node[key] = value;
    } else {
      node.setAttribute(key, value);
    }
  }

  for (const child of children.flat()) {
    if (child == null || child === false) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }

  return node;
}

// SVG-иконки: один набор, тонкая линия, наследуют currentColor.
export function icon(name, size = 20) {
  const paths = ICONS[name];
  if (!paths) throw new Error(`нет иконки ${name}`);
  const svg = `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none"
    stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
    stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
  const wrap = document.createElement('span');
  wrap.className = 'icon';
  wrap.innerHTML = svg;
  return wrap.firstElementChild;
}

const ICONS = {
  gear:
    '<circle cx="12" cy="12" r="3.2"/><path d="M12 2.6v2.6M12 18.8v2.6M4.2 7.5l2.2 1.3M17.6 15.2l2.2 1.3M4.2 16.5l2.2-1.3M17.6 8.8l2.2-1.3"/>',
  chevronLeft: '<path d="M15 5l-7 7 7 7"/>',
  expand: '<path d="M4 9V4h5M20 15v5h-5M4 15v5h5M20 9V4h-5"/>',
  folder: '<path d="M3 7.5A1.5 1.5 0 0 1 4.5 6h4l2 2.2h7A1.5 1.5 0 0 1 21 9.7v8.8A1.5 1.5 0 0 1 19.5 20h-15A1.5 1.5 0 0 1 3 18.5z"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-.9 4.6M20 5v6h-6"/>',
  discord:
    '<path d="M8.5 8.2A11 11 0 0 1 12 7.7a11 11 0 0 1 3.5.5M7 17c-1.2-.4-2.2-1-3-1.8 .4-4.6 2-8 4.2-9.4l.9 1.6M17 17c1.2-.4 2.2-1 3-1.8-.4-4.6-2-8-4.2-9.4l-.9 1.6M8.7 17.2 8 19s2 1 4 1 4-1 4-1l-.7-1.8"/><path d="M9.6 13.2c0 .8-.5 1.4-1.1 1.4S7.4 14 7.4 13.2s.5-1.4 1.1-1.4 1.1.6 1.1 1.4zM16.6 13.2c0 .8-.5 1.4-1.1 1.4s-1.1-.6-1.1-1.4.5-1.4 1.1-1.4 1.1.6 1.1 1.4z" fill="currentColor" stroke="none"/>',
  play: '<path d="M8 5.5v13l11-6.5z" fill="currentColor" stroke="none"/>',
  alert: '<path d="M12 8.5v5M12 16.5v.01"/><path d="M11 4.3 3.5 18.2a1 1 0 0 0 .9 1.5h15.2a1 1 0 0 0 .9-1.5L13 4.3a1.1 1.1 0 0 0-2 0z"/>',
};

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}
