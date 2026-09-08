# Каталог строений

Датапак ставит 148 модовых строений по сетке с известными координатами.
Дальше `tools/world2schem.py` вырезает их из мира в готовые `.schem`.

## Порядок

1. Новый мир: тип **Суперплоский**, пресет по умолчанию (classic flat),
   режим творческий, читы включены. Дальность прорисовки — **24 чанка
   или больше**, иначе дальние слоты окажутся в непрогруженных чанках
   и `/place` откажется работать.
   Слои плоского мира скрипт выемки отбрасывает сам.
2. Скопировать эту папку в `saves/<мир>/datapacks/structure_catalog/`, затем `/reload`.
3. Для каждой партии от 1 до 10 выполнить две команды подряд:

   ```
   /function lmpc_struct:go_1
   /function lmpc_struct:place_1
   ```

   Между `go` и `place` подождать пару секунд, пока прогрузятся чанки.
4. Выйти из мира (важно: иначе куски не сохранятся на диск).
5. `python tools/world2schem.py "<путь к saves/мир>" -o <куда>`

## Партии

### 1 — `/function lmpc_struct:go_1` + `place_1`
```
aquamirae:pirate_outpost
aquamirae:pirate_shelter
aquamirae:pirate_ship
aquamirae:shipwreck
galosphere:forgotten_ruins
galosphere:pink_salt_shrine
minecraft:village_taiga
nova_structures:badlands_miner_outpost
nova_structures:bunker
nova_structures:conduit_ruin
nova_structures:creeping_crypt
nova_structures:deepslate_camp
nova_structures:desert_ruins
nova_structures:end_castle
nova_structures:end_lighthouse
nova_structures:end_ship
```

### 2 — `/function lmpc_struct:go_2` + `place_2`
```
nova_structures:firewatch_tower_birch
nova_structures:firewatch_tower_cherry
nova_structures:firewatch_tower_dark_oak
nova_structures:firewatch_tower_forest
nova_structures:firewatch_tower_jungle
nova_structures:firewatch_tower_mangrove
nova_structures:firewatch_tower_savanna
nova_structures:firewatch_tower_swamp
nova_structures:firewatch_tower_taiga
nova_structures:hamlet
nova_structures:illager_camp
nova_structures:illager_hideout
nova_structures:illager_manor
nova_structures:jungle_ruins
nova_structures:lone_citadel
nova_structures:mangrove_witch_hut
```

### 3 — `/function lmpc_struct:go_3` + `place_3`
```
nova_structures:nether_keep
nova_structures:nether_port
nova_structures:nether_skeleton_tower_crimson
nova_structures:nether_skeleton_tower_soul
nova_structures:nether_skeleton_tower_warped
nova_structures:nether_skeleton_tower_waste
nova_structures:piglin_camp
nova_structures:piglin_donjon
nova_structures:piglin_outstation
nova_structures:remnant_bee_keeper
nova_structures:remnant_big_remnant
nova_structures:remnant_big_remnant_2
nova_structures:remnant_big_remnant_3
nova_structures:remnant_birch_graveyard
nova_structures:remnant_bridge_remnant
nova_structures:remnant_bunny_base
```

### 4 — `/function lmpc_struct:go_4` + `place_4`
```
nova_structures:remnant_classic_village
nova_structures:remnant_desert_remnant
nova_structures:remnant_forest_smith
nova_structures:remnant_frog_ranch
nova_structures:remnant_graveyard
nova_structures:remnant_medium_remnant
nova_structures:remnant_medium_remnant_2
nova_structures:remnant_miner_hut
nova_structures:remnant_mud_brick_constructor
nova_structures:remnant_ominous_shop
nova_structures:remnant_ruin_farmer
nova_structures:remnant_ruin_smith
nova_structures:remnant_sawmill
nova_structures:remnant_school_remnant
nova_structures:remnant_taiga_castle
nova_structures:remnant_woodland_hud
```

