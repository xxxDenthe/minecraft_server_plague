package dev.denthe.plaguecore.mc.border;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.BorderMath;
import dev.denthe.plaguecore.core.PhaseTable;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.mc.PlagueState;
import dev.denthe.plaguecore.mc.SporeSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Прилив: из ближайшей Гнили выходит волна и идёт на игрока.
 *
 * Смысл — дать Пограничью ритм суток. Днём это зона, по которой ходят
 * за лутом; ночью она перестаёт быть проходной, и решение «где ночевать»
 * становится решением.
 *
 * <p><b>Сам по себе прилив не выходит.</b> Решением владельца
 * 2026-09-11 закатный автозапуск снят вместе с предупреждением перед
 * ним: волну пускает админ командой {@code /plague tide}, и она идёт
 * сразу всем, у кого рядом Гниль. Ритм суток теперь задаёт админ,
 * а не часы мира.
 *
 * <p><b>Гниль ищется от игрока, а не от города.</b> Нет Гнили в радиусе
 * {@code BORDER_TIDE_SEARCH_CHUNKS} — нет и волны. Ночевать далеко от
 * заразы должно оставаться законным способом прожить ночь: иначе прилив
 * превратился бы в налог на существование, который платят все и всегда.
 *
 * <p><b>Отката к утру не написано.</b> Мутировавшие зомби и ванильные
 * скелеты горят на солнце сами — это и есть отступление прилива. Своя
 * уборка добавила бы код, который делает то, что и так делает солнце.
 *
 * <p>Волна пользуется тем же спавном, что и выводок у спорового мешка
 * ({@link SporeSpawner#выпустить}): состав и повод разные, способ
 * поставить моба на землю — один.
 */
public final class BorderTide {
    private BorderTide() {}

    /** Уровень, начиная с которого чанк считается Гнилью, а не Пограничьем. */
    private static final int ГНИЛЬ = 4;

    /**
     * Выпустить волну каждому, у кого рядом есть Гниль.
     * Возвращает число вышедших волн — нужно для журнала и команд.
     */
    public static int наЗакате(ServerLevel мир, PlagueState состояние) {
        int фаза = PhaseTable.phaseForNight(состояние.night());
        int волн = 0;
        for (ServerPlayer игрок : мир.players()) {
            if (волна(мир, игрок, состояние, фаза)) волн++;
        }
        return волн;
    }

    /**
     * Одна волна на одного игрока. Возвращает false, если волне неоткуда
     * или незачем выходить.
     */
    public static boolean волна(ServerLevel мир, ServerPlayer игрок,
                                PlagueState состояние, int фаза) {
        if (игрок.isCreative() || игрок.isSpectator()) return false;

        BlockPos откуда = откудаИдёт(мир, игрок, состояние);
        if (откуда == null) return false;

        int зомби = BorderMath.поУровню(PlagueConstants.BORDER_TIDE_ZOMBIES, фаза);
        int скелетов = BorderMath.поУровню(PlagueConstants.BORDER_TIDE_SKELETONS, фаза);
        int вышло = SporeSpawner.выпустить(мир, откуда, мир.random, игрок, зомби, скелетов);
        if (вышло == 0) return false;

        PlagueCore.LOG.info("Прилив: {} мобов вышло из {} на игрока {}",
            вышло, откуда, игрок.getGameProfile().getName());
        return true;
    }

    /**
     * Точка выхода волны — поверхность ближайшего чанка Гнили.
     * {@code null}, если Гнили рядом нет или она слишком близко.
     *
     * Слишком близко — это когда игрок уже в самой гнили или у её края:
     * там своё дело делают выводки у мешков, и волна поверх них была бы
     * второй порцией того же самого, только в упор.
     */
    private static BlockPos откудаИдёт(ServerLevel мир, ServerPlayer игрок, PlagueState состояние) {
        int радиус = PlagueConstants.BORDER_TIDE_SEARCH_CHUNKS;
        if (радиус <= 0) return null;

        PlagueGrid сетка = состояние.grid();
        int очаг = BorderMath.ближайшийОчаг(сетка,
            игрок.chunkPosition().x, игрок.chunkPosition().z, радиус, ГНИЛЬ);
        if (очаг < 0) return null;

        int x = сетка.chunkXOf(очаг) * 16 + 8;
        int z = сетка.chunkZOf(очаг) * 16 + 8;
        double минимум = PlagueConstants.BORDER_TIDE_MIN_DISTANCE;
        if (игрок.distanceToSqr(x + 0.5, игрок.getY(), z + 0.5) < минимум * минимум) return null;

        return new BlockPos(x, мир.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

}
