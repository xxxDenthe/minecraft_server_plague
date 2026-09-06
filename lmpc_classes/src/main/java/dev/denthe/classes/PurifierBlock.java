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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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
public class PurifierBlock extends BaseEntityBlock {

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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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

    @Override
    protected RenderShape getRenderShape(BlockState состояние) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level мир, BlockState состояние, BlockEntityType<T> тип) {
        return мир.isClientSide() ? null
            : createTickerHelper(тип, ClassBlockEntities.PURIFIER.get(), PurifierBlockEntity::тик);
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
        if (ключКузнеца(стопка)) {
            return форсаж(мир, позиция, игрок);
        }
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

    /** Гаечный ключ Create (или что стоит в конфиге) в руке. */
    private static boolean ключКузнеца(ItemStack стопка) {
        return ClassesConfig.ключиКузнеца().contains(
            BuiltInRegistries.ITEM.getKey(стопка.getItem()).toString());
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
    private static ItemInteractionResult форсаж(Level мир, BlockPos позиция, Player игрок) {
        if (мир.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель)) {
            return ItemInteractionResult.FAIL;
        }
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.SMITH) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.purifier.overdrive_smith_only"), true);
            return ItemInteractionResult.FAIL;
        }
        if (!очиститель.включитьФорсаж()) {
            игрок.displayClientMessage(
                Component.translatable("msg.lmpc_classes.purifier.overdrive_already"), true);
            return ItemInteractionResult.FAIL;
        }
        игрок.displayClientMessage(
            Component.translatable("msg.lmpc_classes.purifier.overdrive_on"), true);
        мир.playSound(null, позиция, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.6f);
        return ItemInteractionResult.SUCCESS;
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
    protected void onRemove(
            BlockState состояние, Level мир, BlockPos позиция, BlockState новое, boolean двигали) {
        if (!состояние.is(новое.getBlock())
                && мир.getBlockEntity(позиция) instanceof PurifierBlockEntity очиститель) {
            Block.popResource(мир, позиция, очиститель.вынутьВсё());
        }
        super.onRemove(состояние, мир, позиция, новое, двигали);
    }
}
