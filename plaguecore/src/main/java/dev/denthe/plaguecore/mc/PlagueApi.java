package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.InfectionMath;
import net.minecraft.server.level.ServerPlayer;

/**
 * Точка входа для подсистемы классов. Спек ядра, раздел 9.6.
 *
 * Ядро отдаёт наружу только интерфейс. Сами лекарства, рецепты и
 * способности — подсистема 3. Улучшенный отвар Клирика будет предметом,
 * который дёргает {@link #cure} и {@link #grantImmunity}, а не ещё одной
 * копией логики.
 *
 * В отличие от обычного отвара, {@link #cure} работает на любой стадии:
 * ограничение по стадии — свойство предмета, а не лечения как такового.
 */
public final class PlagueApi {
    private PlagueApi() {}

    /** Снять с игрока заражённость. Отрицательное значение игнорируется. */
    public static void cure(ServerPlayer игрок, float очков) {
        if (очков <= 0f) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        PlayerInfection.задать(игрок, д.заражённость - очков);
    }

    /**
     * Иммунитет на N тиков: набор заражённости и кашель соседей
     * не действуют. Уже действующий иммунитет не укорачивается.
     */
    public static void grantImmunity(ServerPlayer игрок, int тиков) {
        if (тиков <= 0) return;
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.иммунитетДо = Math.max(д.иммунитетДо, игрок.level().getGameTime() + тиков);
    }

    public static int getStage(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).стадия;
    }

    public static float getInfection(ServerPlayer игрок) {
        return PlayerPlagueData.данные(игрок).заражённость;
    }

    /** Сколько HP игрок потерял навсегда за смерти от чумы. */
    public static float getPermanentLoss(ServerPlayer игрок) {
        return InfectionMath.постоянныйШтраф(PlayerPlagueData.данные(игрок).смертей);
    }
}
