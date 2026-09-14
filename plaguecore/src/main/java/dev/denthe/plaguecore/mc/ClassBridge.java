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
public final class ClassBridge {
    private ClassBridge() {}

    private static Method методЗащита;
    private static Method методКласс;
    private static Method методШифр;
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> api = Class.forName("dev.denthe.classes.ClassesApi");
            методЗащита = api.getMethod("protectionBonus", Player.class);
            методКласс = api.getMethod("className", Player.class);
            доступен = true;
            // Появился позже остальных: у соседа старой сборки его нет,
            // и это не повод ронять весь мост — отвалится одна награда.
            try {
                методШифр = api.getMethod("rewardCipher", Player.class, int.class);
            } catch (NoSuchMethodException e) {
                методШифр = null;
            }
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

    /**
     * Класс игрока строкой: NONE, CLERIC, SMITH, FARMER, CHRONICLER.
     * Без `lmpc_classes` — всегда NONE, и это не ошибка, а отсутствие мода.
     *
     * Работает и на клиенте: вложение класса синкается игроку с 0.6.0.
     */
    public static String класс(Player игрок) {
        инициализировать();
        if (!доступен) return "NONE";
        try {
            Object результат = методКласс.invoke(null, игрок);
            return результат instanceof String строка ? строка : "NONE";
        } catch (ReflectiveOperationException e) {
            return "NONE";
        }
    }

    /** Клирик ли игрок. Ему интерфейс здоровья говорит чуть больше. */
    public static boolean клирик(Player игрок) {
        return "CLERIC".equals(класс(игрок));
    }

    /**
     * Летописец ли игрок. Без `lmpc_classes` — нет, и тогда подсказок
     * тайнописи не получает никто: движок работает, просто без них.
     */
    static boolean летописец(Player игрок) {
        return "CHRONICLER".equals(класс(игрок));
    }

    /**
     * Игрок назвал вслух новые слова тайнописи — Летописцу за это
     * идёт мастерство. Кто именно Летописец, решает сосед: здесь мы
     * только сообщаем факт.
     */
    static void наградитьЗаШифр(Player игрок, int слов) {
        инициализировать();
        if (!доступен || методШифр == null) return;
        try {
            методШифр.invoke(null, игрок, слов);
        } catch (ReflectiveOperationException e) {
            // тихо: награда за шифр — приятность, а не механика чумы
        }
    }
}
