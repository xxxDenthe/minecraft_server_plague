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
    private static final ModConfigSpec.DoubleValue[] ДОЛЯ_УРОВНЯ =
        new ModConfigSpec.DoubleValue[MaterializationMask.НАСТРАИВАЕМЫХ_УРОВНЕЙ];

    // ── подземелье ────────────────────────────────────────────────────
    private static final ModConfigSpec.IntValue БЛОКОВ_ЗА_ТИК_ПОД_ЗЕМЛЁЙ;
    private static final ModConfigSpec.IntValue СТОЛБЦОВ_ЗА_ТИК;
    private static final ModConfigSpec.DoubleValue СТЕНЫ_РЕДКО;
    private static final ModConfigSpec.DoubleValue СТЕНЫ_ГУСТО;
    private static final ModConfigSpec.DoubleValue ЛОЗЫ;
    private static final ModConfigSpec.DoubleValue ПОЛ;
    private static final ModConfigSpec.DoubleValue МЕШКИ;

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
     * Умолчание в файл — округлённым. Числа у нас float, а конфиг хранит
     * double, и без округления владелец видел бы в файле 0.10000000149.
     */
    private static double окр(float значение) {
        return Math.round(значение * 10000.0) / 10000.0;
    }

    /** Переписать игровые числа значениями из файла. */
    private static void применить() {
        PlagueConstants.START_EPICENTERS = ОЧАГИ.get();
        PlagueConstants.START_INFECTION_PERCENT = СТАРТОВАЯ_ДОЛЯ.get().floatValue();
        PlagueConstants.SCAR_NIGHTS = НОЧЕЙ_ШРАМА.get();
        PlagueConstants.SLEEP_BUDGET_MULTIPLIER = МНОЖИТЕЛЬ_СНА.get().floatValue();
        PlagueConstants.SLEEP_EXTRA_GROWTH = ПРИБАВКА_СНА.get();

        PlagueConstants.BLOCKS_PER_TICK = БЛОКОВ_ЗА_ТИК.get();
        PlagueConstants.SURFACE_DEPTH = ГЛУБИНА.get();
        PlagueConstants.GROWTH_PATCH_FRACTION = ДОЛЯ_НАРОСТА.get().floatValue();

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

        float[] доли = new float[MaterializationMask.НАСТРАИВАЕМЫХ_УРОВНЕЙ];
        for (int i = 0; i < доли.length; i++) доли[i] = ДОЛЯ_УРОВНЯ[i].get().floatValue();
        boolean поправлено = MaterializationMask.задатьДоли(доли);

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
