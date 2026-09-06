package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Мост к моду жажды (`ThirstWasTaken`) — снова только рефлексия.
 *
 * Жажда в этом моде живёт во вложении игрока и умеет ровно то, что нам
 * нужно: `addExhaustion` тратит её так же, как бег и работа. Своего
 * счётчика жажды у чумы нет и не будет — мод в паке уже есть, а второй
 * такой же счётчик игроку не объяснить.
 *
 * Без мода жажды всё молча ничего не делает: болезнь тогда бьёт только
 * по голоду.
 */
final class ThirstBridge {
    private ThirstBridge() {}

    private static AttachmentType<?> тип;
    private static Method методТратить;
    private static boolean инициализирован;
    private static boolean доступен;

    @SuppressWarnings("unchecked")
    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> вложения = Class.forName(
                "dev.ghen.thirst.foundation.common.capability.ModAttachment");
            тип = ((Supplier<AttachmentType<?>>) вложения
                .getField("PLAYER_THIRST").get(null)).get();
            методТратить = Class
                .forName("dev.ghen.thirst.foundation.common.capability.IThirst")
                .getMethod("addExhaustion", Player.class, float.class);
            доступен = true;
            PlagueCore.LOG.info("Мод жажды найден, чума будет сушить горло");
        } catch (ReflectiveOperationException | RuntimeException e) {
            доступен = false;
        }
    }

    /** Потратить жажду. Без мода жажды — тихо ничего. */
    static void потратить(Player игрок, float сколько) {
        if (сколько <= 0f) return;
        инициализировать();
        if (!доступен) return;
        try {
            Object жажда = игрок.getData(тип);
            if (жажда != null) методТратить.invoke(жажда, игрок, сколько);
        } catch (ReflectiveOperationException | RuntimeException e) {
            доступен = false;   // одна осечка — больше не дёргаем каждую секунду
        }
    }
}
