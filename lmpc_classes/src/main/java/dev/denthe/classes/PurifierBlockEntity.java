package dev.denthe.classes;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.lang.LangBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Андезитовый очиститель поверхности — первый тир из спека ядра,
 * раздел 10.1. Раз за ночь, если запитан вращением и есть реагент,
 * поднимает сопротивление чанков вокруг и снимает с них уровни заражения.
 *
 * <p><b>Площадь и скорость — ручки конфига.</b> {@code purifierRadiusChunks}
 * задаёт квадрат вокруг блока, {@code purifierLevelsPerNight} — сколько
 * попыток снять уровень делается по каждому чанку за ночь. Обе появились
 * после живой проверки: с одним чанком и одной попыткой очиститель ровно
 * отыгрывал назад ночной рост заражения и уровень застывал на месте, а
 * вычищенный одиночный чанк всё равно выглядел гнилым — вид берётся по
 * максимуму соседей. Разбор — заметка
 * {@code 2026-09-06-ochistitel-oblast-i-skorost.md}.
 *
 * <p><b>Почему первый тир доступен всем.</b> Это прямое требование
 * спека: если половина компании не разбирается в Create, она не должна
 * выпадать из защиты от чумы. Классовая проверка стоит только
 * на верхних тирах, которых пока нет.
 *
 * <p><b>Где здесь класс.</b> Сила очистителя читает <i>мастерство
 * партии</i> ({@link ClassParty}), а не того, кто его поставил: тир
 * лучшего Кузнеца среди сейчас играющих. Без Кузнеца очиститель
 * работает, но слабее — это и есть «проседает до уровня, доступного
 * без класса» из спека классов, раздел 2.1. Обратной стороной Кузнец
 * получает мастерство за каждую удачную ночную очистку: класс растёт
 * от того же, ради чего существует.
 *
 * <p>Счётчика тиков нет: работа привязана к номеру ночи из
 * `plaguecore`, поэтому переживает перезапуск сервера и выгрузку
 * чанка — очиститель не «пропускает» ночь, а видит, что её номер
 * сменился.
 */
public class PurifierBlockEntity extends KineticBlockEntity {

    /** Проверяем условия раз в секунду: ночь наступает не чаще. */
    private static final int ИНТЕРВАЛ = 20;

    /**
     * Ритмы эффектов, в тиках.
     *
     * Гул взят с маяка и посажен на его же ритм в 80 тиков: файл
     * длинный, и на любом ритме чаще он наслаивается сам на себя
     * в гудящую кашу. Бульканье сдвинуто на полпериода от гула
     * (см. {@link #эффекты}) — иначе два звука бьют в один тик
     * и слышны как один щелчок.
     */
    private static final int ЧАСТОТА_БРЫЗГ = 5;
    private static final int ЧАСТОТА_ГУЛА = 80;
    private static final int ЧАСТОТА_ПАРА = 10;
    private static final int ЧАСТОТА_ИСКР = 20;

    /**
     * Скорость Create, на которой очиститель считается разогнанным
     * до предела: сильнее этого брызги и тон гула уже не растут.
     * 256 — потолок самого Create («слишком быстро» начинается выше).
     */
    private static final float СКОРОСТЬ_ПОТОЛОК = 256f;

    private static final int КАПЕЛЬ_МИНИМУМ = 2;
    private static final int КАПЕЛЬ_МАКСИМУМ = 7;

    private static final String КЛЮЧ_РЕАГЕНТ = "Reagent";
    private static final String КЛЮЧ_НОЧЬ = "LastNight";
    private static final String КЛЮЧ_ФОРСАЖ = "Overdrive";
    private static final String КЛЮЧ_ТИР = "SmithTier";

    private ItemStack реагент = ItemStack.EMPTY;

    /** Номер ночи, за которую уже отработали. -1 — ещё ни разу. */
    private int последняяНочь = -1;

