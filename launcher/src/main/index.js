// Единственный файл лаунчера, которому разрешено знать про Electron.
// Всё остальное в src/main/ — обычный Node, который тестируется без окна.
// Это стережёт test/purity.test.js.

import { app, BrowserWindow, ipcMain, shell } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import * as paths from './paths.js';
import { readConfig, writeConfig } from './config.js';
import { play, watchForUpdates, gameLogFile } from './install.js';

const here = path.dirname(fileURLToPath(import.meta.url));

let window = null;
let running = null;
let stopWatching = null;

function send(channel, payload) {
  if (!window || window.isDestroyed()) return;
  window.webContents.send(channel, payload);
}

function createWindow() {
  window = new BrowserWindow({
    width: 900,
    height: 600,
    minWidth: 700,
    minHeight: 500,
    backgroundColor: '#14100f',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(here, '..', 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  window.loadFile(path.join(here, '..', 'renderer', 'index.html'));
}

ipcMain.handle('config:read', () => readConfig());

ipcMain.handle('config:write', (_event, patch) => writeConfig({ ...readConfig(), ...patch }));

ipcMain.handle('log:open', () => shell.showItemInFolder(gameLogFile()));

ipcMain.handle('folder:open', () => shell.openPath(paths.instance()));

ipcMain.handle('game:play', async (_event, { nickname, maxRamMb } = {}) => {
  if (running) return { started: false, reason: 'игра уже запущена' };

  try {
    const session = await play({
      nickname,
      maxRamMb,
      onProgress: (event) => send('progress', event),
      // Лог игры идёт в окно построчно: разбор чужого краша не должен
      // превращаться в переписку «пришли скриншот».
      onLine: (line) => send('log', line),
    });

    running = session.process;

    // Пока игра идёт, лаунчер следит за манифестом. Файлы не трогаются:
    // подменять джарник под работающей игрой нельзя.
    stopWatching = watchForUpdates({
      knownVersion: readConfig().packVersion,
      onUpdate: (version) => send('update-available', version),
      onError: (err) => send('log', `не удалось проверить обновление: ${err.message}`),
    });

    running.on('close', (code) => {
      running = null;
      stopWatching?.();
      stopWatching = null;
      send('game-closed', code);
    });

    return { started: true, logFile: session.logFile };
  } catch (err) {
    send('log', `не удалось запустить: ${err.message}`);
    return { started: false, reason: err.message };
  }
});

app.whenReady().then(() => {
  paths.ensureDirs();
  createWindow();

  // На macOS принято переоткрывать окно по клику на иконку. Первая версия
  // собирается только под Windows, но обработчик стоит копейки.
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  stopWatching?.();
  // Игру за собой не убиваем: игрок закрыл лаунчер, а не игру.
  if (process.platform !== 'darwin') app.quit();
});
