package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Победа: что происходит после смерти Сердца.
 *
 * Отдельно от {@link HeartFight} по простой причине: очистка идёт три
 * минуты уже после того, как сущность Сердца исчезла, и тикать внутри
 * неё нечему. Поэтому победа висит на тике сервера.
 *
 * Порядок строгий: вспышка, потом очистка кругами, потом выздоровление
 * людей, потом титры.
 *
 * Дизайн — docs/superpowers/specs/2026-09-07-final-boj-design.md
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class HeartVictory {
    private HeartVictory() {}

    /** Мир, по которому идёт очистка. null — победы сейчас нет. */
    private static ServerLevel мир;

    private static int центрЧанкX;
    private static int центрЧанкZ;

    /** Кольцо, которое чистим следующим. Ноль — чанк самого логова. */
    private static int кольцо;

    /** Сколько колец всего; дальше края сетки чистить нечего. */
    private static int колец;

    private static int тиковНаКольцо;
    private static int доКольца;

    /**
     * Начать победу. Вспышка уходит сразу, всё остальное — по тикам.
     *
     * @param где место Сердца: от него расходятся круги очистки
     */
    public static void начать(ServerLevel уровень, BlockPos где) {
        for (ServerPlayer игрок : уровень.players()) {
            PlagueNetwork.отправитьВспышку(игрок);
        }

        PlagueGrid сетка = PlagueState.get(уровень).grid();
        мир = уровень;
        центрЧанкX = где.getX() >> 4;
        центрЧанкZ = где.getZ() >> 4;
        кольцо = 0;
        колец = сетка.size();   // с запасом: логово может стоять у края

        int всегоТиков = Math.max(1, PlagueConstants.HEART_CLEANSE_MINUTES) * 60 * 20;
        тиковНаКольцо = Math.max(1, всегоТиков / Math.max(1, колец));
        доКольца = 0;

        PlagueCore.LOG.info("Сердце пало: очистка мира от чанка {},{}",
            центрЧанкX, центрЧанкZ);
    }

    @SubscribeEvent
    public static void приТике(ServerTickEvent.Post событие) {
        if (мир == null) return;
        if (--доКольца > 0) return;
        доКольца = тиковНаКольцо;

        очиститьКольцо(кольцо);
        кольцо++;

        if (кольцо > колец) завершить();
    }

    /**
     * Кольцо чанков на расстоянии r от логова, по-шахматному: квадрат
     * без середины. Круги должны расходиться ровно, а не эллипсом.
     */
    private static void очиститьКольцо(int r) {
        PlagueState состояние = PlagueState.get(мир);
        PlagueGrid сетка = состояние.grid();

        for (int дх = -r; дх <= r; дх++) {
            for (int дз = -r; дз <= r; дз++) {
                if (Math.max(Math.abs(дх), Math.abs(дз)) != r) continue;

                int чх = центрЧанкX + дх;
                int чз = центрЧанкZ + дз;
                if (!сетка.contains(чх, чз)) continue;
                if (сетка.getLevel(чх, чз) == 0) continue;

                // Ставим ноль напрямую, а не через cleanseChunk: тот
                // снимает по уровню за раз и держит пол рядом с гнилью —
                // правило баланса эпидемии, которой больше нет.
                сетка.setLevel(чх, чз, 0);
                Materializer.поставить(состояние, чх, чз);
            }
        }
        состояние.setDirty();
    }

    /** Стадии 2 и 3: люди выздоравливают, потом титры. */
    private static void завершить() {
        for (ServerPlayer игрок : мир.players()) {
            PlayerPlagueData данные = PlayerPlagueData.данные(игрок);
            данные.смертей = 0;                  // постоянные потери возвращаются
            PlayerInfection.задать(игрок, 0f);   // задать() сам пересчитает здоровье
            игрок.setHealth(игрок.getMaxHealth());

            игрок.sendSystemMessage(Component.translatable("message.plaguecore.victory"));
        }

        PlagueCore.LOG.info("Мир очищен, сессия завершена");
        мир = null;
    }
}
