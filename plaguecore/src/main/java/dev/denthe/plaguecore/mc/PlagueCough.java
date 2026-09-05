package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Кашель: главная примета болезни и единственный способ поймать её
 * от человека. Спек подсистемы 2, раздел 4.
 *
 * Кашель — настоящее событие со звуком и частицами, а не строчка
 * в интерфейсе. Так стадию видно и слышно без всякого HUD: услышал
 * рядом — отошёл.
 *
 * Радиус шесть блоков, а не два. При двух достаточно отойти на три шага,
 * и болезнь становится личной проблемой каждого. При шести больного
 * нельзя просто взять с собой — его либо лечат, либо оставляют.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueCough {
    private PlagueCough() {}

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer больной)) return;
        if (!(больной.level() instanceof ServerLevel мир)) return;
        if (больной.isCreative() || больной.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(больной);
        int стадия = Math.max(0, Math.min(д.стадия, PlagueConstants.PLAYER_COUGH_TICKS.length - 1));
        int период = PlagueConstants.PLAYER_COUGH_TICKS[стадия];
        if (период <= 0) return;
        if (больной.tickCount % период != 0) return;

        кашлянуть(мир, больной);
        заразитьРядом(мир, больной, PlagueConstants.PLAYER_COUGH_CHANCE[стадия]);
    }

    /**
     * Звук и частицы.
     *
     * Сэмплов семь, вариант выбирает сам Minecraft. Тон гуляет на
     * ±6 % от броска к броску: два кашля подряд не должны звучать
     * как один файл, проигранный дважды.
     */
    private static void кашлянуть(ServerLevel мир, ServerPlayer больной) {
        float тон = 0.94f + мир.random.nextFloat() * 0.12f;
        мир.playSound(null, больной.getX(), больной.getY(), больной.getZ(),
            PlagueSounds.PLAYER_COUGH.get(), SoundSource.PLAYERS, 1.0f, тон);
        мир.sendParticles(ParticleTypes.SNEEZE,
            больной.getX(), больной.getEyeY() - 0.1, больной.getZ(),
            12, 0.25, 0.15, 0.25, 0.02);
    }

    /** Каждый в радиусе бросает кубик. Сам больной, понятно, не считается. */
    private static void заразитьРядом(ServerLevel мир, ServerPlayer больной, float шанс) {
        if (шанс <= 0f) return;

        double r = PlagueConstants.PLAYER_COUGH_RADIUS;
        AABB область = больной.getBoundingBox().inflate(r);
        List<Player> рядом = мир.getEntitiesOfClass(Player.class, область,
            п -> п != больной && !п.isCreative() && !п.isSpectator()
                 && п.distanceToSqr(больной) <= r * r);

        for (Player сосед : рядом) {
            if (!(сосед instanceof ServerPlayer жертва)) continue;
            PlayerPlagueData дж = PlayerPlagueData.данные(жертва);
            if (мир.getGameTime() < дж.иммунитетДо) continue;
            if (мир.random.nextFloat() >= шанс) continue;

            int была = дж.стадия;
            дж.заражённость = Math.min(100f,
                дж.заражённость + PlagueConstants.PLAYER_COUGH_AMOUNT);
            дж.стадия = InfectionMath.стадия(дж.заражённость);
            if (дж.стадия != была) PlayerInfection.пересчитатьЗдоровье(жертва);
        }
    }
}
