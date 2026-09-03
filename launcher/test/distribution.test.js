// Ошибка здесь молчалива в худшем виде: установщик соберётся,
// запустится, поставит клиент — и не привезёт ни одного мода,
// потому что переменных окружения на машине игрока нет.

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';

import { distribution, CONFIG_FILE } from '../src/main/distribution.js';
import { packSource } from '../src/main/install.js';

let saved = null;

beforeEach(() => {
  saved = fs.existsSync(CONFIG_FILE) ? fs.readFileSync(CONFIG_FILE, 'utf8') : null;
  fs.rmSync(CONFIG_FILE, { force: true });

  for (const key of ['PLAGUE_REPO', 'PLAGUE_RELEASE_TAG', 'PLAGUE_MANIFEST_TOKEN', 'PLAGUE_MANIFEST_URL']) {
    delete process.env[key];
  }
});

afterEach(() => {
  fs.rmSync(CONFIG_FILE, { force: true });
  if (saved !== null) fs.writeFileSync(CONFIG_FILE, saved, 'utf8');
});

describe('адрес раздачи в сборке', () => {
  it('без файла и без переменных раздачи нет — это рабочее состояние', () => {
    expect(distribution().repo).toBe('');
    expect(packSource(distribution())).toEqual({ kind: 'none' });
  });

  it('файл читается', () => {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify({ repo: 'o/r', tag: 'pack', token: 't' }), 'utf8');

    expect(packSource(distribution())).toEqual({
      kind: 'github',
      owner: 'o',
      repo: 'r',
      tag: 'pack',
      token: 't',
    });
  });

  it('битый файл не роняет лаунчер — он просто соберётся без пака', () => {
    fs.writeFileSync(CONFIG_FILE, 'это не json', 'utf8');
    expect(distribution().repo).toBe('');
  });

  it('переменные окружения работают при отладке из терминала', () => {
    process.env.PLAGUE_REPO = 'o/r';
    process.env.PLAGUE_MANIFEST_TOKEN = 't';

    expect(packSource(distribution())).toMatchObject({ kind: 'github', owner: 'o', repo: 'r' });
  });

  it('файл сильнее переменных: в собранном лаунчере правда только в нём', () => {
    process.env.PLAGUE_REPO = 'из/переменной';
    fs.writeFileSync(CONFIG_FILE, JSON.stringify({ repo: 'из/файла', token: 't' }), 'utf8');

    expect(distribution().repo).toBe('из/файла');
  });

  it('репозиторий без слэша не принимается за адрес', () => {
    expect(packSource({ repo: 'простоСлово', tag: 'pack', token: 't' })).toEqual({ kind: 'none' });
  });

  it('свой сервер вместо GitHub — запасной путь', () => {
    expect(packSource({ repo: '', manifestUrl: 'https://пример.рф/pack.json', token: '' })).toEqual({
      kind: 'url',
      url: 'https://пример.рф/pack.json',
      token: '',
    });
  });
});
