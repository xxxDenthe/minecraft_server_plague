#!/usr/bin/env node
// Сборка манифеста из готовой папки пака. Руками манифест не пишут:
// он разойдётся с действительностью на первой же правке конфига.
//
//   node tools/build-manifest.js --base-url https://github.com/.../download/pack-12/
//
// Ключи:
//   --dir        папка с паком (по умолчанию pack-build)
//   --out        куда положить манифест (по умолчанию pack.json)
//   --base-url   префикс адреса раздачи, обязателен
//   --minecraft  версия Minecraft (по умолчанию 1.21.1)
//   --neoforge   версия NeoForge (по умолчанию 21.1.249)
//   --managed    управляемые папки через запятую
//   --server     host:port сервера сессии
//   --max-ram    память по умолчанию, МБ

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

import { parseManifest } from '../src/main/manifest.js';
import { PROTECTED } from '../src/main/sync.js';

const DEFAULTS = {
  dir: 'pack-build',
  out: 'pack.json',
  minecraft: '1.21.1',
  neoforge: '21.1.249',
  managed: 'mods,config,defaultconfigs,kubejs,resourcepacks,shaderpacks',
  'max-ram': '6144',
};

function parseArgs(argv) {
  const args = { ...DEFAULTS };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) throw new Error(`не понимаю аргумент «${token}»`);

    const key = token.slice(2);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`у ключа --${key} нет значения`);

    args[key] = value;
    i += 1;
  }

  if (!args['base-url']) throw new Error('нужен --base-url: без него игроку неоткуда качать файлы');
  return args;
}

async function sha256(file) {
  const hash = createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

async function walk(dir, base) {
  let entries;
  try {
    entries = await fsp.readdir(dir, { withFileTypes: true });
  } catch (err) {
    if (err.code === 'ENOENT') return [];
    throw err;
  }

  const out = [];
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(full, base)));
    else out.push(path.relative(base, full).split(path.sep).join('/'));
  }
  return out;
}

// Предыдущий манифест нужен ровно для одного: packVersion растёт,
// а не сбрасывается в единицу каждый раз, когда скрипт запустили
// в чистой папке.
async function previousVersion(out) {
  try {
    return parseManifest(await fsp.readFile(out, 'utf8')).packVersion;
  } catch {
    return 0;
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const packDir = path.resolve(args.dir);
  const managedDirs = args.managed.split(',').map((d) => d.trim()).filter(Boolean);
  const baseUrl = args['base-url'].endsWith('/') ? args['base-url'] : `${args['base-url']}/`;

  const files = [];
  for (const dir of managedDirs) {
    for (const relative of await walk(path.join(packDir, dir), packDir)) {
      // Пользовательские файлы в манифест не попадают: иначе лаунчер
      // будет затирать игроку настройки при каждом запуске.
      if (relative.split('/').some((part) => PROTECTED.includes(part))) continue;

      const full = path.join(packDir, relative);
      files.push({
        path: relative,
        sha256: await sha256(full),
        size: (await fsp.stat(full)).size,
        // Имя файла в адресе кодируется: в названиях модов встречаются
        // и пробелы, и плюсы.
        url: baseUrl + relative.split('/').map(encodeURIComponent).join('/'),
      });
    }
  }

  if (files.length === 0) {
    throw new Error(`в ${packDir} не нашлось ни одного файла — проверьте --dir и --managed`);
  }

  const manifest = {
    packVersion: (await previousVersion(args.out)) + 1,
    minecraft: args.minecraft,
    neoforge: args.neoforge,
    java: { major: 21 },
    launch: {
      maxRamMb: Number.parseInt(args['max-ram'], 10),
      jvmArgs: ['-XX:+UseG1GC', '-XX:MaxGCPauseMillis=50'],
    },
    managedDirs,
    files,
  };

  if (args.server) {
    const [host, port = '25565'] = args.server.split(':');
    manifest.server = { host, port: Number.parseInt(port, 10) };
  }

  const text = `${JSON.stringify(manifest, null, 2)}\n`;

  // Манифест, который не проходит нашу же валидацию, не должен доехать
  // до игрока.
  parseManifest(text);

  await fsp.writeFile(args.out, text, 'utf8');

  const mods = files.filter((f) => f.path.startsWith('mods/')).length;
  const bytes = files.reduce((sum, f) => sum + f.size, 0);

  console.log(`${args.out}: версия ${manifest.packVersion}, файлов ${files.length}, из них модов ${mods}`);
  console.log(`объём раздачи: ${(bytes / 1024 / 1024).toFixed(1)} МБ`);
  console.log('сверьте число модов с mods/MODLIST.md — там же лежит эталонный список');
}

main().catch((err) => {
  console.error(`сборка манифеста не удалась: ${err.message}`);
  process.exitCode = 1;
});
