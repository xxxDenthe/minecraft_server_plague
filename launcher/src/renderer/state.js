// Состояния лаунчера и крошечное хранилище. Компоненты подписываются
// и перерисовывают свою часть — без фреймворка, но и без глобального
// хаоса из разбросанных переменных.

export const State = Object.freeze({
  IDLE: 'idle',           // окно открыто, ничего не происходит
  CHECKING: 'checking',   // сверка манифеста и файлов
  LOADING: 'loading',     // загрузка Java, клиента, ассетов, NeoForge
  UPDATING: 'updating',   // синхронизация модпака
  LAUNCHING: 'launching', // команда собрана, игра стартует
  RUNNING: 'running',     // игра запущена, лаунчер ждёт её закрытия
  ERROR: 'error',
});

// Стадии прогресса из основного процесса → состояние лаунчера.
export function stateForStage(stage) {
  switch (stage) {
    case 'manifest': return State.CHECKING;
    case 'pack': return State.UPDATING;
    case 'launch': return State.LAUNCHING;
    case 'java':
    case 'minecraft':
    case 'assets':
    case 'neoforge': return State.LOADING;
    default: return State.LOADING;
  }
}

export function createStore(initial) {
  let value = { ...initial };
  const subscribers = new Set();

  return {
    get: () => value,
    set(patch) {
      value = { ...value, ...patch };
      for (const fn of subscribers) fn(value);
    },
    subscribe(fn) {
      subscribers.add(fn);
      fn(value);
      return () => subscribers.delete(fn);
    },
  };
}