### 5 — `/function lmpc_struct:go_5` + `place_5`
```
nova_structures:remnant_zombie_horse_ranch
nova_structures:ruin_town
nova_structures:shrine_combat_tier_1
nova_structures:shrine_combat_tier_2
nova_structures:shrine_combat_tier_3
nova_structures:shrine_combat_tier_4
nova_structures:shrine_combat_tier_5
nova_structures:shrine_tower
nova_structures:skeleton_camp_crimson
nova_structures:skeleton_camp_soul
nova_structures:skeleton_camp_warped
nova_structures:skeleton_camp_waste
nova_structures:stray_fort
nova_structures:tavern_acacia
nova_structures:tavern_birch
nova_structures:tavern_cherry
```

### 6 — `/function lmpc_struct:go_6` + `place_6`
```
nova_structures:tavern_dark_oak
nova_structures:tavern_desert
nova_structures:tavern_jungle
nova_structures:tavern_mangrove
nova_structures:tavern_oak
nova_structures:tavern_snowy
nova_structures:tavern_spruce
nova_structures:tavern_swamp
nova_structures:toxic_lair
nova_structures:trident_trial_monument
nova_structures:undead_crypt
nova_structures:underground_house
nova_structures:village_birch
nova_structures:village_jungle
nova_structures:village_swamp
nova_structures:well_birch
```

### 7 — `/function lmpc_struct:go_7` + `place_7`
```
nova_structures:well_dark_oak
nova_structures:well_jungle
nova_structures:well_oak
nova_structures:well_savana
nova_structures:well_spruce
nova_structures:wild_ruin
nova_structures:witch_villa
supplementaries:galleon
supplementaries:road_sign
towns_and_towers:mimic_desert
towns_and_towers:pillager_outpost_badlands
towns_and_towers:pillager_outpost_beach
towns_and_towers:pillager_outpost_birch_forest
towns_and_towers:pillager_outpost_desert
towns_and_towers:pillager_outpost_flower_forest
towns_and_towers:pillager_outpost_forest
```

### 8 — `/function lmpc_struct:go_8` + `place_8`
```
towns_and_towers:pillager_outpost_grove
towns_and_towers:pillager_outpost_jungle
towns_and_towers:pillager_outpost_meadow
towns_and_towers:pillager_outpost_mushroom_fields
towns_and_towers:pillager_outpost_ocean
towns_and_towers:pillager_outpost_old_growth_taiga
towns_and_towers:pillager_outpost_savanna
towns_and_towers:pillager_outpost_savanna_plateau
towns_and_towers:pillager_outpost_snowy_beach
towns_and_towers:pillager_outpost_snowy_plains
towns_and_towers:pillager_outpost_snowy_slopes
towns_and_towers:pillager_outpost_snowy_taiga
towns_and_towers:pillager_outpost_sparse_jungle
towns_and_towers:pillager_outpost_sunflower_plains
towns_and_towers:pillager_outpost_swamp
towns_and_towers:pillager_outpost_taiga
```

### 9 — `/function lmpc_struct:go_9` + `place_9`
```
towns_and_towers:pillager_outpost_wooded_badlands
towns_and_towers:village_badlands
towns_and_towers:village_beach
towns_and_towers:village_birch_forest
towns_and_towers:village_flower_forest
towns_and_towers:village_forest
towns_and_towers:village_grove
towns_and_towers:village_jungle
towns_and_towers:village_meadow
towns_and_towers:village_mushroom_fields
towns_and_towers:village_ocean
towns_and_towers:village_old_growth_taiga
towns_and_towers:village_savanna_plateau
towns_and_towers:village_snowy_slopes
towns_and_towers:village_snowy_taiga
towns_and_towers:village_sparse_jungle
```

### 10 — `/function lmpc_struct:go_10` + `place_10`
```
towns_and_towers:village_sunflower_plains
towns_and_towers:village_swamp
towns_and_towers:village_wooded_badlands
towns_and_towers:wreckage_ocean
```
