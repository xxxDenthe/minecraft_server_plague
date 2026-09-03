#!/usr/bin/env node
// Кладёт в сборку адрес раздачи и токен на чтение.
//
//   npm run configure -- --repo xxxDenthe/minecraft_server_plague \
//                        --tag pack --token github_pat_...
//
// Запускается один раз перед `npm run dist`. Пишет
// src/main/distribution.json — файл не версионируется, в нём токен.
//
// Отдельная команда, а не переменные окружения при сборке: переменные
// читаются на машине игрока, где их никто не задавал, и установщик
// молча выходил бы без пака.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const target = path.join(here, '..', 'src', 'main', 'distribution.json');

function parseArgs(argv) {
  const args = { tag: 'pack' };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) throw new Error(`не понимаю аргумент «${token}»`);

    const key = token.slice(2);
    if (key === 'clear') {
      args.clear = true;
      continue;
    }

    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`у ключа --${key} нет значения`);

    args[key] = value;
    i += 1;
  }

  return args;
}

const args = parseArgs(process.argv.slice(2));

if (args.clear) {
  fs.rmSync(target, { force: true });
  console.log('раздача убрана: лаунчер соберётся без пака, с чистым NeoForge');
  process.exit(0);
}

if (!args.repo?.includes('/') && !args['manifest-url']) {
  console.error('нужен --repo вида владелец/репозиторий (или --manifest-url для своего сервера)');
  console.error('чтобы убрать раздачу из сборки: npm run configure -- --clear');
  process.exit(1);
}

if (!args.token) {
  console.error('нужен --token: без него приватный релиз не отдаст ни файла');
  process.exit(1);
}

// Право на запись в сборке не нужно и опасно: этим токеном можно
// стереть релиз, а достать его из установщика может любой.
if (args.token.length < 20) {
  console.error('токен подозрительно короткий — проверьте, что скопировали целиком');
  process.exit(1);
}

const distribution = {
  repo: args.repo ?? '',
  tag: args.tag,
  token: args.token,
  manifestUrl: args['manifest-url'] ?? '',
};

fs.writeFileSync(target, `${JSON.stringify(distribution, null, 2)}\n`, 'utf8');

console.log(`раздача записана в ${path.relative(process.cwd(), target)}`);
console.log(`  репозиторий: ${distribution.repo || '—'}`);
console.log(`  тег релиза:  ${distribution.tag}`);
console.log(`  токен:       ${args.token.slice(0, 8)}… (${args.token.length} символов)`);
console.log('\nфайл не версионируется — в нём токен. Дальше: npm run dist');
