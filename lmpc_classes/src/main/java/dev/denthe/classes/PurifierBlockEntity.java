package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
public class PurifierBlockEntity extends BlockEntity {

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

    public PurifierBlockEntity(BlockPos позиция, BlockState состояние) {
        super(ClassBlockEntities.PURIFIER.get(), позиция, состояние);
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
            return взять;
        }
        if (!ItemStack.isSameItemSameComponents(реагент, откуда)) return 0;

        int взять = Math.min(откуда.getCount(), вместимость - реагент.getCount());
        if (взять <= 0) return 0;
        реагент.grow(взять);
        откуда.shrink(взять);
        setChanged();
        return взять;
    }

    /** Всё, что лежит внутри, — чтобы вернуть игроку при сломе блока. */
    public ItemStack вынутьВсё() {
        ItemStack всё = реагент;
        реагент = ItemStack.EMPTY;
        setChanged();
        return всё;
    }

    /** Строка состояния для правого клика пустой рукой. */
    public Component состояние(Level мир, BlockPos позиция) {
        float скорость = CreateBridge.скоростьРядом(мир, позиция);
        if (скорость < ClassesConfig.очистительМинСкорость()) {
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

    public static void тик(Level мир, BlockPos позиция, BlockState состояние, PurifierBlockEntity сам) {
        if (!(мир instanceof ServerLevel уровень)) return;

        if (уровень.getGameTime() % ИНТЕРВАЛ == 0) {
            сам.скорость = CreateBridge.скоростьРядом(уровень, позиция);
            сам.работает = !сам.реагент.isEmpty()
                && сам.скорость >= ClassesConfig.очистительМинСкорость();

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
            ночнойШаг(уровень, позиция, сам);
        }
        if (сам.работает) эффекты(уровень, позиция, сам.скорость);
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
    private static void эффекты(ServerLevel уровень, BlockPos позиция, float скорость) {
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

        // Пар из-под крышки — медленный столбик, чтобы блок читался
        // работающим и с высоты, где брызги уже не разглядеть.
        if (время % ЧАСТОТА_ПАРА == 0) {
            уровень.sendParticles(ParticleTypes.CLOUD,
                x + (случай.nextDouble() - 0.5) * 0.4, y + 1.1,
                z + (случай.nextDouble() - 0.5) * 0.4,
                1, 0.0, 0.02, 0.0, 0.01);
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

        int тир = ClassParty.тир(уровень.getServer(), PlayerClassData.Класс.SMITH);
        float сила = ClassesConfig.очистительСила(тир);
        float сопротивление = ClassesConfig.очистительСопротивление();
        int радиус = ClassesConfig.очистительРадиус();
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
        сам.реагент.shrink(1);
        сам.setChanged();

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
    }

    @Override
    protected void saveAdditional(CompoundTag тег, HolderLookup.Provider реестры) {
        super.saveAdditional(тег, реестры);
        тег.putInt(КЛЮЧ_НОЧЬ, последняяНочь);
        if (!реагент.isEmpty()) тег.put(КЛЮЧ_РЕАГЕНТ, реагент.save(реестры));
    }

    @Override
    protected void loadAdditional(CompoundTag тег, HolderLookup.Provider реестры) {
        super.loadAdditional(тег, реестры);
        последняяНочь = тег.contains(КЛЮЧ_НОЧЬ) ? тег.getInt(КЛЮЧ_НОЧЬ) : -1;
        реагент = тег.contains(КЛЮЧ_РЕАГЕНТ)
            ? ItemStack.parse(реестры, тег.getCompound(КЛЮЧ_РЕАГЕНТ)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    }
}
