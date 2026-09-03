// Мост между окном и основным процессом. Окно не получает доступа к Node —
// только к тем функциям, которые перечислены здесь явно.

import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('launcher', {
  // Пока пусто: обработчики появятся в Task 11 и 12.
  onProgress: (handler) => {
    ipcRenderer.on('progress', (_event, payload) => handler(payload));
  },
});