    /**
     * Крутится ли и есть ли чем чистить. Пересчитывается раз в секунду
     * вместе с ночным шагом, а читается каждый тик ради брызг и гула:
     * спрашивать Create о скорости шестьдесят раз в секунду незачем.
     *
     * В NBT не пишется: после перезагрузки досчитается за секунду сам.
     */
    private boolean работает = false;

    /**
     * Скорость соседнего вала на последнем пересчёте. Нужна не для
     * работы — она пороговая, — а для вида: чем быстрее крутится, тем
     * гуще брызги и выше тон гула. В NBT, как и {@link #работает},
     * не пишется.
     */
    private float скорость = 0f;

    /**
     * Уровень заражения своего чанка на последнем пересчёте — только
     * ради вида столбика над крышкой. В NBT не пишется: чума знает
     * это лучше нас, спросим заново через секунду.
     */
    private int заражение = 0;

    /**
     * Тир Кузнеца в партии на последнем пересчёте — только ради очков
     * инженера. В NBT пишется и уезжает на клиент, потому что панель
     * очков рисуется на клиенте, а списка игроков и их мастерства там
     * нет. Работа очистителя это поле не читает: ночной шаг спрашивает
     * {@link ClassParty} заново и на сервере.
     */
    private int тирПартии = 0;

    /**
     * Что о реагенте и тире уже уехало на клиент. Пакет шлём только
     * при смене: сам по себе очиститель молчалив, а очки инженера
     * терпят задержку до секунды.
     */
    private int показанныйРеагент = -1;

    /**
     * Форсаж Кузнеца: ближайшая ночь отрабатывается по площади тира
     * выше и вчетверо дороже по реагенту. Одноразовый — гаснет сам
     * в ту же ночь, ради которой включён.
     *
     * В NBT пишется, в отличие от {@link #работает}: игрок мог включить
     * его вечером и выйти из игры, и «потерять» оплаченный форсаж
     * из-за перезапуска сервера он не должен.
     */
    private boolean форсаж = false;

    /**
     * Утренний отчёт копится на все очистители разом и уходит одной
     * пачкой строк. Поля статические нарочно: иначе владелец десяти
     * блоков получал бы десять одинаковых сообщений каждое утро.
     *
     * <p><b>Почему с задержкой, а не сразу.</b> Очистители просыпаются
     * вразнобой — каждый в своём чанке и в свою секунду, — поэтому
     * первый проснувшийся не знает, что сделали остальные. Отчёт
     * отправляется через {@link #ЗАДЕРЖКА_ОТЧЁТА} тиков после
     * последнего отработавшего блока, и тогда в нём уже вся ночь.
     *
     * <p><b>И почему не только на удачу.</b> Раньше строка уходила
     * лишь когда бросок снял уровень, а очистка — вероятность: в
     * неудачную ночь Кузнец не получал ничего и решал, что блок сломан.
     * Теперь отчёт приходит за каждую отработанную ночь, а вышло или
     * нет — сказано в нём словами.
     *
     * ponytail: живёт только до перезапуска сервера. Худшее при сбросе —
     * один потерянный отчёт за ночь.
     */
    private static final int ЗАДЕРЖКА_ОТЧЁТА = 60;

    private static int отчётНочь = -1;
    private static long отчётСрок = 0;
    private static int отчётВсего = 0;
    private static int отчётУдачных = 0;
    private static int отчётОпустело = 0;
    private static boolean отчётФорсаж = false;

