package dev.denthe.classes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Плоский фасад для мостов-соседей. Спек — 2026-09-04-klassy-design.md,
 * раздел 4. Тот же приём, что {@code ShadeApi} у `lmpc_shade`: `plaguecore`
 * зовёт эти методы рефлексией ({@code ClassBridge}), жёсткой
 * Gradle-зависимости между джарами нет.
 *
 * Сигнатуры менять с оглядкой — рефлексия на другой стороне ищет их
 * по имени и типам параметров.
 */
public final class ClassesApi {
    private ClassesApi() {}

    /**
     * Доп. защита от кулона Клирика, 0..1. Полная у Клирика, вполовину
     * у остальных классов (кулон можно носить кому угодно), ноль без
     * кулона на игроке.
     */
    public static float protectionBonus(Player игрок) {
        if (!(игрок instanceof LivingEntity живой)) return 0f;

        boolean носитКулон = CuriosApi.getCuriosInventory(живой)
            .map(инвентарь -> инвентарь.isEquipped(ClassItems.CLERICS_PENDANT.get()))
            .orElse(false);
        if (!носитКулон) return 0f;

        float базовая = ClassesConfig.кулонБазоваяЗащита();
        PlayerClassData.Класс класс = PlayerClassData.данные(игрок).класс;
        return класс == PlayerClassData.Класс.CLERIC
            ? базовая
            : базовая * ClassesConfig.кулонДоляНеКлирику();
    }
}
