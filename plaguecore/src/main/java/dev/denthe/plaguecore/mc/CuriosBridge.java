package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Мост к Curios — тому же роду, что и {@link ClassBridge}: только
 * рефлексия, никакой Gradle-зависимости. `plaguecore` обязан грузиться
 * и работать без Curios, просто повязку тогда некуда надеть.
 *
 * Один метод вместо двух: `findFirstCurio` сразу отвечает и «надета ли»,
 * и «какой это стек» — второй нужен, чтобы стачивать прочность.
 */
public final class CuriosBridge {
    private CuriosBridge() {}

    private static Method методИнвентарь;
    private static Method методНайти;
    private static Method методСтек;
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            методИнвентарь = api.getMethod("getCuriosInventory", LivingEntity.class);
            методНайти = Class
                .forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                .getMethod("findFirstCurio", Item.class);
            методСтек = Class.forName("top.theillusivec4.curios.api.SlotResult")
                .getMethod("stack");
            доступен = true;
            PlagueCore.LOG.info("Curios найден, повязку есть куда надеть");
        } catch (ReflectiveOperationException e) {
            доступен = false;
            PlagueCore.LOG.info("Curios не найден: повязка будет лежать без дела");
        }
    }

    /**
     * Надетый предмет из слота Curios или {@link ItemStack#EMPTY}.
     * Любая осечка отражения — пустой стек, а не краш: без Curios
     * подсистема должна работать ровно так же, только без повязки.
     */
    public static ItemStack надето(LivingEntity кто, Item предмет) {
        инициализировать();
        if (!доступен) return ItemStack.EMPTY;
        try {
            Object инвентарь = ((Optional<?>) методИнвентарь.invoke(null, кто)).orElse(null);
            if (инвентарь == null) return ItemStack.EMPTY;
            Object результат = ((Optional<?>) методНайти.invoke(инвентарь, предмет)).orElse(null);
            if (результат == null) return ItemStack.EMPTY;
            return (ItemStack) методСтек.invoke(результат);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return ItemStack.EMPTY;
        }
    }
}
