package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PhaseParams;
import dev.denthe.plaguecore.core.PhaseTable;
import dev.denthe.plaguecore.core.SpreadEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Определяет наступление ночи и запускает ночной тик. Спек, разделы 6.1 и 6.3.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class NightHook {
    private NightHook() {}

    /** Время суток, с которого считаем, что наступила ночь. */
    private static final long ЗАКАТ = 13000L;

    private static final long СУТКИ = 24000L;

    /** Проверяем время не каждый тик — раз в секунду более чем достаточно. */
    private static final int ИНТЕРВАЛ_ПРОВЕРКИ = 20;

    private static int счётчик = 0;

    @SubscribeEvent
    public static void приСтартеСервера(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        PlagueState state = PlagueState.get(overworld);
        if (!state.isTerrainInitialized()) {
            long t0 = System.currentTimeMillis();
            int ячеек = TerrainInitializer.initialize(overworld, state);
            PlagueCore.LOG.info("Сетка местности заполнена: {} ячеек за {} мс",
                ячеек, System.currentTimeMillis() - t0);
        }
    }

    @SubscribeEvent
    public static void приТикеСервера(ServerTickEvent.Post event) {
        if (++счётчик < ИНТЕРВАЛ_ПРОВЕРКИ) return;
        счётчик = 0;

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        PlagueState state = PlagueState.get(overworld);
        if (state.isPaused()) return;

        long время = overworld.getDayTime();
        long сутки = время / СУТКИ;
        long вСутках = время % СУТКИ;

        if (вСутках >= ЗАКАТ && state.lastProcessedDay() != сутки) {
            state.setLastProcessedDay(сутки);
            state.advanceNight();
            SpreadEngine.NightResult r = runNight(overworld, state, false);
            int вОчередь = Materializer.поставитьЗагруженные(overworld, state);
            PlagueCore.LOG.info("Ночь {} (фаза {}): заражено {}, выросло {}, зажило шрамов {}, в очередь на перерисовку {}",
                state.night(), r.phase(), r.newlyInfected(), r.grown(), r.scarsHealed(), вОчередь);
        }
    }

    @SubscribeEvent
    public static void приПробуждении(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        PlagueState state = PlagueState.get(level);
        if (state.isPaused()) return;

        SpreadEngine.NightResult r = догнатьЗаСон(level, state);
        Materializer.поставитьЗагруженные(level, state);
        PlagueCore.LOG.info("Игроки спали — чума ускорилась: дополнительно заражено {}",
            r.newlyInfected());
    }

    /** Обычный ночной тик. */
    public static SpreadEngine.NightResult runNight(ServerLevel level, PlagueState state, boolean slept) {
        RandomGenerator rng = генератор(level, state.night());
        return SpreadEngine.runNight(state.grid(), state.night(), slept, rng);
    }

    /**
     * Доначисление за сон. Ночь уже была обработана на закате с обычным
     * бюджетом, поэтому добавляем разницу: ещё один бюджет и ещё один
     * прирост. В сумме выходит ровно двойная ночь из спека 6.3.
     */
    private static SpreadEngine.NightResult догнатьЗаСон(ServerLevel level, PlagueState state) {
        int phase = PhaseTable.phaseForNight(state.night());
        PhaseParams params = PhaseTable.paramsFor(phase);
        float добавка = PlagueConstants.SLEEP_BUDGET_MULTIPLIER - 1.0f;
        RandomGenerator rng = генератор(level, state.night() * 31L + 7L);
        return SpreadEngine.runNightWith(
            state.grid(), state.night(), params,
            добавка, PlagueConstants.SLEEP_EXTRA_GROWTH, rng);
    }

    /** Детерминированный генератор: одна и та же ночь в одном мире даёт один результат. */
    private static RandomGenerator генератор(ServerLevel level, long salt) {
        long seed = level.getSeed() ^ (salt * 0x9E3779B97F4A7C15L);
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(seed);
    }
}
