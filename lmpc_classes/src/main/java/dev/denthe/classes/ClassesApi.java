package dev.denthe.classes;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Плоский фасад для мостов-соседей. Спек — 2026-09-04-klassy-design.md,
 * раздел 4 и 2.1. Тот же приём, что {@code ShadeApi} у `lmpc_shade`:
 * `plaguecore` зовёт эти методы рефлексией ({@code ClassBridge}),
 * жёсткой Gradle-зависимости между джарами нет.
 *
 * Сигнатуры менять с оглядкой — рефлексия на другой стороне ищет их
 * по имени и типам параметров. Возвращаемые типы нарочно примитивные
 * и {@code String}: ни одному соседу не нужно тянуть к себе наш
 * {@code enum}, чтобы прочитать класс игрока.
 */
public final class ClassesApi {
    private ClassesApi() {}

    /**
     * Доп. защита от кулона Клирика, 0..1. Полная у Клирика (и растёт
     * с его тиром мастерства), вполовину у остальных классов — кулон
     * можно носить кому угодно, — ноль без кулона на игроке.
     *
     * Потолок 0.9 жёсткий: полная неуязвимость к чуме от одного
     * предмета сломала бы всю подсистему 2, какой бы конфиг ни стоял.
     */
    public static float protectionBonus(Player игрок) {
        boolean носитКулон = CuriosApi.getCuriosInventory(игрок)
            .map(инвентарь -> инвентарь.isEquipped(ClassItems.CLERICS_PENDANT.get()))
            .orElse(false);
        if (!носитКулон) return 0f;

        PlayerClassData д = PlayerClassData.данные(игрок);
        float базовая = ClassesConfig.кулонБазоваяЗащита();
        if (д.класс != PlayerClassData.Класс.CLERIC) {
            return базовая * ClassesConfig.кулонДоляНеКлирику();
        }
        return Math.min(0.9f, базовая * ClassesConfig.силаТира(д.тир()));
    }

    /**
     * Класс игрока строкой, как в {@link PlayerClassData.Класс}:
     * NONE, CLERIC, SMITH, FARMER, CHRONICLER.
     *
     * Строка, а не enum, намеренно: соседи читают этот метод
     * рефлексией и не должны знать наших типов.
     */
    public static String className(Player игрок) {
        return PlayerClassData.данные(игрок).класс.name();
    }

    /** Мастерство текущего класса, 0..100. Спек, раздел 2.1. */
    public static int mastery(Player игрок) {
        return PlayerClassData.данные(игрок).мастерство;
    }

    /** Тир текущего класса, 1..3. */
    public static int masteryTier(Player игрок) {
        return PlayerClassData.данные(игрок).тир();
    }
}
