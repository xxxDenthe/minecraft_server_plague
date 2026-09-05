package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Андезитовый очиститель поверхности — первый тир из спека ядра,
 * раздел 10.1. Раз за ночь, если запитан вращением и есть реагент,
 * поднимает сопротивление своего чанка и с некоторой вероятностью
 * снимает уровень заражения.
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

    private static final String КЛЮЧ_РЕАГЕНТ = "Reagent";
    private static final String КЛЮЧ_НОЧЬ = "LastNight";

    private ItemStack реагент = ItemStack.EMPTY;

    /** Номер ночи, за которую уже отработали. -1 — ещё ни разу. */
    private int последняяНочь = -1;

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
        if (уровень.getGameTime() % ИНТЕРВАЛ != 0) return;

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

        if (сам.реагент.isEmpty()) return;
        if (CreateBridge.скоростьРядом(уровень, позиция) < ClassesConfig.очистительМинСкорость()) return;

        int тир = ClassParty.тир(уровень.getServer(), PlayerClassData.Класс.SMITH);
        float сила = ClassesConfig.очистительСила(тир);

        boolean снизился = PlagueBridge.очиститьЧанк(уровень,
            позиция.getX() >> 4, позиция.getZ() >> 4, сила, ClassesConfig.очистительСопротивление());

        сам.реагент.shrink(1);
        сам.setChanged();

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
