# Наблюдатель — модель и анимация

Источник один: `watcher.bbmodel`. Всё остальное из него выгружается.
Java-кубов руками больше не пишут — модель рисует GeckoLib.

* `watcher.bbmodel` — 36 кубов, 7 костей (`watcher` → head, body,
  right_arm, left_arm, right_leg, left_leg), две анимации: `idle` и `run`.
* `watcher.png` — 128×128 при UV-сетке 64×64, то есть двойное
  разрешение. Тело сплошное `#08080B`, светлые пиксели только в лице.
* `make_watcher.py` — **устарел, не запускать.** Он печёт старые шесть
  кубов и старую текстуру 64×64 и затрёт текущую работу. Оставлен
  как история: в его комментариях записано, почему силуэт именно такой.

## Выгрузка в мод

Blockbench, вкладка Console → `risky_eval` через MCP, один вызов:

```js
const база = 'E:/CLAUDE/projects/minecraft_server_plague/plaguecore/src/main/resources/assets/plaguecore/';
Blockbench.writeFile(база + 'geo/watcher.geo.json', { content: Codecs.bedrock.compile() });
Blockbench.writeFile(база + 'animations/watcher.animation.json',
  { content: JSON.stringify(Animator.buildFile(), null, 2) });
Blockbench.writeFile(база + 'textures/entity/watcher.png',
  { content: 'data:image/png;base64,' + Texture.all[0].getBase64(), savetype: 'image' });
Codecs.project.write(Codecs.project.compile(), Project.save_path);
```

Имена анимаций (`idle`, `run`) и костей завязаны на Java: переименуешь
в Blockbench — правь `Watcher.registerControllers` и
`WatcherGeoModel.setCustomAnimations`.

## Ловушки, на которые уже наступили

* **Кадры создаются только в режиме animate.** Сначала
  `Modes.options.animate.select()`, иначе `Timeline` ещё не существует.
* **Мгновенный сбой кадра делается парой ключей через 0.04 с**, а не
  интерполяцией `step`: степ не переживает выгрузку в формат bedrock.
* **Голова доворачивается прибавкой**, а не присвоением — присвоение
  стирает кадры idle.
