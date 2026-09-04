package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Мост к `lmpc_classes` — необязательному соседнему моду с системой
 * классов. Только рефлексия на публичный метод, Gradle-зависимости
 * между джарами нет и не будет: `plaguecore` не должен знать, что
 * `lmpc_classes` вообще существует, кроме этого одного файла.
 *
 * Любая ошибка отражения — тихий отказ, не краш: без `lmpc_classes`
 * (или при не совпавшей версии) защита от классов просто не добавляется.
 */
final class ClassBridge {
    private ClassBridge() {}

    private static Method методЗащита;
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> api = Class.forName("dev.denthe.classes.ClassesApi");
            методЗащита = api.getMethod("protectionBonus", Player.class);
            доступен = true;
            PlagueCore.LOG.info("lmpc_classes найден, мост классов подключён");
        } catch (ReflectiveOperationException e) {
            доступен = false;
        }
    }

    /** Доп. защита от классового кулона, 0..1. Ноль, если lmpc_classes не установлен. */
    static float дополнительнаяЗащита(Player игрок) {
        инициализировать();
        if (!доступен) return 0f;
        try {
            Object результат = методЗащита.invoke(null, игрок);
            return результат instanceof Float ф ? ф : 0f;
        } catch (ReflectiveOperationException e) {
            return 0f;
        }
    }
}
