package dev.denthe.plaguecore;

import dev.denthe.plaguecore.core.MaterializationMask;
import dev.denthe.plaguecore.core.PhaseParams;
import dev.denthe.plaguecore.core.PhaseTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Баланс чумы в текстовом файле: `config/plaguecore-common.toml`.
 *
 * Правило проекта: за день до сессии числа правятся блокнотом, без
 * пересборки мода. Файл читается при запуске и перечитывается сам,
 * как только его изменили на диске, — NeoForge следит за ним и шлёт
 * {@link ModConfigEvent.Reloading}.
 *
 * Устройство простое до глупости: конфиг знает про {@link PlagueConstants},
 * {@link PhaseTable} и {@link MaterializationMask} и переписывает их поля.
 * Обратной связи нет — те три класса остаются чистой Java и ничего
 * не знают ни про NeoForge, ни про этот файл. Иначе пакет core потянул бы
 * за собой полмода и перестал бы проверяться обычным JUnit.
 *
 * Чего здесь нет и не будет: размера сетки и длины очереди. Первое
 * записано в сохранение мира, второе читается при загрузке классов,
 * задолго до конфига.
 */
public final class PlagueConfig {
    private PlagueConfig() {}

    private static final ModConfigSpec.Builder СТРОИТЕЛЬ = new ModConfigSpec.Builder();

    // ── распространение ───────────────────────────────────────────────
    private static final ModConfigSpec.IntValue ОЧАГИ;
    private static final ModConfigSpec.DoubleValue СТАРТОВАЯ_ДОЛЯ;
    private static final ModConfigSpec.IntValue НОЧЕЙ_ШРАМА;
    private static final ModConfigSpec.DoubleValue МНОЖИТЕЛЬ_СНА;
    private static final ModConfigSpec.IntValue ПРИБАВКА_СНА;

    // ── Сердце чумы ───────────────────────────────────────────────────
    private static final ModConfigSpec.DoubleValue ЗДОРОВЬЕ_СЕРДЦА;
    private static final ModConfigSpec.DoubleValue РАЗМЕР_СЕРДЦА;

    // ── фазы ──────────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue[] КОНЕЦ_ФАЗЫ =
        new ModConfigSpec.IntValue[PhaseTable.PHASE_COUNT];
    private static final ModConfigSpec.DoubleValue[] БАЗА =
        new ModConfigSpec.DoubleValue[PhaseTable.PHASE_COUNT];
    private static final ModConfigSpec.IntValue[] БЮДЖЕТ =
        new ModConfigSpec.IntValue[PhaseTable.PHASE_COUNT];
    private static final ModConfigSpec.IntValue[] РОСТ_КАЖДЫЕ =
        new ModConfigSpec.IntValue[PhaseTable.PHASE_COUNT];
    private static final ModConfigSpec.IntValue[] РОСТ_НА =
        new ModConfigSpec.IntValue[PhaseTable.PHASE_COUNT];

    // ── поверхность ───────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue БЛОКОВ_ЗА_ТИК;
    private static final ModConfigSpec.IntValue ГЛУБИНА;
    private static final ModConfigSpec.DoubleValue ДОЛЯ_НАРОСТА;
    private static final ModConfigSpec.DoubleValue МЕШКИ_НАВЕРХУ;
    private static final ModConfigSpec.DoubleValue[] ДОЛЯ_УРОВНЯ =
        new ModConfigSpec.DoubleValue[MaterializationMask.НАСТРАИВАЕМЫХ_УРОВНЕЙ];

    // ── подземелье ────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue БЛОКОВ_ЗА_ТИК_ПОД_ЗЕМЛЁЙ;
    private static final ModConfigSpec.IntValue СТОЛБЦОВ_ЗА_ТИК;

    // ── животные ──────────────────────────────────────────────────────
    private static final ModConfigSpec.DoubleValue ШАНС_ВЫВОДКА;
    private static final ModConfigSpec.IntValue ЗОМБИ_В_КУЧКЕ;
    private static final ModConfigSpec.IntValue СКЕЛЕТОВ_В_КУЧКЕ;
    private static final ModConfigSpec.IntValue КУЧЕК_ЗА_НОЧЬ;
    private static final ModConfigSpec.IntValue РАДИУС_ВЫВОДКА;
    private static final ModConfigSpec.IntValue НЕ_БЛИЖЕ_К_ИГРОКУ;

    private static final ModConfigSpec.IntValue ПРОВЕРКА_ЖИВОТНЫХ;
    private static final ModConfigSpec.DoubleValue ШАНС_ЗАРАЖЕНИЯ;
    private static final ModConfigSpec.DoubleValue СТЕНЫ_РЕДКО;
    private static final ModConfigSpec.DoubleValue СТЕНЫ_ГУСТО;
    private static final ModConfigSpec.DoubleValue ЛОЗЫ;
    private static final ModConfigSpec.DoubleValue ПОЛ;
    private static final ModConfigSpec.DoubleValue МЕШКИ;

