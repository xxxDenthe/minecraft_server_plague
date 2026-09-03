// Сеть в тестах не трогаем: живой Adoptium проверяется руками
// (проверено 2026-09-03, см. комментарий в java.js). Здесь — разбор
// ответа и проверка версии запуском.

import { describe, it, expect } from 'vitest';

import { fetchJavaRelease, checkJava, majorFromVersionOutput } from '../src/main/java.js';

const answer = (body, ok = true, status = 200) => async () => ({
  ok,
  status,
  json: async () => body,
});

const RELEASE = [
  {
    release_name: 'jdk-21.0.12.1+1',
    binary: {
      package: {
        name: 'OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip',
        link: 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip',
        size: 48999141,
        checksum: 'd35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636',
      },
    },
  },
];

describe('карточка релиза Adoptium', () => {
  it('разбирается в ссылку, хеш и размер', async () => {
    const r = await fetchJavaRelease(21, { fetchImpl: answer(RELEASE) });

    expect(r.sha256).toBe(RELEASE[0].binary.package.checksum);
    expect(r.size).toBe(48999141);
    expect(r.release).toBe('jdk-21.0.12.1+1');
  });

  it('запись без контрольной суммы — ошибка, а не скачивание вслепую', async () => {
    const broken = [{ binary: { package: { link: 'https://example.net/jre.zip' } } }];
    await expect(fetchJavaRelease(21, { fetchImpl: answer(broken) })).rejects.toThrow(/контрольной суммы/);
  });

  it('пустой ответ не проходит молча', async () => {
    await expect(fetchJavaRelease(21, { fetchImpl: answer([]) })).rejects.toThrow();
  });

  it('не-200 — ошибка с кодом', async () => {
    await expect(fetchJavaRelease(21, { fetchImpl: answer('', false, 503) })).rejects.toThrow(/503/);
  });
});

describe('проверка Java запуском', () => {
  it('чужая версия отвергается', async () => {
    // Берём заведомо не Java — сам node: его -version не содержит «21.».
    // Системную java брать нельзя, тест стал бы зависеть от машины.
    await expect(checkJava(process.execPath, 21)).rejects.toThrow(/не удалось запустить/);
  });

  it('несуществующий файл — ошибка, а не тихое «сойдёт»', async () => {
    await expect(checkJava('C:\нет\такого\java.exe', 21)).rejects.toThrow();
  });
});

describe('разбор вывода java -version', () => {
  it('узнаёт мажорную версию Temurin 21', () => {
    expect(
      majorFromVersionOutput(
        'openjdk version "21.0.12.1" 2026-08-18 LTS\nOpenJDK Runtime Environment Temurin-21.0.12.1+1'
      )
    ).toBe(21);
  });

  it('узнаёт чужую версию — именно её и ловим', () => {
    expect(majorFromVersionOutput('java version "1.8.0_411"')).toBe(1);
    expect(majorFromVersionOutput('openjdk version "25.0.3" 2026-04-21 LTS')).toBe(25);
  });

  it('на мусоре отдаёт null, а не наугад взятое число', () => {
    expect(majorFromVersionOutput('bad option: -version')).toBe(null);
    expect(majorFromVersionOutput('')).toBe(null);
  });
});
