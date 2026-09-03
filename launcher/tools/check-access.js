#!/usr/bin/env node
// Проверка доступа к раздаче: видит ли токен репозиторий, какие
// в нём релизы и годятся ли они.
//
//   node tools/check-access.js --repo xxxDenthe/minecraft_server_plague --token ... [--tag pack]
//
// Нужен потому, что «релиза с тегом pack нет» — правда, но бесполезная:
// причин у неё четыре, и различить их можно только несколькими
// запросами. Пусть их делает скрипт, а не человек в браузере.

import { apiHeaders, checkToken } from '../src/main/github.js';

const API = 'https://api.github.com';

function parseArgs(argv) {
  const args = { tag: 'pack' };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) throw new Error(`не понимаю аргумент «${token}»`);

    const key = token.slice(2);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`у ключа --${key} нет значения`);

    args[key] = value;
    i += 1;
  }

  if (!args.repo?.includes('/')) throw new Error('нужен --repo вида владелец/репозиторий');
  if (!args.token) throw new Error('нужен --token');
  checkToken(args.token);

  return args;
}

async function ask(url, token, accept) {
  const response = await fetch(url, { headers: apiHeaders(token, accept), redirect: 'follow' });
  const text = await response.text();

  let body = null;
  try {
    body = JSON.parse(text);
  } catch {
    // не JSON — оставляем null, текст покажем при ошибке
  }

  return { status: response.status, body, text, headers: response.headers };
}

const yes = (v) => (v ? 'да' : 'нет');

async function main() {
  const args = parseArgs(process.argv.slice(2));
  let verdict = 'всё готово к выкладыванию';

  console.log(`репозиторий: ${args.repo}`);
  console.log(`тег релиза:  ${args.tag}`);
  console.log(`токен:       ${args.token.slice(0, 12)}… (${args.token.length} символов)\n`);

  // 1. Кто мы
  const me = await ask(`${API}/user`, args.token);
  if (me.status === 401) {
    console.log('ТОКЕН НЕ РАБОТАЕТ ВООБЩЕ (401).');
    console.log('Он отозван, просрочен или скопирован не целиком. Сделайте новый.');
    return;
  }
  console.log(`1. Токен действителен, аккаунт: ${me.body?.login ?? '(имя не отдано)'}`);

  // 2. Видит ли токен репозиторий
  const repo = await ask(`${API}/repos/${args.repo}`, args.token);
  if (repo.status === 404) {
    console.log('\n2. РЕПОЗИТОРИЙ НЕ ВИДЕН (404). Одно из двух:');
    console.log('   — в токене не выбран этот репозиторий (Repository access);');
    console.log('   — опечатка во владельце или названии.');
    console.log(`   Проверьте, что https://github.com/${args.repo} открывается у вас в браузере.`);
    return;
  }
  if (!repo.body) {
    console.log(`\n2. GitHub ответил ${repo.status}: ${repo.text.slice(0, 200)}`);
    return;
  }

  console.log(`2. Репозиторий виден. Приватный: ${yes(repo.body.private)}`);
  console.log(`   права токена: push ${yes(repo.body.permissions?.push)}, admin ${yes(repo.body.permissions?.admin)}`);

  if (!repo.body.permissions?.push) {
    console.log('   ВНИМАНИЕ: без права записи выложить пак нельзя.');
    console.log('   Нужно Permissions → Repository permissions → Contents: Read and write.');
    verdict = 'токену не хватает прав на запись';
  }

  // 3. Какие вообще есть релизы. Этот список, в отличие от поиска
  // по тегу, показывает и черновики — а черновик и есть самая частая
  // причина «релиза нет».
  const releases = await ask(`${API}/repos/${args.repo}/releases`, args.token);
  const list = Array.isArray(releases.body) ? releases.body : [];

  console.log(`\n3. Релизов в репозитории: ${list.length}`);
  for (const r of list) {
    const marks = [r.draft ? 'ЧЕРНОВИК' : 'опубликован', r.prerelease ? 'предрелиз' : null]
      .filter(Boolean)
      .join(', ');
    console.log(`   — тег «${r.tag_name ?? '(нет тега)'}», ${marks}, ассетов ${r.assets?.length ?? 0}`);
  }

  const draft = list.find((r) => r.draft && r.tag_name === args.tag);
  const published = list.find((r) => !r.draft && r.tag_name === args.tag);

  console.log('');

  if (published) {
    console.log(`4. Релиз «${args.tag}» опубликован — можно выкладывать.`);
  } else if (draft) {
    console.log(`4. Релиз «${args.tag}» СОХРАНЁН КАК ЧЕРНОВИК.`);
    console.log('   Черновик недоступен по тегу: тега у него ещё нет. И лаунчер');
    console.log('   игрока его тоже не увидит — черновики видны только тем, у кого');
    console.log('   есть доступ на запись.');
    console.log('   Откройте релиз на GitHub и нажмите Publish release.');
    verdict = 'релиз лежит черновиком, нажмите Publish release';
  } else if (list.length === 0) {
    console.log('4. Релизов нет совсем. Создайте: Releases → Draft a new release →');
    console.log(`   Choose a tag → напечатать «${args.tag}» → Create new tag → Publish release.`);
    verdict = 'релиз ещё не создан';
  } else {
    console.log(`4. Релиз с тегом «${args.tag}» не найден, но другие релизы есть.`);
    console.log('   Сверьте тег: он написан выше в списке. Регистр важен.');
    verdict = `тег не совпадает — посмотрите список выше`;
  }

  console.log(`\nИТОГ: ${verdict}`);
}

main().catch((err) => {
  console.error(`проверка не удалась: ${err.message}`);
  process.exitCode = 1;
});