    // ── игрок ─────────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue ТИК_ИГРОКА;
    private static final ModConfigSpec.IntValue[] ПОРОГ_СТАДИИ =
        new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue[] ЭКСПОЗИЦИЯ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue ПОД_ЗЕМЛЁЙ;
    private static final ModConfigSpec.DoubleValue[] ЗДОРОВЬЕ_СТАДИИ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue ЕДА;
    private static final ModConfigSpec.IntValue УРОН_КАЖДЫЕ;
    private static final ModConfigSpec.DoubleValue УРОН_СТАДИИ_4;
    private static final ModConfigSpec.IntValue[] КАШЕЛЬ_КАЖДЫЕ =
        new ModConfigSpec.IntValue[5];
    private static final ModConfigSpec.DoubleValue[] ШАНС_КАШЛЯ =
        new ModConfigSpec.DoubleValue[5];
    private static final ModConfigSpec.DoubleValue РАДИУС_КАШЛЯ;
    private static final ModConfigSpec.DoubleValue ОЧКОВ_ЗА_КАШЕЛЬ;
    private static final ModConfigSpec.DoubleValue[] СИЛА_ОТВАРА =
        new ModConfigSpec.DoubleValue[6];
    private static final ModConfigSpec.IntValue СБРОС_ОТВАРА;
    private static final ModConfigSpec.IntValue ПОТОЛОК_ОТВАРА;
    private static final ModConfigSpec.DoubleValue ШТРАФ_СМЕРТИ;
    private static final ModConfigSpec.DoubleValue ПОЛ_ПОСТОЯННЫХ;
    private static final ModConfigSpec.DoubleValue ЖЁСТКИЙ_ПОЛ;

    // ── голос ─────────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue МИН_СТАДИЯ;
    private static final ModConfigSpec.DoubleValue ЧАСТОТА_ДРОЖИ;
    private static final ModConfigSpec.DoubleValue[] ПОЛУТОНОВ =
        new ModConfigSpec.DoubleValue[PlagueConstants.VOICE_LEVELS];
    private static final ModConfigSpec.DoubleValue[] ГЛУХОТА =
        new ModConfigSpec.DoubleValue[PlagueConstants.VOICE_LEVELS];
    private static final ModConfigSpec.DoubleValue[] ХРИП =
        new ModConfigSpec.DoubleValue[PlagueConstants.VOICE_LEVELS];
    private static final ModConfigSpec.DoubleValue[] ДЫХАНИЕ =
        new ModConfigSpec.DoubleValue[PlagueConstants.VOICE_LEVELS];
    private static final ModConfigSpec.DoubleValue[] ДРОЖЬ =
        new ModConfigSpec.DoubleValue[PlagueConstants.VOICE_LEVELS];

    public static final ModConfigSpec SPEC;

