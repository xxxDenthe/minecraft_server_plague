package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.MaterializationMask;
import dev.denthe.plaguecore.core.PlagueGrid;
import dev.denthe.plaguecore.core.SurfaceRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Мост между числами эпидемии и блоками мира. Дизайн материализации.
 *
 * Лень — главное свойство: блоки меняются не когда чанк заразился,
 * а когда на него кто-то смотрит. Пока в место никто не заходит,
 * сервер не тратит на него ни такта.
 *
 * Порядок работы жёсткий и не подлежит упрощению:
 *   ChunkEvent.Load только ставит номер чанка в очередь — событие может
 *   прийти раньше статуса FULL, и работа с блоками прямо в обработчике
 *   вешает загрузку;
 *   ServerTickEvent.Post тратит бюджет блоков и меняет мир.
 *
 * Всё в главном потоке. Блоки чанка лежат в PalettedContainer со сторожем
 * ThreadingDetector: доступ из двух потоков роняет игру.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class Materializer {
    private Materializer() {}

    /**
     * Клиент узнаёт о новом блоке, но соседей не будим и формы не
     * пересчитываем — самая дешёвая комбинация для пакетной замены.
     * Дропов setBlock не создаёт вовсе.
     */
    private static final int ФЛАГИ = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Столбцов в чанке: 16 на 16. */
    private static final int СТОЛБЦОВ = 256;

    /**
     * Насколько глубоко проход спускается сквозь крону в поисках земли.
     * Джунглевое дерево — самое высокое в ваниле, около тридцати блоков.
     */
    private static final int ВЫСОТА_ДЕРЕВА = 32;

    /** Куда пытаемся положить нарост, в порядке предпочтения. */
    private static final Direction[] ПОРЯДОК_ГРАНЕЙ = {
        Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST, Direction.DOWN
    };

    private static final MaterializationQueue ОЧЕРЕДЬ =
        new MaterializationQueue(PlagueConstants.MAX_QUEUE_LENGTH);

    // ── события ───────────────────────────────────────────────────────

    @SubscribeEvent
    public static void приЗагрузкеЧанка(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ChunkPos pos = event.getChunk().getPos();
        поставить(PlagueState.get(level), pos.x, pos.z);
    }

    @SubscribeEvent
    public static void приТикеСервера(ServerTickEvent.Post event) {
        if (ОЧЕРЕДЬ.isEmpty()) return;
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        отработатьТик(overworld, PlagueState.get(overworld), PlagueConstants.BLOCKS_PER_TICK);
    }

    // ── очередь ───────────────────────────────────────────────────────

    /**
     * Поставить чанк в очередь, если картинка в нём отстала от расчёта.
     *
     * Сравниваем не с уровнем чанка, а с наибольшим уровнем вокруг: доля
     * поражённых столбцов сглажена между соседями, поэтому чанк отстаёт
     * и когда заразился не он сам, а сосед — тогда у него сдвинулась кайма.
     */
    public static boolean поставить(PlagueState state, int cx, int cz) {
        PlagueGrid grid = state.grid();
        int i = grid.index(cx, cz);
        if (i < 0) return false;
        // Обратный проход при очистке — забота подсистемы очистителей,
        // здесь только рост.
        if (grid.maxLevelAround(i) <= grid.getAppliedSurfaceAt(i)) return false;
        return ОЧЕРЕДЬ.enqueue(i);
    }

    /**
     * После ночного тика: догнать те чанки, что уже загружены. Незагруженные
     * приедут сами при загрузке — в этом и смысл ленивости.
     */
    public static int поставитьЗагруженные(ServerLevel level, PlagueState state) {
        PlagueGrid grid = state.grid();
        int поставлено = 0;
        for (int i = 0; i < grid.cellCount(); i++) {
            if (grid.maxLevelAround(i) <= grid.getAppliedSurfaceAt(i)) continue;
            int cx = grid.chunkXOf(i);
            int cz = grid.chunkZOf(i);
            if (level.getChunkSource().getChunkNow(cx, cz) == null) continue;
            if (ОЧЕРЕДЬ.enqueue(i)) поставлено++;
        }
        return поставлено;
    }

    public static int длинаОчереди() { return ОЧЕРЕДЬ.size(); }

    /** Нужно при ручной перегенерации мира. */
    public static void сброситьОчередь() { ОЧЕРЕДЬ.clear(); }

    // ── работа ────────────────────────────────────────────────────────

    /** Один тик работы. Возвращает, сколько блоков реально изменено. */
    public static int отработатьТик(ServerLevel level, PlagueState state, int бюджет) {
        PlagueGrid grid = state.grid();
        long seed = level.getSeed();
        int изменено = 0;
        boolean грязно = false;

        while (бюджет > 0 && !ОЧЕРЕДЬ.isEmpty()) {
            int i = ОЧЕРЕДЬ.head();
            int cx = grid.chunkXOf(i);
            int cz = grid.chunkZOf(i);

            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {           // выгрузился — приедет при следующей загрузке
                ОЧЕРЕДЬ.finishHead();
                continue;
            }

            // Уровень чанка решает, во что превращать блок; наибольший
            // уровень вокруг — до чего чанк надо дорисовать.
            int уровень = grid.getLevelAt(i);
            int цель = grid.maxLevelAround(i);
            if (цель <= grid.getAppliedSurfaceAt(i)) {   // кто-то успел раньше
                ОЧЕРЕДЬ.finishHead();
                continue;
            }

            int столбец = ОЧЕРЕДЬ.cursor();
            // Столбец доводим до конца целиком: он стоит не больше четырёх
            // блоков, а обрывать его на середине значит хранить ещё и курсор
            // глубины — сложность ради трёх блоков перерасхода.
            while (столбец < СТОЛБЦОВ && бюджет > 0) {
                int wx = (cx << 4) + (столбец & 15);
                int wz = (cz << 4) + (столбец >> 4);
                float доля = MaterializationMask.fractionAt(grid, wx, wz);
                if (MaterializationMask.isAffected(seed, wx, wz, доля)) {
                    // Уровень своего чанка задаёт силу: подзол или гниль.
                    // Если чанк чист, а кайма соседа сюда дотянулась, берём
                    // силу соседа — иначе гниль обрывалась бы подзолом.
                    int сила = Math.max(уровень, силаПоДоле(доля));
                    int сделано = поразитьСтолбец(level, chunk, seed, wx, wz, сила);
                    бюджет -= сделано;
                    изменено += сделано;
                }
                столбец++;
            }

            if (столбец >= СТОЛБЦОВ) {
                grid.setAppliedSurfaceAt(i, цель);
                грязно = true;
                ОЧЕРЕДЬ.finishHead();
            } else {
                ОЧЕРЕДЬ.setCursor(столбец);
            }
        }

        if (грязно) state.setDirty();
        return изменено;
    }

    /**
     * Дерево целиком, потом земля под ним и несколько блоков вглубь.
     *
     * Раньше проход брал верхний блок столбца и шесть под ним, и этого
     * хватало только на голом месте. Под деревом верхний блок — макушка
     * кроны: нижняя листва оставалась зелёной, ствол не трогался вовсе,
     * а трава под кроной — единственное, что оставалось чистым в замере
     * заражённого чанка. Поэтому дерево сначала проходится насквозь,
     * и лишь от земли начинает считаться глубина.
     */
    private static int поразитьСтолбец(ServerLevel level, LevelChunk chunk, long seed,
                                       int wx, int wz, int уровень) {
        int верх = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
        int дно = level.getMinBuildHeight();
        int изменено = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Сквозь крону вниз. Просветы между листвой проходим тоже: столбец
        // рядом со стволом — это лист, воздух, и только потом земля.
        int земля = верх;
        int запас = ВЫСОТА_ДЕРЕВА;
        while (земля >= дно && запас > 0) {
            pos.set(wx, земля, wz);
            BlockState было = chunk.getBlockState(pos);
            if (!было.isAir() && !BlockTransforms.isWood(было)) break;
            изменено += поразить(level, pos, было, seed, уровень);
            земля--;
            запас--;
        }
        // Столбец, где земли под кроной так и не нашлось: край обрыва,
        // висячий остров. Копать наугад в пустоту не станем.
        if (запас == 0 || земля < дно) return изменено;

        for (int глубина = 0; глубина <= PlagueConstants.SURFACE_DEPTH; глубина++) {
            int y = земля - глубина;
            if (y < дно) break;
            pos.set(wx, y, wz);
            изменено += поразить(level, pos, chunk.getBlockState(pos), seed, уровень);
        }

        // Мешок сажаем последним: земля под ним к этому времени уже гнилая.
        return изменено + посадитьМешок(level, chunk, seed, wx, wz, земля, уровень);
    }

    /**
     * Споровый мешок на поверхности. Близнец пещерного, но реже:
     * под землёй мешок — находка, наверху он должен быть приметой места,
     * а не сорняком.
     *
     * До сих пор мешки жили только в пещерах, и владелец сообщил, что
     * наверху их «просто нет». Так и было: подземный проход нарочно
     * отступает от поверхности, а поверхностный про мешки не знал.
     *
     * Опора ищется на два блока вниз: «земля» столбца может оказаться
     * травинкой или цветком, а мешку нужно на чём стоять.
     */
    private static int посадитьМешок(ServerLevel level, LevelChunk chunk, long seed,
                                     int wx, int wz, int земля, int уровень) {
        float вес = MaterializationMask.columnWeight(seed, wx * 7 + 3, wz * 13 - 5);
        if (!SurfaceRule.sporeSacAt(уровень, вес, PlagueConstants.SURFACE_SPORE_SAC)) return 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = земля; y >= земля - 1; y--) {
            pos.set(wx, y, wz);
            if (!chunk.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) continue;

            BlockPos место = new BlockPos(wx, y + 1, wz);
            BlockState было = level.getBlockState(место);
            // Место занято чем-то основательным — или мешок уже стоит.
            if (!было.isAir() && !было.canBeReplaced()) return 0;
            if (было.is(PlagueBlocks.SPORE_SAC.get())) return 0;

            level.setBlock(место, BlockTransforms.sporeSac(), ФЛАГИ);
            return 1;
        }
        return 0;
    }

    /** Один блок: подмена или нарост. Возвращает, сколько блоков изменено. */
    private static int поразить(ServerLevel level, BlockPos.MutableBlockPos pos,
                                BlockState было, long seed, int уровень) {
        if (было.isAir() || BlockTransforms.isPlagueBlock(было)) return 0;

        BlockState стало = BlockTransforms.replacement(было, уровень);
        if (стало != null) {
            level.setBlock(pos.immutable(), стало, ФЛАГИ);
            return 1;
        }
        if (BlockTransforms.needsCoating(было, уровень)
            && пятноЗдесь(seed, pos.getX(), pos.getY(), pos.getZ())) {
            return обрастить(level, pos.immutable());
        }
        return 0;
    }

    /**
     * Наименьший уровень, которому положена такая доля. Нужен только
     * в кайме, вылезшей за пределы своего чанка: там уровень чанка нулевой,
     * а блоки менять уже надо.
     */
    private static int силаПоДоле(float доля) {
        for (int уровень = 1; уровень <= PlagueConstants.MAX_NATURAL_LEVEL; уровень++) {
            if (доля <= MaterializationMask.fractionFor(уровень)) return уровень;
        }
        return PlagueConstants.MAX_NATURAL_LEVEL;
    }

    /**
     * Нарост ложится пятнами, а не сплошняком: сквозь него должен
     * просвечивать камень. Выбор детерминирован, как и вся маска.
     */
    private static boolean пятноЗдесь(long seed, int wx, int y, int wz) {
        return MaterializationMask.columnWeight(seed, wx * 31 + y, wz * 17 - y)
            < PlagueConstants.GROWTH_PATCH_FRACTION;
    }

    /**
     * Плёнка кладётся не вместо блока, а в соседний воздух, гранью
     * к самому блоку. Так ствол и руда остаются на месте, но обрастают.
     */
    private static int обрастить(ServerLevel level, BlockPos опора) {
        for (Direction грань : ПОРЯДОК_ГРАНЕЙ) {
            BlockPos место = опора.relative(грань);
            if (!level.getBlockState(место).isAir()) continue;
            BlockState нарост = BlockTransforms.coating()
                .setValue(MultifaceBlock.getFaceProperty(грань.getOpposite()), true);
            level.setBlock(место, нарост, ФЛАГИ);
            return 1;
        }
        return 0;   // блок замурован со всех сторон, обрастать некуда
    }
}
