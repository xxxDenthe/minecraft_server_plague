package dev.denthe.plaguecore.mc.border;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.BorderMath;
import dev.denthe.plaguecore.mc.PlagueApi;
import dev.denthe.plaguecore.mc.PlagueSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Приметы Пограничья: зона должна звучать, а не только выглядеть.
 *
 * Раз в {@code BORDER_OMEN_TICKS} игрок в чанке уровня 1 и выше бросает
 * кубик; выпало — где-то рядом, вне поля зрения, что-то происходит.
 * Чем ближе к Гнили, тем чаще.
 *
 * <p><b>Примета — обман, а не предупреждение.</b> Она не значит, что
 * рядом кто-то есть, и с мобами не связана вовсе. Смысл ровно один:
 * заставить игрока обернуться. Поэтому и звуки взяты бытовые — скрип,
 * осыпь, чужой кашель, — а не рык: рык обещал бы бой, которого нет.
 *
 * <p><b>Звук уходит одному игроку, а не в мир.</b> Кубик бросает каждый
 * сам за себя, и восьмером в одной точке зона трещала бы без остановки.
 * Личный пакет к тому же честнее: примета — то, что послышалось тебе.
 *
 * <p>Своих звуковых файлов не добавлено: кашель уже есть у подсистемы 2,
 * остальные три — ванильные.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class BorderOmens {
    private BorderOmens() {}

    /** Разброс высоты вокруг игрока, в блоках: звук не должен быть ровно в ухе. */
    private static final int РАЗБРОС_ВЫСОТЫ = 3;

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (!(игрок.level() instanceof ServerLevel мир)) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;

        int период = PlagueConstants.BORDER_OMEN_TICKS;
        if (период <= 0 || игрок.tickCount % период != 0) return;

        int уровень = PlagueApi.getChunkLevelAt(мир, игрок.blockPosition());
        float шанс = BorderMath.поУровню(PlagueConstants.BORDER_OMEN_CHANCE, уровень);
        if (шанс <= 0f || мир.random.nextFloat() >= шанс) return;

        примета(мир, игрок, уровень);
    }

    /**
     * Проиграть примету немедленно. Отдельно от броска кубика ради
     * команды {@code /plague omen}: ждать пятнадцати процентов на живом
     * сервере — плохой способ проверки.
     */
    public static void примета(ServerLevel мир, ServerPlayer игрок, int уровень) {
        RandomSource ГСЧ = мир.random;

        int ближе = PlagueConstants.BORDER_OMEN_MIN_DISTANCE;
        int дальше = Math.max(ближе + 1, PlagueConstants.BORDER_OMEN_MAX_DISTANCE);
        double дистанция = ближе + ГСЧ.nextDouble() * (дальше - ближе);
        double угол = ГСЧ.nextDouble() * Math.PI * 2.0;

        double x = игрок.getX() + Math.cos(угол) * дистанция;
        double z = игрок.getZ() + Math.sin(угол) * дистанция;
        double y = игрок.getY() + ГСЧ.nextInt(2 * РАЗБРОС_ВЫСОТЫ + 1) - РАЗБРОС_ВЫСОТЫ;

        игрок.connection.send(new ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(звук(ГСЧ)),
            SoundSource.AMBIENT, x, y, z,
            0.7f + ГСЧ.nextFloat() * 0.3f,
            0.9f + ГСЧ.nextFloat() * 0.2f,
            ГСЧ.nextLong()));

        if (уровень >= 3 && ГСЧ.nextFloat() < PlagueConstants.BORDER_OMEN_ASH) {
            пепел(мир, игрок, ГСЧ);
        }
    }

    /**
     * Четыре звука бытового запустения. Выбираются лениво, а не из
     * статического массива: ванильные {@link SoundEvents} трогать при
     * загрузке нашего класса рано — реестр к тому времени может быть
     * ещё не собран.
     */
    private static SoundEvent звук(RandomSource ГСЧ) {
        return switch (ГСЧ.nextInt(4)) {
            case 0 -> PlagueSounds.PLAYER_COUGH.get();      // чужой кашель за стеной
            case 1 -> SoundEvents.WOODEN_TRAPDOOR_OPEN;     // скрип
            case 2 -> SoundEvents.GRAVEL_BREAK;             // осыпалось
            default -> SoundEvents.ITEM_BREAK;              // что-то упало
        };
    }

    /**
     * Горсть пепла у земли, недалеко: частицы должны попадать в поле
     * зрения, иначе их не увидит никто. Летят только тому, кто бросал
     * кубик, — по той же причине, что и звук.
     */
    private static void пепел(ServerLevel мир, ServerPlayer игрок, RandomSource ГСЧ) {
        double угол = ГСЧ.nextDouble() * Math.PI * 2.0;
        double дистанция = 3.0 + ГСЧ.nextDouble() * 3.0;
        double x = игрок.getX() + Math.cos(угол) * дистанция;
        double z = игрок.getZ() + Math.sin(угол) * дистанция;
        double y = мир.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            BlockPos.containing(x, игрок.getY(), z).getX(),
            BlockPos.containing(x, игрок.getY(), z).getZ());

        мир.sendParticles(игрок, ParticleTypes.ASH, false,
            x, Math.min(y + 1.5, игрок.getY() + 4), z,
            12, 0.8, 0.5, 0.8, 0.01);
    }
}
