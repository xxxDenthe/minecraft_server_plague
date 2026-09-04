package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.InfectionMath;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Тик игрока: сколько чумы он набрал и что с ним от этого происходит.
 *
 * Считается раз в секунду, а не каждый тик: восемь игроков против сотен
 * чанков — работа копеечная, но и её нет смысла делать двадцать раз
 * в секунду, когда числа в спеке заданы «за секунду».
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlayerInfection {
    private PlayerInfection() {}

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (!(игрок.level() instanceof ServerLevel мир)) return;
        // Сетка чумы живёт только в верхнем мире.
        if (мир.dimension() != Level.OVERWORLD) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);

        float экспозиция = экспозицияДля(игрок, мир, д);
        д.заражённость = InfectionMath.следующая(д.заражённость, экспозиция);
        д.стадия = InfectionMath.стадия(д.заражённость);
    }

    /** Сколько очков в секунду набирает или теряет игрок там, где стоит. */
    private static float экспозицияДля(ServerPlayer игрок, ServerLevel мир, PlayerPlagueData д) {
        if (мир.getGameTime() < д.иммунитетДо) return 0f;

        PlagueGrid сетка = PlagueState.get(мир).grid();
        int cx = SectionPos.blockToSectionCoord(игрок.getBlockX());
        int cz = SectionPos.blockToSectionCoord(игрок.getBlockZ());
        if (!сетка.contains(cx, cz)) return 0f;

        int уровень = сетка.getLevel(cx, cz);
        boolean подЗемлёй = !мир.canSeeSky(игрок.blockPosition());
        return InfectionMath.экспозиция(уровень, подЗемлёй, защита(игрок));
    }

    /**
     * Доля погашенной экспозиции, 0..1.
     *
     * Пока считается только по броне: очко брони гасит один процент.
     * Полный алмаз (20 очков) — пятая часть. Слот Curios и эффекты Клирика
     * подключатся в подсистеме классов, здесь для них оставлено место.
     *
     * ponytail: линейная прикидка от брони; заменить настоящей формулой,
     * когда появятся маски и зелья Клирика
     */
    public static float защита(ServerPlayer игрок) {
        return Math.min(0.9f, игрок.getArmorValue() * 0.01f);
    }

    /** Выставить заражённость снаружи: команда, отвар, лекарство Клирика. */
    public static void задать(ServerPlayer игрок, float значение) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.заражённость = Math.max(0f, Math.min(100f, значение));
        д.стадия = InfectionMath.стадия(д.заражённость);
    }
}
