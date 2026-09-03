// Откуда лаунчер берёт пак. Значения подставляются при сборке
// установщика и уезжают внутри него к игроку.
//
// Почему файл, а не переменные окружения: переменные читаются на машине
// игрока, где их никто не задавал. Переменные оставлены как запасной
// путь — они удобны при отладке из терминала, но в собранном
// установщике работает только distribution.json.
//
// Файл создаётся командой `npm run configure` и НЕ версионируется:
// в нём лежит токен доступа к приватному релизу.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export const CONFIG_FILE = path.join(here, 'distribution.json');

function fromFile() {
  try {
    const raw = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
    return raw && typeof raw === 'object' ? raw : {};
  } catch {
    // Файла нет — лаунчер собран без раздачи. Это рабочее состояние:
    // он поставит чистый клиент с NeoForge и скажет об этом в логе.
    return {};
  }
}

export function distribution() {
  const file = fromFile();

  return {
    repo: file.repo ?? process.env.PLAGUE_REPO ?? '',
    tag: file.tag ?? process.env.PLAGUE_RELEASE_TAG ?? 'pack',
    token: file.token ?? process.env.PLAGUE_MANIFEST_TOKEN ?? '',
    manifestUrl: file.manifestUrl ?? process.env.PLAGUE_MANIFEST_URL ?? '',
  };
}