    /**
     * Приёмник реагента для воронок, лент и рук Create. Только на вход:
     * очиститель — не сундук, и вытаскивать из него заряд соседней
     * воронкой было бы способом обокрасть чужую оборону.
     */
    private final IItemHandler ворота = new IItemHandler() {
        @Override public int getSlots() { return 1; }

        @Override public ItemStack getStackInSlot(int слот) { return реагент; }

        @Override public int getSlotLimit(int слот) { return 64; }

        @Override public boolean isItemValid(int слот, ItemStack стопка) {
            return стопка.is(ClassItems.CLEANSING_AGENT.get());
        }

        @Override public ItemStack insertItem(int слот, ItemStack стопка, boolean примерка) {
            if (!isItemValid(слот, стопка)) return стопка;
            int влезет = Math.min(стопка.getCount(),
                стопка.getMaxStackSize() - реагент.getCount());
            if (влезет <= 0) return стопка;
            if (!примерка) {
                if (реагент.isEmpty()) реагент = стопка.copyWithCount(влезет);
                else реагент.grow(влезет);
                setChanged();
                сообщитьСравнителю();
            }
            return стопка.getCount() == влезет ? ItemStack.EMPTY
                : стопка.copyWithCount(стопка.getCount() - влезет);
        }

        @Override public ItemStack extractItem(int слот, int сколько, boolean примерка) {
            return ItemStack.EMPTY;
        }
    };

    /** Приёмник для {@link ClassCapabilities}: воронка, лента, рука Create. */
    public IItemHandler ворота() {
        return ворота;
    }

