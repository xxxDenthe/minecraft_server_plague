// Экран настроек. Полноценная часть лаунчера, не модалка. Настройки
// сгруппированы по категориям. То, что ещё не подключено к запуску
// (Java, разрешение, полный экран в игре), показано выключенным —
// без ложной функциональности.

import { el, icon, logo } from './ui.js';

const GB = 1024;
const RAM_MIN = 1 * GB;
const RAM_MAX = 16 * GB;

const gb = (mb) => `${Math.round(mb / GB)} ГБ`;

function group(title, ...rows) {
  return el('section', { class: 'set-group' },
    el('h2', { class: 'set-group-title', text: title }),
    el('div', { class: 'set-rows' }, ...rows),
  );
}

function row(label, control, note) {
  return el('div', { class: 'set-row' },
    el('div', { class: 'set-row-text' },
      el('span', { class: 'set-label', text: label }),
      note ? el('span', { class: 'set-note', text: note }) : null,
    ),
    el('div', { class: 'set-control' }, control),
  );
}

function toggle({ checked, disabled, onChange }) {
  const input = el('input', {
    class: 'toggle-input', type: 'checkbox', checked: !!checked, disabled: !!disabled,
    onchange: (e) => onChange && onChange(e.target.checked),
  });
  return el('label', { class: 'toggle' }, input, el('span', { class: 'toggle-track' }));
}

export function createSettings(store, actions) {
  const cfg = store.get();

  // --- Память ---
  const minLabel = el('span', { class: 'ram-val', text: gb(cfg.minRamMb) });
  const maxLabel = el('span', { class: 'ram-val', text: gb(cfg.maxRamMb) });

  const minSlider = el('input', {
    class: 'slider', type: 'range', min: RAM_MIN, max: RAM_MAX, step: GB, value: cfg.minRamMb,
    oninput: () => {
      if (Number(minSlider.value) > Number(maxSlider.value)) maxSlider.value = minSlider.value;
      minLabel.textContent = gb(minSlider.value);
      maxLabel.textContent = gb(maxSlider.value);
    },
    onchange: saveRam,
  });
  const maxSlider = el('input', {
    class: 'slider', type: 'range', min: RAM_MIN, max: RAM_MAX, step: GB, value: cfg.maxRamMb,
    oninput: () => {
      if (Number(maxSlider.value) < Number(minSlider.value)) minSlider.value = maxSlider.value;
      minLabel.textContent = gb(minSlider.value);
      maxLabel.textContent = gb(maxSlider.value);
    },
    onchange: saveRam,
  });

  function saveRam() {
    actions.saveConfig({ minRamMb: Number(minSlider.value), maxRamMb: Number(maxSlider.value) });
  }

  // --- Дополнительно ---
  const jvmInput = el('input', {
    class: 'text-input', type: 'text', value: cfg.jvmArgs || '',
    placeholder: '-XX:+UseG1GC …',
    onchange: () => actions.saveConfig({ jvmArgs: jvmInput.value.trim() }),
  });

  const node = el('div', { class: 'settings' },
    logo(),
    el('div', { class: 'settings-scroll' },
      group('Игра',
        row('Разрешение игры',
          el('select', { class: 'select', disabled: true }, el('option', { text: 'По умолчанию' })),
          'Появится в следующей версии'),
        row('Полный экран в игре', toggle({ disabled: true }), 'Появится в следующей версии'),
      ),
      group('Память',
        row('Минимум', el('div', { class: 'ram-control' }, minSlider, minLabel)),
        row('Максимум', el('div', { class: 'ram-control' }, maxSlider, maxLabel),
          'Для пака из 99 модов хватает 6–8 ГБ'),
      ),
      group('Java',
        row('Используемая Java',
          el('span', { class: 'set-value', text: 'Встроенная, версия 21' }),
          'Ставится вместе с лаунчером в папку runtime'),
        row('Указать другую',
          el('button', { class: 'btn btn-quiet', type: 'button', disabled: true },
            el('span', { text: 'Выбрать…' })),
          'Появится в следующей версии'),
      ),
      group('Дополнительно',
        row('Аргументы JVM', jvmInput,
          'Через пробел. Добавляются к аргументам пака при запуске.'),
      ),
      group('Файлы',
        row('Папка игры',
          el('button', { class: 'btn btn-quiet', type: 'button', onclick: () => window.launcher.openFolder() },
            icon('folder', 16), el('span', { text: 'Открыть' }))),
        row('Папка логов',
          el('button', { class: 'btn btn-quiet', type: 'button', onclick: () => window.launcher.openLog() },
            icon('folder', 16), el('span', { text: 'Открыть' }))),
      ),
    ),
  );

  return node;
}
