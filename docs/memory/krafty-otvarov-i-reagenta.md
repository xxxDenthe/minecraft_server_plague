---
name: krafty-otvarov-i-reagenta
description: "Чума: рецепты отваров и очищающего реагента лежат в двух модах, KubeJS-переопределений нет"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 39fc62d5-5383-415c-a906-b493e07a562f
  modified: 2026-09-08T18:05:01.002Z
---

Крафты предметов чумы (проверено 2026-09-08) раскиданы по **двум** модам,
всё ванильное `crafting_shapeless`, никаких Create-рецептов и никаких
переопределений в KubeJS:

```
plaguecore/src/main/resources/data/plaguecore/recipe/
    plague_brew.json     бутылка + коричневый гриб + сахар + любой маленький цветок
    plague_mask.json

lmpc_classes/src/main/resources/data/lmpc_classes/recipe/
    plague_bloom.json     3 blighted_grass + 1 spore_sac
    cleansing_agent.json  2 plague_bloom + бутылка          ← «очищающий реагент»
    clerics_brew.json     plague_brew + spore_sac + золотая морковь
    clerics_pendant.json
    censer.json
    andesite_purifier.json
    brass_purifier.json
```

Один реагент = 6 гнилой травы + 2 споровых мешка + 1 бутылка.

**Why:** искать долго — «отвар клирика» живёт не в `plaguecore`, а
в `lmpc_classes`, и слово «реагент» в коде не встречается вовсе,
предмет называется `cleansing_agent`. По слову «реагент» грепается
только `docs/`.

**How to apply:** правки баланса крафтов — в эти json, пересборка мода
обязательна (в конфиг они не вынесены, в отличие от чисел заражения).
Проверки «ты клирик?» в рецепте `clerics_brew` нет: скрафтить может
любой, ограничение стоит на применении предмета.

Связано: [[parallelnaya-sessiya-v-plaguecore]]
