package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.DreadKinds;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Клиентская половина видений. Спек хоррора, раздел 2.
 *
 * Силуэт — клиентская сущность, добавленная только в свой `ClientLevel`.
 * Сервер о ней не знает: её нельзя ударить, она не бьёт, не толкает,
 * не мешает игре и не попадает в чужие экраны. Это самый дешёвый способ
 * показать человека на краю зрения — писать свой рендерер ради полутора
 * секунд незачем.
 *
 * Время меряется игровым временем мира, а не своим счётчиком тиков:
 * так же считает вспышку {@link PlagueOverlay}, и на паузе одиночной
 * игры видение не «доигрывается» само.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class DreadClient {
    private DreadClient() {}

    /**
     * Насколько густо темнеет экран в видении. Не совсем чёрный: полная
     * чернота читается как вылет драйвера, а не как испуг.
     */
    private static final float ПЛОТНОСТЬ_ТЕМНОТЫ = 0.88f;

    /**
     * Сетевой номер силуэта. Заведомо отрицательный: сервер раздаёт
     * своим сущностям положительные, и случайное совпадение стёрло бы
     * с экрана живого моба вместе с нашим фантомом.
     */
    private static final int НОМЕР_СИЛУЭТА = -0x5DEAD;

    /** Доля видения, которая уходит на возврат света. */
    private static final float ВОЗВРАТ = 0.4f;

    /** Игровое время начала темноты и её длительность. */
    private static long темнотаС = -1L;
    private static int темнотаТиков;

    /** Силуэт и время, когда его пора убрать. */
    private static Zombie силуэт;
    private static long силуэтДо = -1L;

    public static void принять(PlagueNetwork.Vision пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        switch (пакет.вид()) {
            case DreadKinds.ТЕМНОТА -> {
                темнотаС = mc.level.getGameTime();
                темнотаТиков = Math.max(1, пакет.тиков());
            }
            // Сердцебиение — ванильный звук Хранителя, самый узнаваемый
            // «сейчас что-то будет» в игре. Играем от самого игрока:
            // это его пульс, а не источник в мире.
            case DreadKinds.СЕРДЦЕ -> mc.player.playSound(
                SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1.0f);
            case DreadKinds.СИЛУЭТ -> поставитьСилуэт(mc, пакет);
            default -> { }
        }
    }

    /**
     * Плотность темноты сейчас, 0..1. Читает {@link PlagueOverlay}.
     * Гаснет мгновенно, возвращается плавно: так это читается как
     * задутый факел, а не как анимация интерфейса.
     */
    public static float силаТемноты() {
        Minecraft mc = Minecraft.getInstance();
        if (темнотаС < 0L || mc.level == null) return 0f;

        long прошло = mc.level.getGameTime() - темнотаС;
        if (прошло < 0L || прошло >= темнотаТиков) {
            темнотаС = -1L;
            return 0f;
        }

        float доля = (float) прошло / темнотаТиков;
        if (доля < 1f - ВОЗВРАТ) return ПЛОТНОСТЬ_ТЕМНОТЫ;
        return ПЛОТНОСТЬ_ТЕМНОТЫ * (1f - доля) / ВОЗВРАТ;
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (силуэт == null || mc.level == null) return;
        if (mc.level.getGameTime() >= силуэтДо) убратьСилуэт(mc);
    }

    private static void поставитьСилуэт(Minecraft mc, PlagueNetwork.Vision пакет) {
        убратьСилуэт(mc);
        Zombie з = EntityType.ZOMBIE.create(mc.level);
        if (з == null) return;

        з.setId(НОМЕР_СИЛУЭТА);
        з.setPos(пакет.x(), пакет.y(), пакет.z());
        з.setNoAi(true);
        з.setSilent(true);
        з.setInvulnerable(true);
        // Развернуть лицом к игроку: силуэт, стоящий спиной, читается
        // как обычный зомби, а не как «на меня смотрели».
        з.lookAt(EntityAnchorArgument.Anchor.EYES, mc.player.position());

        mc.level.addEntity(з);
        силуэт = з;
        силуэтДо = mc.level.getGameTime() + Math.max(1, пакет.тиков());
    }

    private static void убратьСилуэт(Minecraft mc) {
        if (силуэт != null && mc.level != null) {
            mc.level.removeEntity(НОМЕР_СИЛУЭТА, Entity.RemovalReason.DISCARDED);
        }
        силуэт = null;
        силуэтДо = -1L;
    }
}
