// Единственный файл лаунчера, которому разрешено знать про Electron.
// Всё остальное в src/main/ — обычный Node, который тестируется без окна.
// Это стережёт test/purity.test.js.

import { app, BrowserWindow, ipcMain, shell } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import * as paths from './paths.js';
import { readConfig, writeConfig } from './config.js';
import { play, watchForUpdates, gameLogFile } from './install.js';
import { fetchSkinPng } from './skin.js';

const here = path.dirname(fileURLToPath(import.meta.url));

// Приглашение в Discord: единственное место, где оно живёт. Меняется —
// меняем здесь и пересобираем.
const DISCORD_URL = 'https://discord.gg/7GKFA9zvX5';

// Фиксированный размер 16:9 (бриф). Ни ресайза мышью, ни полного экрана:
// кнопка «во весь экран» — пасхалка, окно физически не разворачивается.
const WIN_WIDTH = 1280;
const WIN_HEIGHT = 720;

let window = null;
let running = null;
let stopWatching = null;

function send(channel, payload) {
  if (!window || window.isDestroyed()) return;
  window.webContents.send(channel, payload);
}

function createWindow() {
  window = new BrowserWindow({
    width: WIN_WIDTH,
    height: WIN_HEIGHT,
    useContentSize: true,
    resizable: false,
    maximizable: false,
    fullscreenable: false,
    // В собранном .exe иконку окну даёт сам исполняемый файл; это для
    // режима разработки (npm start) и панели задач.
    icon: path.join(here, '..', 'renderer', 'assets', 'lmpc-icon.ico'),
    backgroundColor: '#0c0d0f',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(here, '..', 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Меню приложения лаунчеру не нужно — и по Alt оно всплывать не должно.
  window.removeMenu();

  window.loadFile(path.join(here, '..', 'renderer', 'index.html'));
}

ipcMain.handle('config:read', () => readConfig());

ipcMain.handle('config:write', (_event, patch) => writeConfig({ ...readConfig(), ...patch }));

ipcMain.handle('log:open', () => shell.showItemInFolder(gameLogFile()));

ipcMain.handle('folder:open', () => shell.openPath(paths.instance()));

ipcMain.handle('discord:open', () => shell.openExternal(DISCORD_URL));

ipcMain.handle('skin:fetch', (_event, nickname) => fetchSkinPng(nickname));

// Внешние ссылки со стороны нашего же окна (регистрация ely.by, видеогайд).
// Проверка на https — чтобы опечатка в ссылке не открыла file:// или
// неизвестную схему; удалённого контента, который мог бы это подсунуть,
// в окне нет.
ipcMain.handle('link:open', (_event, url) => {
  if (typeof url === 'string' && /^https:\/\/\S+$/.test(url)) shell.openExternal(url);
});

ipcMain.handle('game:play', async (_event, { nickname, maxRamMb, minRamMb } = {}) => {
  if (running) return { started: false, reason: 'игра уже запущена' };

  try {
    const session = await play({
      nickname,
      maxRamMb,
      minRamMb,
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