    /** Сравнителю рядом — новый остаток реагента. */
    private void сообщитьСравнителю() {
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    /** Сколько реагента внутри — для сигнала сравнителю. */
    public int реагентаВнутри() {
        return реагент.getCount();
    }

    /**
     * Форсаж на ближайшую ночь. Возвращает false, если он уже включён:
     * второй ключ подряд не должен молча съедать ничего.
     */
    public boolean включитьФорсаж() {
        if (форсаж) return false;
        форсаж = true;
        setChanged();
        return true;
    }

    public boolean форсажВключён() {
        return форсаж;
    }

    public PurifierBlockEntity(BlockPos позиция, BlockState состояние) {
        super(ClassBlockEntities.PURIFIER.get(), позиция, состояние);
    }

    /**
     * Поведений Create у очистителя нет: ни фильтра, ни воронки, ни
     * скроллящихся значений. Метод абстрактный в {@code SmartBlockEntity},
     * поэтому пустая реализация обязательна.
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> поведения) {
    }

    /**
     * Нагрузка на кинетическую сеть. Именно она делает спековые «64 SU
     * на штуку» (ядро, 10.1) настоящим ограничением: до 0.15.0 очиститель
     * скорость только читал, и одно водяное колесо крутило их сколько
     * угодно.
     *
     * Число берётся из конфига и пишется в {@code lastStressApplied} —
     * так требует Create: поле читают очки инженера и оверлей сети.
     * Через конфиг самого Create его завести нельзя, {@code CStress}
     * отказывается принимать блоки чужих модов.
     */
    @Override
    public float calculateStressApplied() {
        float нагрузка = ClassesConfig.очистительСтресс(латунный());
        this.lastStressApplied = нагрузка;
        return нагрузка;
    }

    /**
     * Тик идёт на обеих сторонах: клиентская половина нужна самому
     * Create — он крутит вал по накопленному углу. Наша работа целиком
     * серверная, поэтому {@link #тик} сам отсеивает клиент.
     */
    @Override
    public void tick() {
        super.tick();
        тик(level, getBlockPos(), getBlockState(), this);
    }

    public ItemStack реагент() {
        return реагент;
    }

    /** Сколько реагента влезло; остаток остаётся у игрока. */
    public int принятьРеагент(ItemStack откуда) {
        int вместимость = откуда.getMaxStackSize();
        if (реагент.isEmpty()) {
            int взять = Math.min(откуда.getCount(), вместимость);
            реагент = откуда.split(взять);
            setChanged();
            сообщитьСравнителю();
            return взять;
        }
        if (!ItemStack.isSameItemSameComponents(реагент, откуда)) return 0;

        int взять = Math.min(откуда.getCount(), вместимость - реагент.getCount());
        if (взять <= 0) return 0;
        реагент.grow(взять);
        откуда.shrink(взять);
        setChanged();
        сообщитьСравнителю();
        return взять;
    }

    /** Всё, что лежит внутри, — чтобы вернуть игроку при сломе блока. */
    public ItemStack вынутьВсё() {
        ItemStack всё = реагент;
        реагент = ItemStack.EMPTY;
        setChanged();
        return всё;
    }

    /**
     * Латунный ли это тир. Спрашиваем блок, а не своё поле: тир задан
     * тем, что стоит в мире, и в NBT его хранить незачем.
     */
    private boolean латунный() {
        return PurifierBlock.латунный(getBlockState());
    }

    /** Строка состояния для правого клика пустой рукой. */
    public Component состояние(Level мир, BlockPos позиция) {
        float скорость = Math.abs(getSpeed());
        if (скорость < ClassesConfig.очистительМинСкорость(латунный())) {
            return Component.translatable("msg.lmpc_classes.purifier.no_power");
        }
        if (реагент.isEmpty()) {
            return Component.translatable("msg.lmpc_classes.purifier.no_reagent");
        }
        int тир = мир.getServer() == null
            ? 0 : ClassParty.тир(мир.getServer(), PlayerClassData.Класс.SMITH);
        return Component.translatable("msg.lmpc_classes.purifier.working",
            реагент.getCount(), тир == 0
                ? Component.translatable("msg.lmpc_classes.purifier.no_smith")
                : Component.literal(ClassMastery.римская(тир)));
    }

    /**
     * Панель очков инженера. Скорость и нагрузку {@link KineticBlockEntity}
     * рисует сам — дописываем только то, что знает один очиститель:
     * остаток реагента, тир Кузнеца в партии и хватает ли оборотов
     * на ночную работу.
     *
     * <p>Метод целиком клиентский, поэтому спрашивать сервер тут нельзя:
     * реагент и тир приезжают в NBT (см. {@link #тик}), скорость Create
     * синхронизирует сам, а порог скорости лежит в общем конфиге,
     * который есть и на клиенте.
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> подсказка, boolean приседает) {
        super.addToGoggleTooltip(подсказка, приседает);

        float нужно = ClassesConfig.очистительМинСкорость(латунный());
        boolean хватает = Math.abs(getSpeed()) >= нужно;

        строка().translate("gui.goggles.purifier").forGoggles(подсказка);

        (реагент.isEmpty()
            ? строка().translate("gui.goggles.purifier.no_reagent").style(ChatFormatting.RED)
            : строка().translate("gui.goggles.purifier.reagent", реагент.getCount())
                .style(ChatFormatting.AQUA))
            .forGoggles(подсказка, 1);

        (тирПартии == 0
            ? строка().translate("gui.goggles.purifier.no_smith").style(ChatFormatting.GRAY)
            : строка().translate("gui.goggles.purifier.smith", ClassMastery.римская(тирПартии))
                .style(ChatFormatting.AQUA))
            .forGoggles(подсказка, 1);

        (хватает
            ? строка().translate("gui.goggles.purifier.speed_ok").style(ChatFormatting.GREEN)
            : строка().translate("gui.goggles.purifier.speed_low", (int) нужно)
                .style(ChatFormatting.RED))
            .forGoggles(подсказка, 1);

        return true;
    }

    /**
     * Строка перевода в нашем пространстве имён: {@code CreateLang}
     * умеет только ключи самого Create.
     */
    private static LangBuilder строка() {
        return new LangBuilder(LmpcClasses.MODID);
    }

    public static void тик(Level мир, BlockPos позиция, BlockState состояние, PurifierBlockEntity сам) {
        if (!(мир instanceof ServerLevel уровень)) return;

        if (уровень.getGameTime() % ИНТЕРВАЛ == 0) {
            сам.скорость = Math.abs(сам.getSpeed());
            сам.работает = !сам.реагент.isEmpty()
                && сам.скорость
                   >= ClassesConfig.очистительМинСкорость(PurifierBlock.латунный(состояние));

            // Свечение и «включённый» вид — то же поле, что и эффекты,
            // поэтому состояние переставляется здесь же, раз в секунду
            // и только при смене. UPDATE_CLIENTS, а не UPDATE_ALL:
            // соседям от нашего огонька ничего не нужно, а блок-энтити
            // при смене свойства того же блока переживает подмену.
            if (состояние.getValue(PurifierBlock.РАБОТАЕТ) != сам.работает) {
                уровень.setBlock(позиция,
                    состояние.setValue(PurifierBlock.РАБОТАЕТ, сам.работает),
                    Block.UPDATE_CLIENTS);
            }
            сам.заражение = PlagueBridge.уровеньЧанка(
                уровень, позиция.getX() >> 4, позиция.getZ() >> 4);

            // Очки инженера: пакет уходит только когда показывать стало
            // что-то другое, а не шестьдесят раз в секунду.
            int тир = ClassParty.тир(уровень.getServer(), PlayerClassData.Класс.SMITH);
            if (тир != сам.тирПартии || сам.показанныйРеагент != сам.реагент.getCount()) {
                сам.тирПартии = тир;
                сам.показанныйРеагент = сам.реагент.getCount();
                сам.notifyUpdate();
            }
            ночнойШаг(уровень, позиция, сам);
            сдатьОтчёт(уровень);
        }
        if (сам.работает) эффекты(уровень, позиция, сам.скорость, сам.заражение);
    }

    /**
     * Брызги и гул — единственный признак, что очиститель жив: работает
     * он раз за ночь, а стоять рядом с молчащим ящиком игрок будет
     * весь день.
     *
     * Всё шлём с сервера: клиентского тика у блока нет, а
     * {@code sendParticles} и {@code playSound} сами рассылаются всем,
     * кто рядом. Отдельная клиентская половина ради этого не нужна.
     */
    private static void эффекты(
            ServerLevel уровень, BlockPos позиция, float скорость, int заражение) {
        long время = уровень.getGameTime();
        RandomSource случай = уровень.random;
        double x = позиция.getX() + 0.5;
        double y = позиция.getY();
        double z = позиция.getZ() + 0.5;

        // 0 на холостом ходу, 1 на потолке Create. Одна доля крутит
        // и густоту брызг, и тон гула — машина «раскручивается»
        // на слух и на глаз разом.
        float разгон = Math.min(скорость / СКОРОСТЬ_ПОТОЛОК, 1f);

        if (время % ЧАСТОТА_ГУЛА == 0) {
            уровень.playSound(null, позиция, SoundEvents.BEACON_AMBIENT,
                SoundSource.BLOCKS, 0.12f, 0.5f + разгон * 0.25f);
        }
        // Полпериода от гула: два звука в одном тике сливаются в щелчок.
        if (время % ЧАСТОТА_ГУЛА == ЧАСТОТА_ГУЛА / 2) {
            уровень.playSound(null, позиция, SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 0.16f, 0.75f + разгон * 0.2f);
        }

        // Столбик над крышкой заодно докладывает, как идут дела в своём
        // чанке: белые искры — вычищено, облако — работа идёт, дым —
        // земля ещё гнилая. Это ответ на главную жалобу живой проверки
        // («поставил и не понял, работает ли») — видно через полбазы
        // и без единого клика.
        if (время % ЧАСТОТА_ПАРА == 0) {
            ParticleOptions столбик = заражение >= 3 ? ParticleTypes.SMOKE
                : заражение >= 1 ? ParticleTypes.CLOUD
                : ParticleTypes.END_ROD;
            уровень.sendParticles(столбик,
                x + (случай.nextDouble() - 0.5) * 0.4, y + 1.1,
                z + (случай.nextDouble() - 0.5) * 0.4,
                заражение >= 3 ? 2 : 1, 0.0, 0.02, 0.0, 0.01);
        }

        // Искра по ободу — «чистящая» блёстка от снятого воска, самая
        // подходящая ванильная частица под смысл блока.
        if (время % ЧАСТОТА_ИСКР == 0) {
            double угол = случай.nextDouble() * Math.PI * 2;
            уровень.sendParticles(ParticleTypes.WAX_OFF,
                x + Math.cos(угол) * 0.45, y + 0.95, z + Math.sin(угол) * 0.45,
                1, 0.0, 0.0, 0.0, 0.0);
        }

        if (время % ЧАСТОТА_БРЫЗГ != 0) return;

        int капель = КАПЕЛЬ_МИНИМУМ
            + Math.round((КАПЕЛЬ_МАКСИМУМ - КАПЕЛЬ_МИНИМУМ) * разгон);
        for (int i = 0; i < капель; i++) {
            double угол = случай.nextDouble() * Math.PI * 2;
            double разлёт = 0.08 + случай.nextDouble() * 0.07 + разгон * 0.10;
            // Количество ноль — тогда три числа читаются не как разброс
            // места, а как скорость. Ванильная капля берёт нашу скорость
            // по горизонтали только при нулевой вертикальной, вверх она
            // подбрасывает себя сама. Отсюда и ноль посередине.
            уровень.sendParticles(ParticleTypes.SPLASH,
                x, y + 1.05, z,
                0, Math.cos(угол), 0.0, Math.sin(угол), разлёт);
        }
    }

    /**
     * Ночной всплеск — единственный момент, когда очиститель делает
     * работу, а не изображает её. Без него игрок никогда не видит
     * главного события блока: оно случается раз за ночь и длится ноль
     * тиков.
     *
     * Кольцо рисуется по краю реально обработанной области, чтобы
     * «докуда достаёт» читалось глазами, а не только из конфига.
     */
    private static void ночнойВсплеск(
            ServerLevel уровень, BlockPos позиция, int радиусЧанков, boolean снизился) {
        double x = позиция.getX() + 0.5;
        double y = позиция.getY() + 1.0;
        double z = позиция.getZ() + 0.5;
        double радиус = 8.0 + радиусЧанков * 16.0;

        int точек = 64;
        for (int i = 0; i < точек; i++) {
            double угол = i * Math.PI * 2 / точек;
            уровень.sendParticles(ParticleTypes.END_ROD,
                x + Math.cos(угол) * радиус, y, z + Math.sin(угол) * радиус,
                1, 0.0, 0.03, 0.0, 0.0);
        }
        уровень.playSound(null, позиция, SoundEvents.BEACON_ACTIVATE,
            SoundSource.BLOCKS, 0.5f, снизился ? 1.4f : 1.0f);

        if (!снизился) return;
        // Удачная ночь звучит и выглядит отдельно: столб вверх и звон,
        // иначе «сработал» и «сработал впустую» на вид одинаковы.
        уровень.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z,
            30, 0.15, 0.3, 0.15, 0.08);
        уровень.playSound(null, позиция, SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.BLOCKS, 0.7f, 0.9f);
    }

