package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.DreadMath;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Режиссёр напряжения. Спек `2026-09-17-horror-rezhissyor-design.md`.
 *
 * Раз в секунду переводит мир вокруг игрока в `DreadMath.Факторы`, копит
 * напряжение и, когда оно переваливает порог, выдаёт событие. Вся
 * арифметика ритма живёт в `core`; здесь только чтение мира и исполнение.
 *
 * Зачем вообще машина, если звуки можно включать руками: ручной звук
 * пугает один раз, а страшна не громкость, а пауза перед следующим
 * разом. Держать такую паузу на восьмерых игроков одновременно человек
 * за пультом не может, а счётчик может.
 *
 * Счёт живёт в памяти и не сохраняется: переживать перезапуск ему
 * незачем, а тишина в первые минуты после рестарта даже к месту.
 * Единственное, что обязано пережить перезапуск, — счётчик явлений;
 * он приедет в `PlagueState` вместе с Наблюдателем.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class DreadDirector {
    private DreadDirector() {}

    /**
     * Счёт одного игрока.
     *
     * @param напряжение   сколько накоплено, 0..100
     * @param последнее    тик последнего события
     * @param долина       сколько тиков после него держится тишина
     * @param шороховЗаЧас потрачено шорохов в текущем часе
     * @param виденийЗаЧас потрачено видений в текущем часе
     * @param началоЧаса   тик, с которого считается этот час
     */
    public record Счёт(float напряжение, long последнее, int долина,
                       int шороховЗаЧас, int виденийЗаЧас, long началоЧаса) {}

    private static final Map<UUID, Счёт> СЧЁТ = new HashMap<>();

    /** Тиков в часе реального времени. */
    private static final long ЧАС = 72000L;

    private static final Счёт ПУСТО = new Счёт(0f, 0L, 0, 0, 0, 0L);

    /** Текущий счёт игрока. Панель мастера показывает именно его. */
    public static Счёт состояние(ServerPlayer игрок) {
        return СЧЁТ.getOrDefault(игрок.getUUID(), ПУСТО);
    }

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!PlagueConstants.DREAD_ENABLED) return;
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;
        if (игрок.level().dimension() != Level.OVERWORLD) return;

        ServerLevel уровень = игрок.serverLevel();
        long сейчас = уровень.getGameTime();
        Счёт счёт = сбросЧаса(состояние(игрок), сейчас);

        DreadMath.Факторы ф = факторы(игрок, уровень);
        // Тик режиссёра идёт раз в секунду, поэтому прирост за секунду
        // прибавляется как есть, без пересчёта на тики.
        float напряжение = DreadMath.копить(счёт.напряжение(),
            DreadMath.прирост(ф, PlagueConstants.весаСтраха()));

        if (DreadMath.вДолине(сейчас, счёт.последнее(), счёт.долина())) {
            запомнить(игрок, счёт, напряжение);
            return;
        }

        DreadMath.Уровень уровеньСобытия = поБюджету(DreadMath.уровень(напряжение,
            PlagueConstants.DREAD_RUSTLE_AT, PlagueConstants.DREAD_VISION_AT,
            PlagueConstants.DREAD_WATCHER_AT,
            явлениеДоступно(уровень, игрок, ф)), счёт);

        if (уровеньСобытия == DreadMath.Уровень.НЕТ) {
            запомнить(игрок, счёт, напряжение);
            return;
        }

        выдать(игрок, уровеньСобытия);
    }

    /**
     * Выдать событие и завести долину. Этим же пользуется панель мастера:
     * ручное событие обязано вести себя как обычное, иначе мастер
     * и режиссёр начнут бить в одну точку дважды подряд.
     */
    public static void выдать(ServerPlayer игрок, DreadMath.Уровень уровень) {
        Счёт счёт = состояние(игрок);
        long сейчас = игрок.serverLevel().getGameTime();
        int долина;
        int шорохов = счёт.шороховЗаЧас();
        int видений = счёт.виденийЗаЧас();

        switch (уровень) {
            case ШОРОХ -> {
                DreadCatalog.шорох(игрок, игрок.serverLevel().getRandom());
                долина = PlagueConstants.DREAD_VALLEY_RUSTLE;
                шорохов++;
            }
            case ВИДЕНИЕ -> {
                видение(игрок);
                долина = PlagueConstants.DREAD_VALLEY_VISION;
                видений++;
            }
            case ЯВЛЕНИЕ -> {
                // Если поставить его некуда, событие не пропадает:
                // человек дошёл до сотни и обязан хоть что-то получить.
                if (!Watcher.явить(игрок)) видение(игрок);
                долина = PlagueConstants.DREAD_VALLEY_WATCHER;
            }
            default -> {
                return;
            }
        }

        СЧЁТ.put(игрок.getUUID(),
            new Счёт(0f, сейчас, долина, шорохов, видений, счёт.началоЧаса()));
    }

    /** Заткнуть режиссёра на время — ручка мастера перед ролевой сценой. */
    public static void тишина(ServerPlayer игрок, int тиков) {
        Счёт счёт = состояние(игрок);
        СЧЁТ.put(игрок.getUUID(), new Счёт(счёт.напряжение(),
            игрок.serverLevel().getGameTime(), тиков,
            счёт.шороховЗаЧас(), счёт.виденийЗаЧас(), счёт.началоЧаса()));
    }

    /** Подтолкнуть напряжение, не выдавая события. Тоже ручка мастера. */
    public static void подтолкнуть(ServerPlayer игрок, float сколько) {
        Счёт счёт = состояние(игрок);
        запомнить(игрок, счёт, DreadMath.копить(счёт.напряжение(), сколько));
    }

    private static DreadMath.Факторы факторы(ServerPlayer игрок, ServerLevel уровень) {
        int чанкX = SectionPos.blockToSectionCoord(игрок.getBlockX());
        int чанкZ = SectionPos.blockToSectionCoord(игрок.getBlockZ());
        int уровеньЧанка = Math.max(0, PlagueApi.getChunkLevel(уровень, чанкX, чанкZ));
        int соседей = соседей(игрок, уровень);

        return new DreadMath.Факторы(
            уровень.getMaxLocalRawBrightness(игрок.blockPosition()),
            уровеньЧанка,
            // Пограничье — кольцо вокруг очага: уровни 1 и 2 сетки.
            уровеньЧанка == 1 || уровеньЧанка == 2,
            игрок.getBlockY(),
            !уровень.isDay(),
            PlagueApi.getStage(игрок),
            соседей == 0,
            PlagueState.get(уровень).phase(),
            соседей);
    }

    private static int соседей(ServerPlayer игрок, ServerLevel уровень) {
        double радиус = PlagueConstants.DREAD_ALONE_RADIUS;
        int счёт = 0;
        for (Player другой : уровень.players()) {
            if (другой == игрок || другой.isSpectator()) continue;
            if (другой.distanceToSqr(игрок) <= радиус * радиус) счёт++;
        }
        return счёт;
    }

    /**
     * Одно случайное видение: темнота, сердцебиение или силуэт.
     *
     * Силуэт ставится сбоку-сзади, а не прямо за спиной: строго позади
     * его не увидят вовсе, а в лицо — это уже не «показалось».
     */
    private static void видение(ServerPlayer игрок) {
        RandomSource случай = игрок.serverLevel().getRandom();
        int вид = случай.nextInt(DreadKinds.ВСЕГО);
        int тиков = вид == DreadKinds.СИЛУЭТ ? 25 : 50;

        float[] точка = DreadMath.заСпиной(
            (float) игрок.getX(), (float) игрок.getY(), (float) игрок.getZ(),
            игрок.getYRot() + (случай.nextBoolean() ? 115f : -115f),
            8f + случай.nextFloat() * 6f);

        PacketDistributor.sendToPlayer(игрок,
            new PlagueNetwork.Vision(вид, тиков, точка[0], точка[1], точка[2]));
    }

    /**
     * Можно ли сейчас явление. Кроме лимита сессии, нужны условия
     * самой сцены: человек один и либо ночь, либо он под землёй.
     * Днём в поле Наблюдатель — это просто моб, стоящий в траве.
     */
    private static boolean явлениеДоступно(ServerLevel уровень, ServerPlayer игрок,
                                           DreadMath.Факторы ф) {
        if (PlagueState.get(уровень).watchers()
            >= PlagueConstants.DREAD_WATCHERS_PER_SESSION) return false;
        if (!ф.один()) return false;
        return ф.ночь() || ф.y() < PlagueConstants.DREAD_DEPTH_Y;
    }

    /**
     * Ужать событие до того, что разрешает часовой бюджет. Видение без
     * бюджета становится шорохом, шорох без бюджета — тишиной: лучше
     * промолчать, чем выбить у человека весь запас за один вечер.
     */
    private static DreadMath.Уровень поБюджету(DreadMath.Уровень уровень, Счёт счёт) {
        if (уровень == DreadMath.Уровень.ВИДЕНИЕ && !DreadMath.бюджетЕсть(
                счёт.виденийЗаЧас(), PlagueConstants.DREAD_VISIONS_PER_HOUR)) {
            уровень = DreadMath.Уровень.ШОРОХ;
        }
        if (уровень == DreadMath.Уровень.ШОРОХ && !DreadMath.бюджетЕсть(
                счёт.шороховЗаЧас(), PlagueConstants.DREAD_RUSTLES_PER_HOUR)) {
            return DreadMath.Уровень.НЕТ;
        }
        return уровень;
    }

    private static Счёт сбросЧаса(Счёт счёт, long сейчас) {
        if (сейчас - счёт.началоЧаса() < ЧАС) return счёт;
        return new Счёт(счёт.напряжение(), счёт.последнее(), счёт.долина(), 0, 0, сейчас);
    }

    private static void запомнить(ServerPlayer игрок, Счёт счёт, float напряжение) {
        СЧЁТ.put(игрок.getUUID(), new Счёт(напряжение, счёт.последнее(), счёт.долина(),
            счёт.шороховЗаЧас(), счёт.виденийЗаЧас(), счёт.началоЧаса()));
    }
}
