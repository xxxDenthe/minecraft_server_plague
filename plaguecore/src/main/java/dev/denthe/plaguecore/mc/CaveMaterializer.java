package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.CaveRule;
import dev.denthe.plaguecore.core.CaveRule.CaveAction;
import dev.denthe.plaguecore.core.CaveRule.CaveSpot;
import dev.denthe.plaguecore.core.MaterializationMask;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Материализация под землёй. Ядро, раздел 8.4; дизайн материализации, 6.
 *
 * Второй проход, независимый от поверхностного: своя очередь, свой
 * счётчик applied, свой бюджет. Так и задумано — работа тут в разы
 * дороже, а видит её куда меньше народу.
 *
 * Правило запуска другое и в этом весь смысл. Поверхность рисуется при
 * любой загрузке чанка, подземелье — только когда игрок реально стоит
 * под землёй. Иначе сервер прочёсывал бы каждый загруженный чанк от дна
 * мира до неба ради пещер, в которые никто не спустится.
 *
 * Работаем по границе «твёрдый блок рядом с воздухом»: идём по воздуху,
 * а не по камню. Сплошной массив породы не трогаем вообще — там нет
 * полости, а значит и чуме негде расти. Это разом и дёшево, и логично.
 *
 * Всё в главном потоке, как и поверхность: PalettedContainer сторожит
 * ThreadingDetector.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class CaveMaterializer {
    private CaveMaterializer() {}

    /** Те же флаги, что и на поверхности: клиенту сказать, соседей не будить. */
    private static final int ФЛАГИ = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final int СТОЛБЦОВ = 256;

    /** Как часто пересматриваем, кто из игроков ушёл под землю. */
    private static final int ТИКОВ_МЕЖДУ_ОПРОСАМИ = 20;

    /**
     * Сколько чанков вокруг игрока ставим в очередь.
     *
     * Спек говорит «в этом чанке», но игрок у края чанка смотрит
     * в соседний, и граница между заросшей и чистой пещерой резала бы
     * глаз. Кольцо в один чанк стоит недорого: работа всё равно
     * размазана по тикам потолком столбцов.
     */
    private static final int РАДИУС_ЧАНКОВ = 1;

    /** Самая длинная плеть лозы, в блоках. */
    private static final int ДЛИНА_ЛОЗЫ = 3;

    /** Куда пытаемся положить плёнку нароста, в порядке предпочтения. */
    private static final Direction[] БОКА = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final MaterializationQueue ОЧЕРЕДЬ =
        new MaterializationQueue(PlagueConstants.MAX_QUEUE_LENGTH);

    private static int тиков = 0;

    // ── события ───────────────────────────────────────────────────────

    @SubscribeEvent
    public static void приТикеСервера(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        if (++тиков >= ТИКОВ_МЕЖДУ_ОПРОСАМИ) {
            тиков = 0;
            поставитьПодИгроками(overworld, PlagueState.get(overworld));
        }

        if (ОЧЕРЕДЬ.isEmpty()) return;
        отработатьТик(overworld, PlagueState.get(overworld), PlagueConstants.BLOCKS_PER_TICK_CAVE);
    }

    // ── очередь ───────────────────────────────────────────────────────

    /**
     * Под землёй ли игрок. Порог — не координата Y, а отсутствие прямого
     * выхода к небу над головой: так шахта под холмом считается
     * подземельем, а карьер — нет.
     */
    private static boolean подЗемлёй(ServerLevel level, ServerPlayer игрок) {
        return !level.canSeeSky(игрок.blockPosition());
    }

    /** Поставить в очередь чанки вокруг тех, кто спустился под землю. */
    public static int поставитьПодИгроками(ServerLevel level, PlagueState state) {
        int поставлено = 0;
        for (ServerPlayer игрок : level.players()) {
            if (!подЗемлёй(level, игрок)) continue;
            ChunkPos центр = игрок.chunkPosition();
            for (int dx = -РАДИУС_ЧАНКОВ; dx <= РАДИУС_ЧАНКОВ; dx++) {
                for (int dz = -РАДИУС_ЧАНКОВ; dz <= РАДИУС_ЧАНКОВ; dz++) {
                    if (поставить(state, центр.x + dx, центр.z + dz)) поставлено++;
                }
            }
        }
        return поставлено;
    }

    /** Поставить чанк в очередь, если подземная картинка отстала от расчёта. */
    public static boolean поставить(PlagueState state, int cx, int cz) {
        PlagueGrid grid = state.grid();
        int i = grid.index(cx, cz);
        if (i < 0) return false;
        // Обратный проход при очистке — забота подсистемы очистителей.
        if (grid.getLevelAt(i) <= grid.getAppliedUndergroundAt(i)) return false;
        return ОЧЕРЕДЬ.enqueue(i);
    }

    public static int длинаОчереди() { return ОЧЕРЕДЬ.size(); }

    public static void сброситьОчередь() { ОЧЕРЕДЬ.clear(); }

    // ── работа ────────────────────────────────────────────────────────

    /** Один тик работы. Возвращает, сколько блоков реально изменено. */
    public static int отработатьТик(ServerLevel level, PlagueState state, int бюджет) {
        PlagueGrid grid = state.grid();
        long seed = level.getSeed();
        int изменено = 0;
        int столбцов = PlagueConstants.CAVE_COLUMNS_PER_TICK;
        boolean грязно = false;

        while (бюджет > 0 && столбцов > 0 && !ОЧЕРЕДЬ.isEmpty()) {
            int i = ОЧЕРЕДЬ.head();
            int cx = grid.chunkXOf(i);
            int cz = grid.chunkZOf(i);

            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {           // выгрузился — вернётся, когда игрок придёт снова
                ОЧЕРЕДЬ.finishHead();
                continue;
            }

            int уровень = grid.getLevelAt(i);
            if (уровень <= grid.getAppliedUndergroundAt(i)) {   // кто-то успел раньше
                ОЧЕРЕДЬ.finishHead();
                continue;
            }

            int столбец = ОЧЕРЕДЬ.cursor();
            while (столбец < СТОЛБЦОВ && бюджет > 0 && столбцов > 0) {
                int wx = (cx << 4) + (столбец & 15);
                int wz = (cz << 4) + (столбец >> 4);
                int сделано = пройтиСтолбец(level, chunk, seed, wx, wz, уровень, бюджет);
                бюджет -= сделано;
                изменено += сделано;
                столбцов--;
                столбец++;
            }

            if (столбец >= СТОЛБЦОВ) {
                grid.setAppliedUndergroundAt(i, уровень);
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
     * Столбец от дна мира до поверхности, снизу вверх.
     *
     * Идём по воздуху: пустых клеток под землёй мало, а именно они задают
     * всю границу. Для каждой клетки воздуха соседи снизу, сверху и с боков
     * дают пол, потолок и стены. Блок под нами уже прочитан на прошлом
     * витке, поэтому на клетку приходится одно чтение столбца плюс четыре
     * боковых — и только когда клетка пуста.
     */
    private static int пройтиСтолбец(ServerLevel level, LevelChunk chunk, long seed,
                                     int wx, int wz, int уровень, int бюджет) {
        int дно = level.getMinBuildHeight() + 1;              // над коренной породой
        // Верхние блоки столбца — вотчина поверхностного прохода,
        // отступаем от них, чтобы два прохода не спорили за одну клетку.
        int верх = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz)
                   - PlagueConstants.SURFACE_DEPTH - 2;
        if (верх <= дно) return 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int изменено = 0;

        pos.set(wx, дно - 1, wz);
        BlockState снизу = chunk.getBlockState(pos);

        for (int y = дно; y <= верх && изменено < бюджет; y++) {
            pos.set(wx, y, wz);
            BlockState тут = chunk.getBlockState(pos);
            BlockState предыдущий = снизу;
            снизу = тут;

            if (!тут.isAir()) continue;   // работаем только по полости

            pos.set(wx, y + 1, wz);
            BlockState сверху = chunk.getBlockState(pos);
            pos.set(wx, y, wz);

            изменено += обработатьПустоту(level, chunk, seed, wx, y, wz,
                                          уровень, предыдущий, сверху);
        }
        return изменено;
    }

    /**
     * Одна клетка воздуха и её соседи. Порядок проверок задаёт приоритет:
     * пол важнее потолка, потолок важнее стен — на клетку приходится
     * не больше одного изменения, иначе бюджет теряет смысл.
     */
    private static int обработатьПустоту(ServerLevel level, LevelChunk chunk, long seed,
                                         int wx, int y, int wz, int уровень,
                                         BlockState подНами, BlockState надНами) {
        float вес = MaterializationMask.blockWeight(seed, wx, y, wz);

        // ── пол ───────────────────────────────────────────────────────
        if (BlockTransforms.isCaveSubstrate(подНами)) {
            CaveSpot место = BlockTransforms.isOre(подНами) ? CaveSpot.ORE : CaveSpot.FLOOR;
            CaveAction действие = CaveRule.actionFor(место, уровень, вес);

            if (действие == CaveAction.SPORE_SAC && BlockTransforms.isCaveFloorMaterial(подНами)) {
                // Мешок сидит на гнилой земле: сперва пол, потом бугор на нём.
                level.setBlock(new BlockPos(wx, y - 1, wz), BlockTransforms.rottedDirt(), ФЛАГИ);
                level.setBlock(new BlockPos(wx, y, wz), BlockTransforms.sporeSac(), ФЛАГИ);
                return 2;
            }
            if (действие == CaveAction.ROTTED_DIRT && BlockTransforms.isCaveFloorMaterial(подНами)) {
                level.setBlock(new BlockPos(wx, y - 1, wz), BlockTransforms.rottedDirt(), ФЛАГИ);
                return 1;
            }
            if (действие == CaveAction.COAT_GROWTH) {
                return обрастить(level, new BlockPos(wx, y, wz), Direction.DOWN);
            }
        }

        // ── потолок ───────────────────────────────────────────────────
        if (BlockTransforms.isCaveSubstrate(надНами)) {
            CaveSpot место = BlockTransforms.isOre(надНами) ? CaveSpot.ORE : CaveSpot.CEILING;
            CaveAction действие = CaveRule.actionFor(место, уровень, вес);

            if (действие == CaveAction.HANG_VINE) {
                return повеситьЛозу(level, wx, y, wz, вес);
            }
            if (действие == CaveAction.COAT_GROWTH) {
                return обрастить(level, new BlockPos(wx, y, wz), Direction.UP);
            }
        }

        // ── стены ─────────────────────────────────────────────────────
        for (Direction бок : БОКА) {
            int nx = wx + бок.getStepX();
            int nz = wz + бок.getStepZ();
            // За край чанка не заглядываем: соседний чанк обработает свою
            // сторону сам, а level.getBlockState там мог бы его подгрузить.
            if ((nx >> 4) != (wx >> 4) || (nz >> 4) != (wz >> 4)) continue;

            BlockState сосед = chunk.getBlockState(new BlockPos(nx, y, nz));
            if (!BlockTransforms.isCaveSubstrate(сосед)) continue;

            CaveSpot место = BlockTransforms.isOre(сосед) ? CaveSpot.ORE : CaveSpot.WALL;
            if (CaveRule.actionFor(место, уровень, вес) == CaveAction.COAT_GROWTH) {
                return обрастить(level, new BlockPos(wx, y, wz), бок);
            }
        }

        return 0;
    }

    /**
     * Лоза свисает плетью, а не обрубком в один блок: длина от одного
     * до трёх, по тому же весу — значит, детерминирована.
     *
     * Идём вниз только по воздуху. Клетки под нами уже пройдены этим
     * же столбцом снизу вверх, и в какой-то из них могла лечь плёнка;
     * затирать её лозой не надо.
     */
    private static int повеситьЛозу(ServerLevel level, int wx, int y, int wz, float вес) {
        int длина = 1 + (int) (вес * ДЛИНА_ЛОЗЫ / PlagueConstants.CAVE_CEILING_VINES);
        длина = Math.min(длина, ДЛИНА_ЛОЗЫ);

        int повешено = 0;
        for (int звено = 0; звено < длина; звено++) {
            BlockPos место = new BlockPos(wx, y - звено, wz);
            if (!level.getBlockState(место).isAir()) break;
            level.setBlock(место, BlockTransforms.vine(), ФЛАГИ);
            повешено++;
        }
        return повешено;
    }

    /**
     * Плёнка кладётся в саму пустоту, гранью к блоку-опоре. Камень и руда
     * остаются на месте — их не заменяют, их обрастают.
     */
    private static int обрастить(ServerLevel level, BlockPos пустота, Direction кОпоре) {
        BlockState нарост = BlockTransforms.coating()
            .setValue(MultifaceBlock.getFaceProperty(кОпоре), true);
        level.setBlock(пустота, нарост, ФЛАГИ);
        return 1;
    }
}