    /** Ночная работа: раз за ночь, независимо от того, вышло или нет. */
    private static void ночнойШаг(ServerLevel уровень, BlockPos позиция, PurifierBlockEntity сам) {
        int ночь = PlagueBridge.ночь(уровень);
        if (ночь < 0 || ночь == сам.последняяНочь) return;

        // Первая встреча с миром — не работаем задним числом за все
        // прошедшие ночи, просто запоминаем, где сейчас находимся.
        if (сам.последняяНочь < 0) {
            сам.последняяНочь = ночь;
            сам.setChanged();
            return;
        }
        сам.последняяНочь = ночь;
        сам.setChanged();

        if (!сам.работает) return;

        boolean латунный = сам.латунный();
        boolean форсаж = сам.форсаж;
        сам.форсаж = false;

        int тир = ClassParty.тир(уровень.getServer(), PlayerClassData.Класс.SMITH);
        // Скорость вала больше не только порог: разогнанный очиститель
        // чистит сильнее. Вероятность всё равно вероятность, поэтому
        // потолок в единицу — иначе выше 100 % «шанса» считать нечего.
        float сила = Math.min(1f, ClassesConfig.очистительСила(тир)
            * ClassesConfig.очистительМножительСкорости(сам.скорость, латунный));
        float сопротивление = ClassesConfig.очистительСопротивление();
        int радиус = ClassesConfig.очистительРадиус(латунный)
            + (форсаж ? ClassesConfig.форсажРадиус() : 0);
        int попыток = ClassesConfig.очистительПопыток();
        int чанкX = позиция.getX() >> 4;
        int чанкZ = позиция.getZ() >> 4;

        // Квадрат, а не круг: сетка чумы квадратная, и круг на радиусе 1
        // отличался бы от квадрата только четырьмя углами.
        boolean снизился = false;
        for (int dx = -радиус; dx <= радиус; dx++) {
            for (int dz = -радиус; dz <= радиус; dz++) {
                for (int попытка = 0; попытка < попыток; попытка++) {
                    // Сопротивление подсыпаем один раз за ночь: это защита
                    // от нового заражения, а не второй способ снимать уровни.
                    float прирост = попытка == 0 ? сопротивление : 0f;
                    снизился |= PlagueBridge.очиститьЧанк(
                        уровень, чанкX + dx, чанкZ + dz, сила, прирост);
                }
            }
        }

        // Реагент по-прежнему один за ночь, а не по чанку: иначе площадь
        // 3 × 3 съедала бы стопку за неделю, и очиститель превращался бы
        // в кормушку. ponytail: плоская цена, вводить цену за чанк,
        // если реагент окажется слишком дешёвым.
        // Расход тира: андезитовый съедает один реагент за ночь, латунный —
        // четыре (спек 10.1). Если внутри осталось меньше, забираем сколько
        // есть: работа уже сделана, а недодавать за неё нечем.
        int расход = ClassesConfig.очистительРасход(латунный)
            * (форсаж ? ClassesConfig.форсажРасход() : 1);
        сам.реагент.shrink(Math.min(расход, сам.реагент.getCount()));
        сам.setChanged();
        сам.сообщитьСравнителю();

        ночнойВсплеск(уровень, позиция, радиус, снизился);

        // Последний реагент ушёл — очиститель до утра встанет, и об этом
        // надо сказать хлопком, а не тишиной: молчащий блок игрок
        // замечает через сутки, когда заражение уже вернулось.
        if (сам.реагент.isEmpty()) {
            сам.работает = false;
            уровень.playSound(null, позиция, SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.5f, 1.2f);
            уровень.sendParticles(ParticleTypes.SMOKE,
                позиция.getX() + 0.5, позиция.getY() + 1.1, позиция.getZ() + 0.5,
                12, 0.15, 0.05, 0.15, 0.02);
        }

        if (снизился) {
            ServerPlayer кузнец = ClassParty.лучший(уровень.getServer(), PlayerClassData.Класс.SMITH);
            if (кузнец != null) {
                PlayerClassData.прибавитьМастерство(кузнец, ClassesConfig.кузнецМастерствоЗаОчистку());
            }
        }
        скопитьОтчёт(уровень, ночь, снизился, форсаж, сам.реагент.isEmpty());
    }

