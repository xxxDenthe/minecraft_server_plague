package dev.denthe.classes;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Андезитовый очиститель поверхности. Спек ядра, раздел 10.1.
 *
 * Блок нарочно без меню-контейнера: реагент закладывается правым
 * кликом с реагентом в руке, состояние читается правым кликом пустой
 * рукой. Экран здесь ничего бы не добавил — внутри один слот, и ради
 * него заводить меню, сеть и клиентскую половину незачем.
 *
 * Работу делает {@link PurifierBlockEntity}; тут только взаимодействие
 * и возврат содержимого при сломе.
 */
public class PurifierBlock extends KineticBlock implements EntityBlock {

    public static final MapCodec<PurifierBlock> CODEC = simpleCodec(PurifierBlock::new);

    /**
     * Работает ли прямо сейчас — есть вращение и реагент. Берём ванильное
     * {@code lit}, а не заводим своё имя: свойство ровно с тем же смыслом
     * («машина включена»), и на нём же висит свечение блока, которое
     * прописано в {@link ClassBlocks}.
     */
    public static final BooleanProperty РАБОТАЕТ = BlockStateProperties.LIT;

    public PurifierBlock(Properties свойства) {
        super(свойства);
        registerDefaultState(stateDefinition.any().setValue(РАБОТАЕТ, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> строитель) {
        строитель.add(РАБОТАЕТ);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Ось вращения — вертикаль. Гнездо вала нарисовано на нижней грани,
     * и настоящий вал в него входит снизу; принимать вращение сбоку
     * значило бы, что модель врёт.
     */
    @Override
    public Direction.Axis getRotationAxis(BlockState состояние) {
        return Direction.Axis.Y;
    }

    /**
     * Вал цепляется только снизу — решение владельца («вариант А, только
     * снизу»). Сверху стоит сопло, и второе гнездо там отняло бы у него
     * место.
     */
    @Override
    public boolean hasShaftTowards(
            LevelReader мир, BlockPos позиция, BlockState состояние, Direction сторона) {
        return сторона == Direction.DOWN;
    }

    /**
     * Латунный ли это тир. Тир живёт в самом блоке, а не в поле сущности:
     * так его видно и там, где сущности ещё нет — например при постановке.
     */
    public static boolean латунный(BlockState состояние) {
        return состояние.is(ClassBlocks.BRASS_PURIFIER.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos позиция, BlockState состояние) {
        return new PurifierBlockEntity(позиция, состояние);
    }

    /**
     * Латунный очиститель ставит только Кузнец (спек ядра 10.1).
     * Возврат {@code null} отменяет постановку — блок не тратится.
     *
     * Проверка стоит на постановке, а не на крафте, тем же приёмом, что
     * у грядки Фермера и отвара Клирика: собрать вещь может кто угодно,
     * толк от неё — у класса. Иначе Кузнец без сборщиков не может дать
     * компании второй тир, даже когда всё для него добыто.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext контекст) {
        BlockState состояние = super.getStateForPlacement(контекст);
        if (состояние == null || !латунный(состояние)) return состояние;

        Player игрок = контекст.getPlayer();
        if (игрок == null
                || PlayerClassData.данные(игрок).класс == PlayerClassData.Класс.SMITH) {
            return состояние;
        }
        if (!контекст.getLevel().isClientSide()) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.purifier.smith_only"), true);
        }
        return null;
    }

    /**
     * Тикер нужен на обеих сторонах, а не только на серверной, как было
     * до 0.15.0: {@code SmartBlockEntity} Create тикает и на клиенте —
     * этим он копит угол, под которым рисуется вал. Своей работы
     * очиститель на клиенте по-прежнему не делает.
     *
     * {@code createTickerHelper} остался в {@code BaseEntityBlock},
     * от которого мы больше не наследуемся, поэтому проверка типа —
     * своя, и она же делает приведение безопасным.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level мир, BlockState состояние, BlockEntityType<T> тип) {
        if (тип != ClassBlockEntities.PURIFIER.get()) return null;
        return (уровень, позиция, сост, сущность) -> ((PurifierBlockEntity) сущность).tick();
    }

    /**
     * Сравнитель рядом показывает остаток реагента: 15 — полная стопка,
     * 0 — пусто. Дешёвый способ повесить на базе лампу «заряд кончается»
     * или включить автодозагрузку с ленты, ничего для этого не написав.
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState состояние) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState состояние, Level мир, BlockPos позиция) {
        if (!(мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель)) return 0;
        int реагента = очиститель.реагентаВнутри();
        return реагента <= 0 ? 0 : Math.max(1, реагента * 15 / 64);
    }

    /** Реагент в руке — заложить внутрь; всё прочее пропускаем дальше. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack стопка, BlockState состояние, Level мир, BlockPos позиция,
            Player игрок, InteractionHand рука, BlockHitResult попадание) {
        if (!стопка.is(ClassItems.CLEANSING_AGENT.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (мир.isClientSide()) return ItemInteractionResult.SUCCESS;

        if (!(мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель)) {
            return ItemInteractionResult.FAIL;
        }
        int взято = очиститель.принятьРеагент(стопка);
        игрок.displayClientMessage(очиститель.состояние(мир, позиция), true);
        return взято > 0 ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
    }

    /**
     * Гаечный ключ Create по очистителю — Форсаж Кузнеца. Приседать при
     * этом не надо: присед с ключом остаётся крейтовским, то есть
     * разбирает блок в инвентарь ({@code onSneakWrenched} не тронут).
     *
     * До 0.15.0 ключ ловился в {@code useItemOn} по списку предметов
     * из конфига, потому что жёсткой зависимости на Create не было.
     * Теперь блок — настоящий {@code IWrenchable}, и ключ приходит
     * сюда сам, каким бы он ни был.
     *
     * Владелец знает, что случайный клик стоит четырёхкратного реагента,
     * и принял это.
     */
    @Override
    public InteractionResult onWrenched(BlockState состояние, UseOnContext контекст) {
        return форсаж(контекст.getLevel(), контекст.getClickedPos(), контекст.getPlayer());
    }

    /**
     * Форсаж — активка Кузнеца и ответ на открытый вопрос спека классов
     * (раздел 13): «активка Кузнеца точно ноль?». Была ноль: вся сила
     * класса выражалась постройками, и играть Кузнецом означало ждать.
     *
     * Теперь Кузнец бьёт ключом по своему очистителю и разово выжимает
     * из него ночь по площади тира выше — вчетверо дороже по реагенту.
     * Ни новой сущности, ни нового блока: жест, цена и одна ночь.
     */
    private static InteractionResult форсаж(Level мир, BlockPos позиция, Player игрок) {
        if (игрок == null) return InteractionResult.PASS;
        if (мир.isClientSide()) return InteractionResult.SUCCESS;
        if (!(мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель)) {
            return InteractionResult.FAIL;
        }
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.SMITH) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.purifier.overdrive_smith_only"), true);
            return InteractionResult.FAIL;
        }
        if (!очиститель.включитьФорсаж()) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.purifier.overdrive_already"), true);
            return InteractionResult.FAIL;
        }
        игрок.displayClientMessage(
            Component.translatable("msg.lmpc_classes.purifier.overdrive_on"), true);
        мир.playSound(null, позиция, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    /** Пустая рука — доклад о состоянии: есть ли вращение, реагент и Кузнец в партии. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState состояние, Level мир, BlockPos позиция, Player игрок, BlockHitResult попадание) {
        if (!мир.isClientSide() && мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель) {
            игрок.displayClientMessage(очиститель.состояние(мир, позиция), false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Реагент внутри — не расходник блока, а вложение партии. Тихо
     * съедать его при сломе значило бы наказывать за перестройку
     * обороны, чего спек как раз не хочет: оборону положено двигать
     * вперёд, а не ставить один раз навсегда.
     */
    @Override
    public void onRemove(
            BlockState состояние, Level мир, BlockPos позиция, BlockState новое, boolean двигали) {
        if (!состояние.is(новое.getBlock())
                && мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель) {
            Block.popResource(мир, позиция, очиститель.вынутьВсё());
        }
        super.onRemove(состояние, мир, позиция, новое, двигали);
    }
}
