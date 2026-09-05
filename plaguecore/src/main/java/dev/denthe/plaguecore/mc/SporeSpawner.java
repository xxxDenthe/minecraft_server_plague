package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.SpawnMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ночные выводки у споровых мешков.
 *
 * Ночью рядом с мешком с вероятностью {@code SPAWN_CHANCE_PER_NIGHT}
 * вылезает кучка: мутировавшие зомби и скелеты. Смысл в том, чтобы
 * гнилая земля была опасна сама по себе, а не только неприятна на вид:
 * ночевать посреди заражённого поля должно быть плохой идеей.
 *
 * <p><b>Мешки не ищутся перебором.</b> Кубик катает сам блок на своём
 * случайном тике — {@link SporeSacBlock#randomTick}. Иначе пришлось бы
 * каждую ночь обходить блоки вокруг каждого игрока: мешков в гнилом поле
 * сотни, а блоков в радиусе — миллионы. Побочная выгода: случайный тик
 * приходит только в загруженном чанке, то есть выводок появляется там,
 * где есть кому его встретить. Перевод «шанс за ночь» → «шанс за тик»
 * живёт в {@link SpawnMath}.
 *
 * <p><b>Потолок на игрока обязателен.</b> В гнилом поле мешков бывает
 * под сотню; без потолка каждый честно отсыпал бы свою четвёрку, и ночь
 * стала бы непроходимой, а сервер лёг бы от сущностей. Потолок считается
 * на игрока, а не на мир: одиночка в глуши не должен спасаться тем, что
 * лимит выел кто-то другой на другом конце карты.
 *
 * <p>Скелеты ванильные намеренно: своя мутировавшая версия потребовала бы
 * модели, текстуры и рендерера, а ванильный скелет ещё и сгорает на
 * рассвете — выводок убирает себя сам, и мир не засоряется.
 */
public final class SporeSpawner {
    private SporeSpawner() {}

    /**
     * Дальше этого от мешка игрока не ищем. Нужно из-за чанколоадеров
     * Create: случайный тик может прийти в чанк, загруженный машиной
     * за километр от людей, и выводок там был бы подарком серверу.
     */
    private static final int ПОИСК_ИГРОКА = 128;

    /** Сколько раз пробуем найти место одному мобу, прежде чем бросить. */
    private static final int ПОПЫТОК_НА_МОБА = 12;

    /**
     * Сколько кучек уже вылезло этой ночью у каждого игрока.
     *
     * ponytail: одна карта на весь сервер, без разделения по мирам.
     * Сессия идёт в одном мире; понадобится Нижний — ключом станет пара
     * «игрок + измерение».
     */
    private static final Map<UUID, Integer> кучекЗаНочь = new HashMap<>();

    /** Игровые сутки, за которые посчитаны кучки выше. */
    private static long суткиСчёта = Long.MIN_VALUE;

    /**
     * Случайный тик спорового мешка. Возвращает true, если выводок вылез —
     * нужно только для команды проверки и тестов на живом сервере.
     */
    public static boolean попытка(ServerLevel уровень, BlockPos мешок, RandomSource ГСЧ) {
        if (!уровень.isNight()) return false;

        float шанс = SpawnMath.шансЗаТик(PlagueConstants.SPAWN_CHANCE_PER_NIGHT);
        if (шанс <= 0f || ГСЧ.nextFloat() >= шанс) return false;

        return высыпать(уровень, мешок, ГСЧ);
    }

    /**
     * Выпустить кучку прямо сейчас, без броска кубика и без проверки ночи.
     * Отдельно от {@link #попытка} ради команды {@code /plague spawn}:
     * ждать выпадения 30% на живом сервере — плохой способ проверки.
     */
    public static boolean высыпать(ServerLevel уровень, BlockPos мешок, RandomSource ГСЧ) {
        Player игрок = уровень.getNearestPlayer(
            мешок.getX() + 0.5, мешок.getY() + 0.5, мешок.getZ() + 0.5, ПОИСК_ИГРОКА, false);
        if (игрок == null) return false;

        double минимум = PlagueConstants.SPAWN_MIN_PLAYER_DISTANCE;
        if (игрок.distanceToSqr(мешок.getX() + 0.5, мешок.getY() + 0.5, мешок.getZ() + 0.5)
                < минимум * минимум) {
            return false;
        }

        сброситьНаНовыхСутках(уровень);
        int было = кучекЗаНочь.getOrDefault(игрок.getUUID(), 0);
        if (было >= PlagueConstants.SPAWN_MAX_GROUPS_PER_NIGHT) return false;

        int вышло = насыпать(уровень, мешок, ГСЧ,
                             PlagueEntities.MUTATED_ZOMBIE.get(), PlagueConstants.SPAWN_ZOMBIES)
                  + насыпать(уровень, мешок, ГСЧ,
                             EntityType.SKELETON, PlagueConstants.SPAWN_SKELETONS);
        if (вышло == 0) return false;

        кучекЗаНочь.put(игрок.getUUID(), было + 1);
        return true;
    }

    /**
     * Счётчик обнуляется на новых сутках, а не на закате: ночь целиком
     * лежит внутри одних суток (13000–23000), так что за границу не
     * перескакивает и лишней проверки не нужно.
     */
    private static void сброситьНаНовыхСутках(ServerLevel уровень) {
        long сутки = уровень.getDayTime() / 24000L;
        if (сутки != суткиСчёта) {
            суткиСчёта = сутки;
            кучекЗаНочь.clear();
        }
    }

    /** Выпустить сколько получится мобов одного вида. Возвращает сколько вышло. */
    private static int насыпать(ServerLevel уровень, BlockPos мешок, RandomSource ГСЧ,
                                EntityType<? extends Mob> тип, int сколько) {
        int вышло = 0;
        for (int i = 0; i < сколько; i++) {
            BlockPos место = найтиМесто(уровень, мешок, ГСЧ);
            if (место == null) continue;

            Mob моб = тип.create(уровень);
            if (моб == null) continue;
            моб.moveTo(место.getX() + 0.5, место.getY(), место.getZ() + 0.5,
                       ГСЧ.nextFloat() * 360f, 0f);
            моб.finalizeSpawn(уровень, уровень.getCurrentDifficultyAt(место),
                              MobSpawnType.NATURAL, null);
            уровень.addFreshEntity(моб);
            вышло++;
        }
        return вышло;
    }

    /**
     * Место под моба рядом с мешком: две клетки воздуха на твёрдом полу.
     *
     * Постоянство мобам не выставляется намеренно — отойдёшь далеко,
     * и они пропадут сами, как ванильные. Иначе за четыре дня сессии
     * в гнилом поле накопились бы тысячи.
     */
    private static BlockPos найтиМесто(ServerLevel уровень, BlockPos мешок, RandomSource ГСЧ) {
        int радиус = PlagueConstants.SPAWN_RADIUS;
        for (int i = 0; i < ПОПЫТОК_НА_МОБА; i++) {
            BlockPos место = мешок.offset(
                ГСЧ.nextInt(2 * радиус + 1) - радиус,
                ГСЧ.nextInt(3) - 1,
                ГСЧ.nextInt(2 * радиус + 1) - радиус);
            if (годноеМесто(уровень, место)) return место;
        }
        return null;
    }

    private static boolean годноеМесто(ServerLevel уровень, BlockPos место) {
        BlockPos пол = место.below();
        return уровень.getBlockState(пол).isFaceSturdy(уровень, пол, Direction.UP)
            && пусто(уровень, место)
            && пусто(уровень, место.above());
    }

    /** Клетка свободна: ни столкновений, ни жидкости. */
    private static boolean пусто(ServerLevel уровень, BlockPos место) {
        return уровень.getBlockState(место).getCollisionShape(уровень, место).isEmpty()
            && уровень.getFluidState(место).isEmpty();
    }
}