    /** Записать в утренний отчёт, что сделал за ночь один очиститель. */
    private static void скопитьОтчёт(ServerLevel уровень, int ночь,
            boolean снизился, boolean форсаж, boolean опустел) {
        if (отчётНочь != ночь) {
            отчётНочь = ночь;
            отчётВсего = 0;
            отчётУдачных = 0;
            отчётОпустело = 0;
            отчётФорсаж = false;
        }
        отчётСрок = уровень.getGameTime() + ЗАДЕРЖКА_ОТЧЁТА;
        отчётВсего++;
        if (снизился) отчётУдачных++;
        if (опустел) отчётОпустело++;
        отчётФорсаж |= форсаж;
    }

    /**
     * Отдать накопленный отчёт Кузнецу — заголовок, итог ночи и, если
     * есть, отдельная строка про кончившийся реагент. Цвета живут здесь,
     * а текст в языковых файлах: править лор владельцу, не пересобирая мод.
     */
    private static void сдатьОтчёт(ServerLevel уровень) {
        if (отчётНочь < 0 || уровень.getGameTime() < отчётСрок) return;

        int всего = отчётВсего;
        int удачных = отчётУдачных;
        int опустело = отчётОпустело;
        boolean форсаж = отчётФорсаж;
        отчётНочь = -1;

        ServerPlayer кузнец = ClassParty.лучший(уровень.getServer(), PlayerClassData.Класс.SMITH);
        if (кузнец == null) return;

        кузнец.sendSystemMessage(Component.translatable(форсаж
                ? "msg.lmpc_classes.purifier.dawn.header_overdrive"
                : "msg.lmpc_classes.purifier.dawn.header")
            .withStyle(стиль -> стиль.withColor(ChatFormatting.GOLD).withBold(true)));
        кузнец.sendSystemMessage(удачных > 0
            ? Component.translatable("msg.lmpc_classes.purifier.dawn.cleansed", удачных, всего)
                .withStyle(ChatFormatting.GREEN)
            : Component.translatable("msg.lmpc_classes.purifier.dawn.held", всего)
                .withStyle(ChatFormatting.GRAY));
        if (опустело > 0) {
            кузнец.sendSystemMessage(
                Component.translatable("msg.lmpc_classes.purifier.dawn.empty", опустело)
                    .withStyle(ChatFormatting.RED));
        }
        кузнец.playNotifySound(удачных > 0
                ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.FIRE_EXTINGUISH,
            SoundSource.PLAYERS, 0.7f, удачных > 0 ? 1.2f : 1.0f);
    }