    static {
        СТРОИТЕЛЬ.comment(
            "Баланс чумы. Все числа можно править на живом сервере:",
            "файл перечитывается сам, как только его сохранили.",
            "Смысл каждого числа — в docs/superpowers/specs/2026-09-03-plague-core-design.md"
        ).push("spread");

        ОЧАГИ = СТРОИТЕЛЬ
            .comment("Сколько отдельных очагов сажается на старте сессии.",
                     "Число определяет темп: зараза растёт только по краю очага,",
                     "поэтому один большой очаг ползёт втрое медленнее тридцати мелких.")
            .defineInRange("startEpicenters", PlagueConstants.START_EPICENTERS, 1, 500);
        СТАРТОВАЯ_ДОЛЯ = СТРОИТЕЛЬ
            .comment("Какая доля мира заражена в первую ночь.")
            .defineInRange("startInfection", окр(PlagueConstants.START_INFECTION_PERCENT), 0.0, 1.0);
        НОЧЕЙ_ШРАМА = СТРОИТЕЛЬ
            .comment("Сколько ночей держится шрам после полной очистки земли.")
            .defineInRange("scarNights", PlagueConstants.SCAR_NIGHTS, 0, 60);
        МНОЖИТЕЛЬ_СНА = СТРОИТЕЛЬ
            .comment("Во сколько раз растёт бюджет ночи, если игроки её проспали.")
            .defineInRange("sleepBudgetMultiplier", окр(PlagueConstants.SLEEP_BUDGET_MULTIPLIER), 1.0, 10.0);
        ПРИБАВКА_СНА = СТРОИТЕЛЬ
            .comment("Насколько сильнее растёт уровень на месте, если ночь проспали.")
            .defineInRange("sleepExtraGrowth", PlagueConstants.SLEEP_EXTRA_GROWTH, 0, 3);

        СТРОИТЕЛЬ.pop().comment(
            "Фазы эпидемии. Главная ручка всей игры: ими правится кривая",
            "распространения. base — вероятность заразить соседний чанк,",
            "budget — потолок новых заражённых чанков за ночь.",
            "Ночи фаз идут по возрастанию; у последней фазы конца нет."
        ).push("phases");

        for (int ф = 0; ф < PhaseTable.PHASE_COUNT; ф++) {
            PhaseParams умолчание = PhaseTable.paramsFor(ф);
            boolean последняя = ф == PhaseTable.PHASE_COUNT - 1;
            СТРОИТЕЛЬ.push("phase" + ф);

            if (!последняя) {
                КОНЕЦ_ФАЗЫ[ф] = СТРОИТЕЛЬ
                    .comment("Последняя ночь этой фазы.")
                    .defineInRange("endNight", PhaseTable.endNightOf(ф), 1, 1000);
            }
            БАЗА[ф] = СТРОИТЕЛЬ
                .comment("Вероятность заразить соседний чанк за ночь.")
                .defineInRange("base", окр(умолчание.base()), 0.0, 1.0);
            БЮДЖЕТ[ф] = СТРОИТЕЛЬ
                .comment("Потолок новых заражённых чанков за ночь.")
                .defineInRange("budget", умолчание.budget(), 0, 4000);
            РОСТ_КАЖДЫЕ[ф] = СТРОИТЕЛЬ
                .comment("Раз во сколько ночей уровень растёт на месте.")
                .defineInRange("growthEveryNights", умолчание.growthEveryNights(), 1, 30);
            РОСТ_НА[ф] = СТРОИТЕЛЬ
                .comment("На сколько уровень растёт за раз.")
                .defineInRange("growthAmount", умолчание.growthAmount(), 0, 5);

            СТРОИТЕЛЬ.pop();
        }

        СТРОИТЕЛЬ.pop().comment("Как чума выглядит на поверхности.").push("surface");

        БЛОКОВ_ЗА_ТИК = СТРОИТЕЛЬ
            .comment("Сколько блоков меняется за тик. Настоящая защита TPS:",
                     "поднимать, пока не просядет тик, и не выше.")
            .defineInRange("blocksPerTick", PlagueConstants.BLOCKS_PER_TICK, 1, 4096);
        ГЛУБИНА = СТРОИТЕЛЬ
            .comment("На сколько блоков вглубь от земли идёт заражение.")
            .defineInRange("depth", PlagueConstants.SURFACE_DEPTH, 1, 32);
        ДОЛЯ_НАРОСТА = СТРОИТЕЛЬ
            .comment("Какая доля подходящих мест обрастает наростом.",
                     "Единица — сплошная плёнка, сквозь неё ничего не просвечивает.")
            .defineInRange("growthPatches", окр(PlagueConstants.GROWTH_PATCH_FRACTION), 0.0, 1.0);
        МЕШКИ_НАВЕРХУ = СТРОИТЕЛЬ
            .comment("Доля столбцов, на которых вырастает споровый мешок.",
                     "0.012 — это примерно три мешка на чанк сплошной Гнили.")
            .defineInRange("sporeSacs", окр(PlagueConstants.SURFACE_SPORE_SAC), 0.0, 1.0);
        for (int у = 1; у <= MaterializationMask.НАСТРАИВАЕМЫХ_УРОВНЕЙ; у++) {
            ДОЛЯ_УРОВНЯ[у - 1] = СТРОИТЕЛЬ
                .comment("Какая доля земли поражена на уровне " + у + ".",
                         "Доли обязаны расти вместе с уровнем: на этом держится то,",
                         "что перерисовка чанка только добавляет блоки. Ряд с ошибкой",
                         "мод выправит сам и напишет об этом в лог.")
                .defineInRange("fractionLevel" + у, окр(MaterializationMask.fractionFor(у)), 0.0, 1.0);
        }

        СТРОИТЕЛЬ.pop().comment("Как чума выглядит в пещерах.").push("cave");

        БЛОКОВ_ЗА_ТИК_ПОД_ЗЕМЛЁЙ = СТРОИТЕЛЬ
            .comment("Свой бюджет блоков за тик: под землёй столбец дороже.")
            .defineInRange("blocksPerTick", PlagueConstants.BLOCKS_PER_TICK_CAVE, 1, 512);
        СТОЛБЦОВ_ЗА_ТИК = СТРОИТЕЛЬ
            .comment("Сколько столбцов чанка просматривается за тик.")
            .defineInRange("columnsPerTick", PlagueConstants.CAVE_COLUMNS_PER_TICK, 1, 256);
        СТЕНЫ_РЕДКО = СТРОИТЕЛЬ
            .comment("Доля стен, покрытая наростом на уровнях 1–2.")
            .defineInRange("wallSparse", окр(PlagueConstants.CAVE_WALL_SPARSE), 0.0, 1.0);
        СТЕНЫ_ГУСТО = СТРОИТЕЛЬ
            .comment("Доля стен, покрытая наростом на Гнили. Не меньше wallSparse.")
            .defineInRange("wallDense", окр(PlagueConstants.CAVE_WALL_DENSE), 0.0, 1.0);
        ЛОЗЫ = СТРОИТЕЛЬ
            .comment("Доля потолка, с которой свисают лозы.")
            .defineInRange("ceilingVines", окр(PlagueConstants.CAVE_CEILING_VINES), 0.0, 1.0);
        ПОЛ = СТРОИТЕЛЬ
            .comment("Доля пола, становящаяся гнилой землёй.")
            .defineInRange("floorRot", окр(PlagueConstants.CAVE_FLOOR_ROT), 0.0, 1.0);
        МЕШКИ = СТРОИТЕЛЬ
            .comment("Доля пола под споровые мешки. Входит в долю гнилого пола.")
            .defineInRange("sporeSacs", окр(PlagueConstants.CAVE_SPORE_SAC), 0.0, 1.0);

        СТРОИТЕЛЬ.pop().comment(
            "Превращение мирных животных в заражённых.",
            "Свинья и корова, оказавшиеся в заражённом чанке, со временем",
            "мутируют и становятся враждебными."
        ).push("animals");

        ПРОВЕРКА_ЖИВОТНЫХ = СТРОИТЕЛЬ
            .comment("Раз во сколько тиков животное проверяется на превращение.")
            .defineInRange("checkTicks", PlagueConstants.ANIMAL_CHECK_TICKS, 20, 1200);
        ШАНС_ЗАРАЖЕНИЯ = СТРОИТЕЛЬ
            .comment("Шанс за проверку на уровне 1. На уровне N умножается на N.",
                     "0 полностью отключает превращение.")
            .defineInRange("infectChance", окр(PlagueConstants.ANIMAL_INFECT_CHANCE), 0.0, 1.0);

        СТРОИТЕЛЬ.pop().comment(
            "Ночные выводки у споровых мешков.",
            "Ночью рядом с мешком вылезает кучка мутировавших зомби и скелетов.",
            "Кубик катает сам мешок на своём случайном тике, поэтому выводок",
            "появляется только в загруженных чанках — там, где есть люди."
        ).push("spawn");

        ШАНС_ВЫВОДКА = СТРОИТЕЛЬ
            .comment("Вероятность, что за одну ночь у одного мешка вылезет кучка.",
                     "0 полностью отключает ночные выводки.")
            .defineInRange("chancePerNight", окр(PlagueConstants.SPAWN_CHANCE_PER_NIGHT), 0.0, 1.0);
        ЗОМБИ_В_КУЧКЕ = СТРОИТЕЛЬ
            .comment("Мутировавших зомби в одной кучке.")
            .defineInRange("zombies", PlagueConstants.SPAWN_ZOMBIES, 0, 12);
        СКЕЛЕТОВ_В_КУЧКЕ = СТРОИТЕЛЬ
            .comment("Скелетов в одной кучке. Скелеты ванильные: утром сгорают сами.")
            .defineInRange("skeletons", PlagueConstants.SPAWN_SKELETONS, 0, 12);
        КУЧЕК_ЗА_НОЧЬ = СТРОИТЕЛЬ
            .comment("Сколько кучек за ночь может вылезти рядом с одним игроком.",
                     "Главная защита от армии: в гнилом поле мешков бывает под сотню.")
            .defineInRange("maxGroupsPerNight", PlagueConstants.SPAWN_MAX_GROUPS_PER_NIGHT, 0, 20);
        РАДИУС_ВЫВОДКА = СТРОИТЕЛЬ
            .comment("Радиус вокруг мешка, в котором ищется место для мобов.")
            .defineInRange("radius", PlagueConstants.SPAWN_RADIUS, 1, 16);
        НЕ_БЛИЖЕ_К_ИГРОКУ = СТРОИТЕЛЬ
            .comment("Ближе этого к игроку кучка не появляется — не лезем в лицо.")
            .defineInRange("minPlayerDistance", PlagueConstants.SPAWN_MIN_PLAYER_DISTANCE, 0, 64);

        СТРОИТЕЛЬ.pop().comment(
            "Чума в самом игроке: как копится, чем бьёт, чем лечится.",
            "Заражённость — число от 0 до 100. Стадия выводится из него."
        ).push("player");

        ТИК_ИГРОКА = СТРОИТЕЛЬ
            .comment("Раз во сколько тиков пересчитывается заражённость. 20 — раз в секунду.")
            .defineInRange("tickInterval", PlagueConstants.PLAYER_TICK_INTERVAL, 1, 200);

        for (int с = 0; с < 4; с++) {
            ПОРОГ_СТАДИИ[с] = СТРОИТЕЛЬ
                .comment("С какого числа очков начинается стадия " + (с + 1) + ".")
                .defineInRange("stage" + (с + 1) + "At",
                    PlagueConstants.PLAYER_STAGE_THRESHOLDS[с], 1, 100);
        }

        for (int у = 0; у < 5; у++) {
            ЭКСПОЗИЦИЯ[у] = СТРОИТЕЛЬ
                .comment(у == 0
                    ? "Очков заражённости за секунду в чистом чанке. Отрицательное: воздух лечит."
                    : "Очков заражённости за секунду в чанке уровня " + у + ".")
                .defineInRange("exposureLevel" + у,
                    окр(PlagueConstants.PLAYER_EXPOSURE[у]), -5.0, 5.0);
        }

        ПОД_ЗЕМЛЁЙ = СТРОИТЕЛЬ
            .comment("Во сколько раз быстрее копится зараза под землёй.")
            .defineInRange("undergroundMultiplier",
                окр(PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER), 1.0, 5.0);

        for (int с = 0; с < 5; с++) {
            ЗДОРОВЬЕ_СТАДИИ[с] = СТРОИТЕЛЬ
                .comment("Сколько HP временно отнимает стадия " + с + ".")
                .defineInRange("stage" + с + "HealthPenalty",
                    окр(PlagueConstants.PLAYER_STAGE_HEALTH[с]), 0.0, 18.0);
        }

        ЕДА = СТРОИТЕЛЬ
            .comment("Во сколько раз слабее сытит еда у больного. 0.5 — вдвое.")
            .defineInRange("foodMultiplier",
                окр(PlagueConstants.PLAYER_FOOD_MULTIPLIER), 0.0, 1.0);

        УРОН_КАЖДЫЕ = СТРОИТЕЛЬ
            .comment("Раз во сколько тиков стадия 4 бьёт игрока. 3600 — три минуты.")
            .defineInRange("stage4DamageTicks",
                PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS, 20, 72000);

        УРОН_СТАДИИ_4 = СТРОИТЕЛЬ
            .comment("Сколько HP снимает удар стадии 4. 2.0 — одно сердце.")
            .defineInRange("stage4Damage",
                окр(PlagueConstants.PLAYER_STAGE4_DAMAGE), 0.0, 20.0);

        for (int с = 0; с < 5; с++) {
            КАШЕЛЬ_КАЖДЫЕ[с] = СТРОИТЕЛЬ
                .comment("Раз во сколько тиков кашляет игрок стадии " + с + ". Ноль — молчит.")
                .defineInRange("stage" + с + "CoughTicks",
                    PlagueConstants.PLAYER_COUGH_TICKS[с], 0, 72000);
            ШАНС_КАШЛЯ[с] = СТРОИТЕЛЬ
                .comment("Шанс заразить соседа одним кашлем на стадии " + с + ".")
                .defineInRange("stage" + с + "CoughChance",
                    окр(PlagueConstants.PLAYER_COUGH_CHANCE[с]), 0.0, 1.0);
        }

        РАДИУС_КАШЛЯ = СТРОИТЕЛЬ
            .comment("Радиус кашля в блоках. Шесть: больного нельзя вести с собой.")
            .defineInRange("coughRadius",
                окр(PlagueConstants.PLAYER_COUGH_RADIUS), 0.0, 64.0);

        ОЧКОВ_ЗА_КАШЕЛЬ = СТРОИТЕЛЬ
            .comment("Сколько очков получает сосед, которому не повезло.")
            .defineInRange("coughAmount",
                окр(PlagueConstants.PLAYER_COUGH_AMOUNT), 0.0, 100.0);

        for (int г = 0; г < 6; г++) {
            СИЛА_ОТВАРА[г] = СТРОИТЕЛЬ
                .comment(г < 5
                    ? "Сколько очков снимает " + (г + 1) + "-й глоток отвара подряд."
                    : "Сколько снимают шестой и все дальнейшие глотки подряд.")
                .defineInRange("brewStrength" + (г + 1),
                    окр(PlagueConstants.PLAYER_BREW_STRENGTH[г]), 0.0, 100.0);
        }

        СБРОС_ОТВАРА = СТРОИТЕЛЬ
            .comment("Через сколько тиков без глотка счётчик обнуляется. 6000 — пять минут.")
            .defineInRange("brewResetTicks",
                PlagueConstants.PLAYER_BREW_RESET_TICKS, 0, 72000);

        ПОТОЛОК_ОТВАРА = СТРОИТЕЛЬ
            .comment("Выше этой стадии отвар не действует. 2: лихорадку лечит только Клирик.")
            .defineInRange("brewMaxStage", PlagueConstants.PLAYER_BREW_MAX_STAGE, 0, 4);

        ШТРАФ_СМЕРТИ = СТРОИТЕЛЬ
            .comment("Сколько HP навсегда снимает смерть на стадии 2+. 1.0 — полсердца.")
            .defineInRange("deathPenalty",
                окр(PlagueConstants.PLAYER_DEATH_PENALTY), 0.0, 20.0);

        ПОЛ_ПОСТОЯННЫХ = СТРОИТЕЛЬ
            .comment("Ниже этого максимума здоровья смерти не опускают. 6.0 — три сердца.")
            .defineInRange("permanentFloor",
                окр(PlagueConstants.PLAYER_PERMANENT_FLOOR), 2.0, 20.0);

        ЖЁСТКИЙ_ПОЛ = СТРОИТЕЛЬ
            .comment("Итоговый максимум здоровья не опускается ниже этого никогда.",
                "Сторожит сложение постоянных потерь со штрафом стадии.")
            .defineInRange("hardFloor",
                окр(PlagueConstants.PLAYER_HARD_FLOOR), 1.0, 20.0);

        СТРОИТЕЛЬ.pop().comment(
            "Голос больного в Simple Voice Chat. Подбирается только слухом,",
            "поэтому числа тут, а не в коде: правь на живом сервере и слушай.",
            "Быстрее всего — командой /plague voice, она пишет сюда же.",
            "level1 — первая испорченная стадия (minStage), level2 — все выше.",
            "Разбор эффекта — docs/superpowers/notes/2026-09-05-golos-i-kashel.md"
        ).push("voice");

        МИН_СТАДИЯ = СТРОИТЕЛЬ
            .comment("С какой стадии портить голос. Ниже неё речь обычная.")
            .defineInRange("minStage", PlagueConstants.VOICE_MIN_STAGE, 1, 4);

        ЧАСТОТА_ДРОЖИ = СТРОИТЕЛЬ
            .comment("Частота дрожи в герцах. 5–7 — человеческий озноб, выше — робот.")
            .defineInRange("tremorHz", окр(PlagueConstants.VOICE_TREMOR_HZ), 0.5, 15.0);

        for (int у = 0; у < PlagueConstants.VOICE_LEVELS; у++) {
            СТРОИТЕЛЬ.push("level" + (у + 1));

            ПОЛУТОНОВ[у] = СТРОИТЕЛЬ
                .comment("На сколько полутонов опустить голос.",
                    "Главная примета болезни: отёкшие связки звучат ниже.",
                    "Выше 4 начинается демон, а не больной.")
                .defineInRange("semitones", окр(PlagueConstants.VOICE_SEMITONES[у]), 0.0, 8.0);

            ГЛУХОТА[у] = СТРОИТЕЛЬ
                .comment("Сила фильтра низких частот, 0..1: меньше — глуше.",
                    "Ниже 0.2 голос звучит уже не больным, а сломанным микрофоном.")
                .defineInRange("muffle", окр(PlagueConstants.VOICE_MUFFLE[у]), 0.05, 1.0);

            ХРИП[у] = СТРОИТЕЛЬ
                .comment("Жёсткость мягкого ограничения: больше — сильнее хрип.")
                .defineInRange("rasp", окр(PlagueConstants.VOICE_RASP[у]), 1.0, 8.0);

            ДЫХАНИЕ[у] = СТРОИТЕЛЬ
                .comment("Громкость дыхания как доля от громкости голоса.",
                    "Шум идёт по огибающей, поэтому в паузах речи тихо.")
                .defineInRange("breath", окр(PlagueConstants.VOICE_BREATH[у]), 0.0, 1.5);

            ДРОЖЬ[у] = СТРОИТЕЛЬ
                .comment("Глубина дрожи, 0..1. Выше 0.4 звучит как обрыв связи.")
                .defineInRange("tremor", окр(PlagueConstants.VOICE_TREMOR[у]), 0.0, 0.8);

            СТРОИТЕЛЬ.pop();
        }

        СТРОИТЕЛЬ.pop().comment(
            "Сердце чумы: цель всей сессии.",
            "Дизайн — docs/superpowers/specs/2026-09-05-serdce-chumy-design.md"
        ).push("heart");

        ЗДОРОВЬЕ_СЕРДЦА = СТРОИТЕЛЬ
            .comment("Здоровье Сердца. Двадцать кусков модели делят его поровну:",
                "при 200 каждый кусок отваливается за 10 урона.",
                "Правка доходит до уже поставленных Сердец после перезахода в мир.")
            .defineInRange("health", окр(PlagueConstants.HEART_HEALTH), 20.0, 4000.0);

        РАЗМЕР_СЕРДЦА = СТРОИТЕЛЬ
            .comment("Во сколько раз Сердце крупнее исходной модели.",
                "1.0 — как в Блокбенче, 1.5 — заметно больше игрока.",
                "Растёт и картинка, и хитбокс.",
                "Правка доходит до уже поставленных Сердец после перезахода в мир.")
            .defineInRange("scale", окр(PlagueConstants.HEART_SCALE), 0.5, 6.0);

        SPEC = СТРОИТЕЛЬ.pop().build();
    }

