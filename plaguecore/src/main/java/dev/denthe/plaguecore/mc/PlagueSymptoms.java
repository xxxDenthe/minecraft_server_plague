package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Симптомы: чем болезнь мешает жить прямо сейчас. Заметка
 * `2026-09-06-simptomy-dnyom.md`.
 *
 * До этого класса стадии отнимали только максимум здоровья, а это
 * цифра, а не ощущение: в гнилом поле днём можно было спокойно копать
 * и строить, пока счётчик тихо полз вверх. Здесь болезнь начинает
 * мешать делу — сбивает бег, тупит инструмент, сушит горло.
 *
 * Всё, что тут раздаётся, — ванильные эффекты короткой длительности,
 * которые перевыдаются раз в секунду. Так они гаснут сами через пару
 * секунд после лечения, и снимать их отдельно не нужно.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueSymptoms {
    private PlagueSymptoms() {}

    /** Длительность фонового эффекта. Чуть больше периода перевыдачи. */
    private static final int ДЛИТЕЛЬНОСТЬ_ФОНА = 40;

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;
        if (игрок.level().dimension() != Level.OVERWORLD) return;

        int стадия = стадия(игрок);
        if (стадия <= 0) return;

        эффект(игрок, MobEffects.DIG_SLOWDOWN, PlagueConstants.STAGE_FATIGUE[стадия]);
        эффект(игрок, MobEffects.WEAKNESS, PlagueConstants.STAGE_WEAKNESS[стадия]);

        float голод = PlagueConstants.STAGE_EXHAUSTION[стадия];
        if (голод > 0f) игрок.causeFoodExhaustion(голод);
        ThirstBridge.потратить(игрок, PlagueConstants.STAGE_THIRST[стадия]);
    }

    /**
     * Приступ кашля: то, что делает кашель событием, а не звуком.
     * Зовётся из {@link PlagueCough} сразу после самого кашля.
     *
     * Слабость в ногах — на всех стадиях: приступ сбивает бег и не даёт
     * убежать от того, что тебя догнало. Тошнота и слабость в руках
     * приходят позже, по стадиям из конфига.
     */
    static void приступ(ServerPlayer игрок, int стадия) {
        int секунды = Math.round(PlagueConstants.COUGH_STUN_SECONDS * 20f);
        if (секунды <= 0) return;

        добавить(игрок, MobEffects.MOVEMENT_SLOWDOWN, секунды, 0);
        if (стадия >= PlagueConstants.COUGH_NAUSEA_STAGE) {
            добавить(игрок, MobEffects.CONFUSION, секунды + 20, 0);
        }
        if (стадия >= PlagueConstants.COUGH_WEAKNESS_STAGE) {
            добавить(игрок, MobEffects.WEAKNESS, секунды * 2, 0);
        }

        игрок.causeFoodExhaustion(PlagueConstants.COUGH_EXHAUSTION);
        ThirstBridge.потратить(игрок, PlagueConstants.COUGH_THIRST);
    }

    /**
     * Вдох спор: рывок заражения под открытым небом.
     *
     * Ровная струйка очков в секунду не читается вообще никак — игрок
     * видит только, что число выросло. Рывок со звуком и приступом
     * говорит телом: здесь нечем дышать. Повязка снимает вдохи целиком,
     * в этом и весь смысл подготовки.
     */
    static void вдох(ServerLevel мир, ServerPlayer игрок) {
        мир.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            игрок.getX(), игрок.getEyeY(), игрок.getZ(), 12, 0.4, 0.3, 0.4, 0.01);
        PlagueCough.кашлянуть(мир, игрок);
        приступ(игрок, стадия(игрок));
    }

    private static int стадия(ServerPlayer игрок) {
        int с = PlayerPlagueData.данные(игрок).стадия;
        return Math.max(0, Math.min(с, PlagueConstants.STAGE_FATIGUE.length - 1));
    }

    /** Фоновый эффект уровня N. Ноль — эффекта нет вовсе. */
    private static void эффект(ServerPlayer игрок, Holder<MobEffect> какой, int уровень) {
        if (уровень <= 0) return;
        добавить(игрок, какой, ДЛИТЕЛЬНОСТЬ_ФОНА, уровень - 1);
    }

    /**
     * Эффект без частиц, но со значком: облако пузырьков вокруг больного
     * заслонило бы полэкрана, а значок в углу — ровно та подсказка,
     * которая нужна, чтобы понять, почему кирка вдруг стала тупой.
     */
    private static void добавить(ServerPlayer игрок, Holder<MobEffect> какой,
                                 int тиков, int сила) {
        игрок.addEffect(new MobEffectInstance(какой, тиков, сила, true, false, true));
    }
}
