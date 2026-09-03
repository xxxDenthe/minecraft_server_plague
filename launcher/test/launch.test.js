// Команда запуска собирается из двух профилей, и ошибка в ней даёт
// либо краш на старте, либо — хуже — молча не тот UUID.

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { selectArguments, substitute, buildClasspath, buildCommand } from '../src/main/launch.js';
import { offlineUuid } from '../src/main/offline.js';

let temp;
let savedRoot;

beforeEach(() => {
  savedRoot = process.env.LMPC_LAUNCHER_ROOT;
  temp = fs.mkdtempSync(path.join(os.tmpdir(), 'plague-launch-'));
  process.env.LMPC_LAUNCHER_ROOT = temp;
});

afterEach(() => {
  if (savedRoot === undefined) delete process.env.LMPC_LAUNCHER_ROOT;
  else process.env.LMPC_LAUNCHER_ROOT = savedRoot;
  fs.rmSync(temp, { recursive: true, force: true });
});

const lib = (name, artifactPath) => ({
  name,
  downloads: { artifact: { path: artifactPath, sha1: 'a', size: 1, url: 'https://x' } },
});

const vanillaJson = {
  id: '1.21.1',
  type: 'release',
  mainClass: 'net.minecraft.client.main.Main',
  assetIndex: { id: '17' },
  arguments: {
    jvm: ['-Dminecraft.launcher.brand=${launcher_name}', '-cp', '${classpath}'],
    game: ['--username', '${auth_player_name}', '--uuid', '${auth_uuid}'],
  },
  libraries: [
    lib('org.ow2.asm:asm:9.7', 'org/ow2/asm/asm/9.7/asm-9.7.jar'),
    lib('com.github.oshi:oshi-core:6.4.10', 'com/github/oshi/oshi-core/6.4.10/oshi-core-6.4.10.jar'),
    { ...lib('ca.weblite:java-objc-bridge:1.1', 'ca/weblite/java-objc-bridge/1.1/b.jar'), rules: [{ action: 'allow', os: { name: 'osx' } }] },
  ],
};

const forgeProfile = {
  id: 'neoforge-21.1.249',
  type: 'release',
  inheritsFrom: '1.21.1',
  mainClass: 'cpw.mods.bootstraplauncher.BootstrapLauncher',
  arguments: {
    jvm: ['-DlibraryDirectory=${library_directory}'],
    game: ['--fml.neoForgeVersion', '21.1.249'],
  },
  libraries: [lib('org.ow2.asm:asm:9.10.1', 'org/ow2/asm/asm/9.10.1/asm-9.10.1.jar')],
};

describe('отбор аргументов', () => {
  it('строки берутся как есть', () => {
    expect(selectArguments(['-a', '-b'])).toEqual(['-a', '-b']);
  });

  it('аргумент под чужую ОС отбрасывается', () => {
    const list = [{ rules: [{ action: 'allow', os: { name: 'osx' } }], value: '-XstartOnFirstThread' }];
    expect(selectArguments(list)).toEqual([]);
  });

  it('аргумент под нашу ОС берётся, в том числе списком', () => {
    const list = [{ rules: [{ action: 'allow', os: { name: 'windows' } }], value: ['-Da=1', '-Db=2'] }];
    expect(selectArguments(list)).toEqual(['-Da=1', '-Db=2']);
  });

  it('аргументы по features не включаются никогда', () => {
    const list = [{ rules: [{ action: 'allow', features: { is_demo_user: true } }], value: '--demo' }];
    expect(selectArguments(list)).toEqual([]);
  });
});

describe('подстановки', () => {
  it('заменяются все вхождения', () => {
    expect(substitute(['${a}/${a}', '${b}'], { a: 'x', b: 'y' })).toEqual(['x/x', 'y']);
  });

  it('неизвестное не трогается — видно в логе, что не подставилось', () => {
    expect(substitute(['${неизвестно}'], { a: 'x' })).toEqual(['${неизвестно}']);
  });
});

describe('classpath', () => {
  it('версия NeoForge побеждает ванильную при совпадении координат', () => {
    const cp = buildClasspath(vanillaJson, forgeProfile, 'C:\client.jar');

    const asm = cp.filter((p) => p.includes(`asm${path.sep}`));
    expect(asm).toHaveLength(1);
    expect(asm[0]).toContain('9.10.1');
  });

  it('библиотеки под чужую ОС не попадают', () => {
    const cp = buildClasspath(vanillaJson, forgeProfile, 'C:\client.jar');
    expect(cp.some((p) => p.includes('java-objc-bridge'))).toBe(false);
  });

  it('клиентский джарник идёт последним', () => {
    const cp = buildClasspath(vanillaJson, forgeProfile, 'C:\client.jar');
    expect(cp.at(-1)).toBe('C:\client.jar');
  });
});

describe('команда запуска', () => {
  const command = (over = {}) =>
    buildCommand({ vanillaJson, forgeProfile, clientJar: 'C:\client.jar', nickname: 'Denthe', maxRamMb: 4096, ...over });

  it('главный класс берётся из профиля NeoForge', () => {
    const { args } = command();
    expect(args).toContain('cpw.mods.bootstraplauncher.BootstrapLauncher');
    expect(args).not.toContain('net.minecraft.client.main.Main');
  });

  it('UUID совпадает с offline.js и идёт без дефисов', () => {
    const { args } = command();
    const uuid = args[args.indexOf('--uuid') + 1];

    expect(uuid).toBe(offlineUuid('Denthe').replaceAll('-', ''));
    expect(uuid).not.toContain('-');
  });

  it('память проставляется обоими ключами', () => {
    const { args } = command({ maxRamMb: 8192 });
    expect(args).toContain('-Xmx8192M');
    expect(args[1]).toBe('-Xms1024M');
  });

  it('${version_name} — ванильная версия: по ней NeoForge узнаёт клиентский джарник', () => {
    const { values } = command();
    expect(values.version_name).toBe('1.21.1');
  });

  it('токен не пустой — игра спотыкается о пустой аргумент', () => {
    const { values } = command();
    expect(values.auth_access_token).not.toBe('');
  });

  it('аргументы NeoForge идут после ванильных', () => {
    const { args } = command();
    expect(args.indexOf('--fml.neoForgeVersion')).toBeGreaterThan(args.indexOf('--username'));
  });

  it('адрес сервера подставляется, когда он задан', () => {
    const { args } = command({ server: { host: 'plague.example.net', port: 25565 } });
    expect(args.at(-1)).toBe('plague.example.net:25565');
    expect(args.at(-2)).toBe('--quickPlayMultiplayer');
  });

  it('без ника команда не собирается', () => {
    expect(() => command({ nickname: '' })).toThrow(/ник/);
  });
});
