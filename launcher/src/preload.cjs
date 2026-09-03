// Мост между окном и основным процессом. Окно не получает доступа к Node —
// только к тем функциям, которые перечислены здесь явно.
//
// Расширение .cjs не прихоть: preload грузится в песочнице, а там модули
// ES не поддерживаются, и при `"type": "module"` в package.json обычный
// .js молча не загрузится — окно останется без моста.

const { contextBridge, ipcRenderer } = require('electron');

const on = (channel) => (handler) =>
  ipcRenderer.on(channel, (_event, payload) => handler(payload));

contextBridge.exposeInMainWorld('launcher', {
  readConfig: () => ipcRenderer.invoke('config:read'),
  writeConfig: (patch) => ipcRenderer.invoke('config:write', patch),
  play: (options) => ipcRenderer.invoke('game:play', options),
  openLog: () => ipcRenderer.invoke('log:open'),

  onProgress: on('progress'),
  onLog: on('log'),
  onUpdateAvailable: on('update-available'),
  onGameClosed: on('game-closed'),
});
