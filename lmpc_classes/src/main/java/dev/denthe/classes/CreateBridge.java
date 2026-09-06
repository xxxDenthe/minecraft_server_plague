package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * Мост к Create — тем же приёмом, что {@link PlagueBridge} к
 * `plaguecore`: только рефлексия, никакой Gradle-зависимости.
 *
 * Спек ядра (10.1) требует, чтобы Очиститель работал от вращения.
 * Наследоваться от {@code KineticBlockEntity}, как там написано,
 * значило бы завести жёсткую зависимость `lmpc_classes` на Create:
 * без Create мод перестал бы грузиться целиком, вместе с Клириком
 * и алтарём — ровно та беда, из-за которой Curios пришлось честно
 * пометить `required`. Здесь цена не оправдана: достаточно спросить
 * у соседнего блока его скорость.
 *
 * ponytail: стресс (SU) не потребляется — читаем скорость, но не
 * нагружаем сеть. Значит, одно водяное колесо крутит сколько угодно
 * очистителей, и ограничение спека «64 SU на штуку» не работает.
 * Апгрейд — наследование от KineticBlockEntity с настоящей
 * зависимостью на Create, когда Очиститель переедет в `plaguecore`
 * вместе с подсистемой 4.
 */
public final class CreateBridge {
    private CreateBridge() {}

    private static final String КЛАСС_КИНЕТИКА =
        "com.simibubi.create.content.kinetics.base.KineticBlockEntity";

    private static Class<?> кинетика;
    private static Method методGetSpeed;
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            кинетика = Class.forName(КЛАСС_КИНЕТИКА);
            методGetSpeed = кинетика.getMethod("getSpeed");
            доступен = true;
        } catch (ReflectiveOperationException e) {
            доступен = false;
        }
    }

    /**
     * Стороны, с которых принимается вращение: снизу (шестерня под блоком,
     * как в сцене Ponder) и сверху. Порядок значения не имеет — берётся
     * самый быстрый из двух.
     */
    private static final Direction[] ВЕРТИКАЛЬ = { Direction.DOWN, Direction.UP };

    /** Есть ли Create в сборке. */
    public static boolean доступен() {
        инициализировать();
        return доступен;
    }

    /**
     * Скорость вращения кинетического соседа сверху или снизу, по модулю.
     * Ноль — крутить нечем: ни Create, ни вала рядом.
     *
     * Смотрим только вверх и вниз, а не на все шесть сторон. Модель блока
     * показывает гнездо вала ровно на нижней грани, и принимать вращение
     * сбоку значило бы, что картинка врёт. Вал в округе не ищем тем более:
     * очиститель должен быть частью механизма, а не стоять рядом с ним.
     */
    public static float скоростьРядом(Level уровень, BlockPos позиция) {
        инициализировать();
        if (!доступен) return 0f;

        float лучшая = 0f;
        for (Direction сторона : ВЕРТИКАЛЬ) {
            BlockEntity сосед = уровень.getBlockEntity(позиция.relative(сторона));
            if (сосед == null || !кинетика.isInstance(сосед)) continue;
            try {
                Object значение = методGetSpeed.invoke(сосед);
                if (значение instanceof Number число) {
                    лучшая = Math.max(лучшая, Math.abs(число.floatValue()));
                }
            } catch (ReflectiveOperationException e) {
                // молчим — без Create очиститель просто стоит без питания
            }
        }
        return лучшая;
    }
}