    /**
     * Подписка на файл. Событий два: первое чтение и каждая правка
     * на диске. Оба приходят на шину нашего мода, поэтому чужих конфигов
     * тут не бывает и отсеивать некого.
     */
    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, событие -> применить());
        modEventBus.addListener(ModConfigEvent.Reloading.class, событие -> применить());
    }

    /**
     * Записать ручку голоса в файл. Значение сразу уходит и в живые
     * константы: перечитывание файла придёт своим чередом, а слышать
     * правку надо в ту же секунду, пока GM крутит ползунок.
     *
     * @return false, если такой ручки нет
     */
    public static boolean выставитьГолос(String id, double значение) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        if (р == null) return false;
        double v = Math.max(р.минимум(), Math.min(р.максимум(), значение));

        ModConfigSpec.ConfigValue<?> ячейка = ячейкаГолоса(id, р.уровень());
        if (ячейка instanceof ModConfigSpec.IntValue целая) {
            целая.set((int) Math.round(v));
        } else if (ячейка instanceof ModConfigSpec.DoubleValue дробная) {
            дробная.set(Math.round(v * 10000.0) / 10000.0);
        } else {
            return false;
        }
        SPEC.save();
        VoiceKnobs.записать(id, v);
        return true;
    }

    private static ModConfigSpec.ConfigValue<?> ячейкаГолоса(String id, int уровень) {
        if (id.equals("minStage")) return МИН_СТАДИЯ;
        if (id.equals("tremorHz")) return ЧАСТОТА_ДРОЖИ;
        if (уровень < 0 || уровень >= PlagueConstants.VOICE_LEVELS) return null;
        if (id.startsWith("semitones")) return ПОЛУТОНОВ[уровень];
        if (id.startsWith("muffle")) return ГЛУХОТА[уровень];
        if (id.startsWith("rasp")) return ХРИП[уровень];
        if (id.startsWith("breath")) return ДЫХАНИЕ[уровень];
        if (id.startsWith("tremor")) return ДРОЖЬ[уровень];
        return null;
    }

    /**
     * Умолчание в файл — округлённым. Числа у нас float, а конфиг хранит
     * double, и без округления владелец видел бы в файле 0.10000000149.
     */
    private static double окр(float значение) {
        return Math.round(значение * 10000.0) / 10000.0;
    }

    /** Переписать игровые числа значениями из файла. */
    private static void применить() {
        PlagueConstants.HEART_HEALTH = ЗДОРОВЬЕ_СЕРДЦА.get().floatValue();
        PlagueConstants.HEART_SCALE = РАЗМЕР_СЕРДЦА.get().floatValue();

        PlagueConstants.START_EPICENTERS = ОЧАГИ.get();
        PlagueConstants.START_INFECTION_PERCENT = СТАРТОВАЯ_ДОЛЯ.get().floatValue();
        PlagueConstants.SCAR_NIGHTS = НОЧЕЙ_ШРАМА.get();
        PlagueConstants.SLEEP_BUDGET_MULTIPLIER = МНОЖИТЕЛЬ_СНА.get().floatValue();
        PlagueConstants.SLEEP_EXTRA_GROWTH = ПРИБАВКА_СНА.get();

        PlagueConstants.BLOCKS_PER_TICK = БЛОКОВ_ЗА_ТИК.get();
        PlagueConstants.SURFACE_DEPTH = ГЛУБИНА.get();
        PlagueConstants.GROWTH_PATCH_FRACTION = ДОЛЯ_НАРОСТА.get().floatValue();
        PlagueConstants.SURFACE_SPORE_SAC = МЕШКИ_НАВЕРХУ.get().floatValue();

        PlagueConstants.BLOCKS_PER_TICK_CAVE = БЛОКОВ_ЗА_ТИК_ПОД_ЗЕМЛЁЙ.get();
        PlagueConstants.CAVE_COLUMNS_PER_TICK = СТОЛБЦОВ_ЗА_ТИК.get();
        PlagueConstants.CAVE_WALL_SPARSE = СТЕНЫ_РЕДКО.get().floatValue();
        // Густо не может быть реже, чем редко: иначе покрытие перестанет
        // быть вложенным и стены пещер начнут выздоравливать при росте уровня.
        PlagueConstants.CAVE_WALL_DENSE =
            Math.max(PlagueConstants.CAVE_WALL_SPARSE, СТЕНЫ_ГУСТО.get().floatValue());
        PlagueConstants.CAVE_CEILING_VINES = ЛОЗЫ.get().floatValue();
        PlagueConstants.CAVE_FLOOR_ROT = ПОЛ.get().floatValue();
        PlagueConstants.CAVE_SPORE_SAC = МЕШКИ.get().floatValue();

        PlagueConstants.SPAWN_CHANCE_PER_NIGHT = ШАНС_ВЫВОДКА.get().floatValue();
        PlagueConstants.SPAWN_ZOMBIES = ЗОМБИ_В_КУЧКЕ.get();
        PlagueConstants.SPAWN_SKELETONS = СКЕЛЕТОВ_В_КУЧКЕ.get();
        PlagueConstants.SPAWN_MAX_GROUPS_PER_NIGHT = КУЧЕК_ЗА_НОЧЬ.get();
        PlagueConstants.SPAWN_RADIUS = РАДИУС_ВЫВОДКА.get();
        PlagueConstants.SPAWN_MIN_PLAYER_DISTANCE = НЕ_БЛИЖЕ_К_ИГРОКУ.get();

        PlagueConstants.ANIMAL_CHECK_TICKS = ПРОВЕРКА_ЖИВОТНЫХ.get();
        PlagueConstants.ANIMAL_INFECT_CHANCE = ШАНС_ЗАРАЖЕНИЯ.get().floatValue();

        boolean поправлено = false;

        PlagueConstants.PLAYER_TICK_INTERVAL = ТИК_ИГРОКА.get();
        PlagueConstants.PLAYER_UNDERGROUND_MULTIPLIER = ПОД_ЗЕМЛЁЙ.get().floatValue();
        PlagueConstants.PLAYER_FOOD_MULTIPLIER = ЕДА.get().floatValue();
        PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS = УРОН_КАЖДЫЕ.get();
        PlagueConstants.PLAYER_STAGE4_DAMAGE = УРОН_СТАДИИ_4.get().floatValue();
        PlagueConstants.PLAYER_COUGH_RADIUS = РАДИУС_КАШЛЯ.get().floatValue();
        PlagueConstants.PLAYER_COUGH_AMOUNT = ОЧКОВ_ЗА_КАШЕЛЬ.get().floatValue();
        PlagueConstants.PLAYER_BREW_RESET_TICKS = СБРОС_ОТВАРА.get();
        PlagueConstants.PLAYER_BREW_MAX_STAGE = ПОТОЛОК_ОТВАРА.get();
        PlagueConstants.PLAYER_DEATH_PENALTY = ШТРАФ_СМЕРТИ.get().floatValue();
        PlagueConstants.PLAYER_PERMANENT_FLOOR = ПОЛ_ПОСТОЯННЫХ.get().floatValue();
        PlagueConstants.PLAYER_HARD_FLOOR = ЖЁСТКИЙ_ПОЛ.get().floatValue();

        // Пороги стадий обязаны идти по возрастанию, иначе стадия схлопнется
        // и следующая никогда не наступит. Выправляем молча, как с фазами.
        int[] пороги = new int[4];
        int минимумПорога = 1;
        for (int с = 0; с < 4; с++) {
            int изФайла = ПОРОГ_СТАДИИ[с].get();
            пороги[с] = Math.max(минимумПорога, изФайла);
            if (пороги[с] != изФайла) поправлено = true;
            минимумПорога = пороги[с] + 1;
        }
        PlagueConstants.PLAYER_STAGE_THRESHOLDS = пороги;

        float[] экспозиция = new float[5];
        float[] здоровьеСтадии = new float[5];
        int[] кашельКаждые = new int[5];
        float[] шансКашля = new float[5];
        for (int с = 0; с < 5; с++) {
            экспозиция[с] = ЭКСПОЗИЦИЯ[с].get().floatValue();
            здоровьеСтадии[с] = ЗДОРОВЬЕ_СТАДИИ[с].get().floatValue();
            кашельКаждые[с] = КАШЕЛЬ_КАЖДЫЕ[с].get();
            шансКашля[с] = ШАНС_КАШЛЯ[с].get().floatValue();
        }
        PlagueConstants.VOICE_MIN_STAGE = МИН_СТАДИЯ.get();
        PlagueConstants.VOICE_TREMOR_HZ = ЧАСТОТА_ДРОЖИ.get().floatValue();

        int уровней = PlagueConstants.VOICE_LEVELS;
        float[] полутонов = new float[уровней];
        float[] глухота = new float[уровней];
        float[] хрип = new float[уровней];
        float[] дыхание = new float[уровней];
        float[] дрожь = new float[уровней];
        for (int у = 0; у < уровней; у++) {
            полутонов[у] = ПОЛУТОНОВ[у].get().floatValue();
            глухота[у] = ГЛУХОТА[у].get().floatValue();
            хрип[у] = ХРИП[у].get().floatValue();
            дыхание[у] = ДЫХАНИЕ[у].get().floatValue();
            дрожь[у] = ДРОЖЬ[у].get().floatValue();
        }
        PlagueConstants.VOICE_SEMITONES = полутонов;
        PlagueConstants.VOICE_MUFFLE = глухота;
        PlagueConstants.VOICE_RASP = хрип;
        PlagueConstants.VOICE_BREATH = дыхание;
        PlagueConstants.VOICE_TREMOR = дрожь;

        PlagueConstants.PLAYER_EXPOSURE = экспозиция;
        PlagueConstants.PLAYER_STAGE_HEALTH = здоровьеСтадии;
        PlagueConstants.PLAYER_COUGH_TICKS = кашельКаждые;
        PlagueConstants.PLAYER_COUGH_CHANCE = шансКашля;

        float[] силаОтвара = new float[6];
        for (int г = 0; г < 6; г++) силаОтвара[г] = СИЛА_ОТВАРА[г].get().floatValue();
        PlagueConstants.PLAYER_BREW_STRENGTH = силаОтвара;

        float[] доли = new float[MaterializationMask.НАСТРАИВАЕМЫХ_УРОВНЕЙ];
        for (int i = 0; i < доли.length; i++) доли[i] = ДОЛЯ_УРОВНЯ[i].get().floatValue();
        поправлено |= MaterializationMask.задатьДоли(доли);

        for (int ф = 0; ф < PhaseTable.PHASE_COUNT; ф++) {
            int конец = КОНЕЦ_ФАЗЫ[ф] == null ? Integer.MAX_VALUE : КОНЕЦ_ФАЗЫ[ф].get();
            поправлено |= PhaseTable.задатьФазу(ф, конец, new PhaseParams(
                БАЗА[ф].get().floatValue(),
                БЮДЖЕТ[ф].get(),
                РОСТ_КАЖДЫЕ[ф].get(),
                РОСТ_НА[ф].get()));
        }

        if (поправлено) {
            PlagueCore.LOG.warn("Конфиг чумы: доли или ночи фаз шли не по возрастанию, "
                + "значения выправлены. Проверь config/plaguecore-common.toml");
        }
        PlagueCore.LOG.info("Конфиг чумы прочитан: очагов {}, блоков за тик {}, глубина {}",
            PlagueConstants.START_EPICENTERS, PlagueConstants.BLOCKS_PER_TICK,
            PlagueConstants.SURFACE_DEPTH);
    }
}
