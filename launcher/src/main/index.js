// Единственный файл лаунчера, которому разрешено знать про Electron.
// Всё остальное в src/main/ — обычный Node, который тестируется без окна.
// Это стережёт test/purity.test.js.

import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

let window = null;

function createWindow() {
  window = new BrowserWindow({
    width: 900,
    height: 600,
    minWidth: 700,
    minHeight: 500,
    backgroundColor: '#14100f',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(here, '..', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  window.loadFile(path.join(here, '..', 'renderer', 'index.html'));
}

app.whenReady().then(() => {
  createWindow();

  // На macOS принято переоткрывать окно по клику на иконку. Первая версия
  // собирается только под Windows, но обработчик стоит копейки.
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
