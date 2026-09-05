package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Видимые приметы личного заражения — заглушка до подсистемы 2. Дизайна
 * стадий ещё нет, поэтому источник стадии сейчас один: вложение
 * {@link PlagueAttachments#СТАДИЯ}, которое правится только командой
 * `/plague setstage` для проверки. Когда подсистема 2 появится и станет
 * сама писать в то же вложение, этот файл менять не придётся.
 *
 * Стадия 3 — редкие споры. Стадия 4 — споры гуще и кашель: короткий
 * плевок дымки изо рта раз в {@link #ИНТЕРВАЛ_КАШЛЯ} тиков.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlayerInfectionEffects {
    private PlayerInfectionEffects() {}

    private static final int СТАДИЯ_РЕДКИХ_СПОР = 3;
    private static final int СТАДИЯ_ГУСТЫХ_СПОР = 4;

    /** Раз во сколько тиков проверяется редкая аура спор (стадия 3). */
    private static final int ИНТЕРВАЛ_СПОР_РЕДКО = 40;
    /** То же для густой ауры (стадия 4) — вчетверо чаще. */
    private static final int ИНТЕРВАЛ_СПОР_ГУСТО = 10;
    /** Раз во сколько тиков — кашель на стадии 4. 200 тиков — десять секунд. */
    private static final int ИНТЕРВАЛ_КАШЛЯ = 200;

    @SubscribeEvent
    public static void приТике(EntityTickEvent.Post событие) {
        Entity сущность = событие.getEntity();
        if (!(сущность instanceof ServerPlayer игрок)) return;

        int стадия = игрок.getData(PlagueAttachments.СТАДИЯ);
        if (стадия < СТАДИЯ_РЕДКИХ_СПОР) return;
        ServerLevel мир = (ServerLevel) игрок.level();

        int интервалСпор = стадия >= СТАДИЯ_ГУСТЫХ_СПОР ? ИНТЕРВАЛ_СПОР_ГУСТО : ИНТЕРВАЛ_СПОР_РЕДКО;
        if (игрок.tickCount % интервалСпор == 0) {
            мир.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                игрок.getX(), игрок.getY() + игрок.getBbHeight() * 0.5, игрок.getZ(),
                1, 0.3, 0.4, 0.3, 0.0);
        }

        if (стадия >= СТАДИЯ_ГУСТЫХ_СПОР && игрок.tickCount % ИНТЕРВАЛ_КАШЛЯ == 0) {
            кашлянуть(игрок, мир);
        }
    }

    private static void кашлянуть(ServerPlayer игрок, ServerLevel мир) {
        Vec3 рот = игрок.getEyePosition().subtract(0, 0.15, 0)
            .add(игрок.getLookAngle().scale(0.3));
        мир.sendParticles(ParticleTypes.SNEEZE,
            рот.x, рот.y, рот.z, 6, 0.05, 0.05, 0.05, 0.02);
    }
}