    /**
     * Своё в NBT пишется через {@code write}/{@code read} Create, а не
     * через {@code saveAdditional}: у {@code SmartBlockEntity} тот объявлен
     * final и сам зовёт эту пару. Флаг {@code клиентскийПакет} различает
     * запись в сейв и рассылку на клиент; нам различать нечего — реагент
     * и ночь нужны и там, и там.
     */
    @Override
    protected void write(CompoundTag тег, HolderLookup.Provider реестры, boolean клиентскийПакет) {
        super.write(тег, реестры, клиентскийПакет);
        тег.putInt(КЛЮЧ_НОЧЬ, последняяНочь);
        if (форсаж) тег.putBoolean(КЛЮЧ_ФОРСАЖ, true);
        if (тирПартии > 0) тег.putInt(КЛЮЧ_ТИР, тирПартии);
        if (!реагент.isEmpty()) тег.put(КЛЮЧ_РЕАГЕНТ, реагент.save(реестры));
    }

    @Override
    protected void read(CompoundTag тег, HolderLookup.Provider реестры, boolean клиентскийПакет) {
        super.read(тег, реестры, клиентскийПакет);
        последняяНочь = тег.contains(КЛЮЧ_НОЧЬ) ? тег.getInt(КЛЮЧ_НОЧЬ) : -1;
        форсаж = тег.getBoolean(КЛЮЧ_ФОРСАЖ);
        тирПартии = тег.getInt(КЛЮЧ_ТИР);
        реагент = тег.contains(КЛЮЧ_РЕАГЕНТ)
            ? ItemStack.parse(реестры, тег.getCompound(КЛЮЧ_РЕАГЕНТ)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    }
}
