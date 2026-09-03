// Образец — CorePurityTest в plaguecore: там запрещены импорты Minecraft
// в пакете core, здесь запрещён импорт electron во всём src/main/,
// кроме index.js.
//
// Смысл тот же: модули должны запускаться в обычном Node, иначе их
// не протестировать без окна, и отладка установки Minecraft превратится
// в клики по интерфейсу.

import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const mainDir = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..', 'src', 'main'
);

const ALLOWED = new Set(['index.js']);

function sourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full);
    return entry.name.endsWith('.js') ? [full] : [];
  });
}

describe('чистота основного процесса', () => {
  it('никто, кроме index.js, не импортирует electron', () => {
    const guilty = [];

    for (const file of sourceFiles(mainDir)) {
      if (ALLOWED.has(path.basename(file))) continue;

      const text = fs.readFileSync(file, 'utf8');
      if (/from\s+['"]electron['"]/.test(text) || /require\(\s*['"]electron['"]\s*\)/.test(text)) {
        guilty.push(path.relative(mainDir, file));
      }
    }

    expect(guilty).toEqual([]);
  });

  it('в src/main есть хотя бы один файл — тест не проходит вхолостую', () => {
    expect(sourceFiles(mainDir).length).toBeGreaterThan(0);
  });
});
