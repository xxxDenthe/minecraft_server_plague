package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Пассивки Кузнеца, Фермера и Летописца и рост их мастерства.
 * Спек — 2026-09-04-klassy-design.md, разделы 5–7.
 *
 * До 0.6.0 три класса из четырёх были пустыми названиями: выбрать
 * можно, а разницы никакой. Здесь у каждого появилось то, что видно
 * без единого мода-донора — ни Create, ни FarmersDelight, ни Jade
 * в сборке `lmpc_classes` нет и по границам подсистемы быть не должно.
 *
 * **Это честные заглушки, а не то, что обещает спек.** Верхние тиры
 * Очистителя (Кузнец), грядка `plague_bloom` (Фермер) и снимок-артефакт
 * (Летописец) требуют чужих подсистем — Мира и Лора, которых ещё нет.
 * Здесь у каждого класса стоит вандальная замена того же настроения,
 * вся в конфиге, чтобы её было не жалко выкинуть, когда подъедет
 * настоящая механика.
 *
 * Счётчиков «сколько тиков прошло» нет ни у кого: интервалы считаются
 * от мирового времени по модулю. Так они переживают перезаход игрока
 * и не заводят ещё одно состояние, которое надо сохранять.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassPassives {
    private ClassPassives() {}

    /** Раз во сколько тиков Летописец получает свежие числа. Секунда — глазу хватает. */
    private static final int ИНТЕРВАЛ_ОБЗОРА = 20;

    @SubscribeEvent
    public static void тикИгрока(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;

        PlayerClassData данные = PlayerClassData.данные(игрок);
        switch (данные.класс) {
            case CLERIC -> клирикЛечится(игрок, данные.тир());
            case SMITH -> кузнецЧинит(игрок);
            case CHRONICLER -> {
                летописецСмотрит(игрок);
                датьСкорость(игрок, данные.тир());
            }
            default -> { }
        }
        // Класс мог смениться минуту назад — прибавка к скорости обязана уйти
        // вместе с ним. Снимаем тут, а не в обработчике смены класса: тот
        // не сработает, если игрок вышел Летописцем, а мод обновили.
        if (данные.класс != PlayerClassData.Класс.CHRONICLER) снятьСкорость(игрок);
    }

    // ── Клирик ────────────────────────────────────────────────────────

    /**
     * «Своя рана заживает первой»: раз в
     * {@code clericRegenIntervalTicks} (делится на тир) Клирику
     * возвращается полсердца.
     *
     * Ванильная регенерация требует сытости, а чума сытость и режет —
     * поэтому эта работает всегда. Полсердца в полминуты в бою ничего
     * не решает, а в дороге экономит еду, которой при чуме мало.
     */
    private static void клирикЛечится(ServerPlayer игрок, int тир) {
        int интервал = ClassesConfig.клирикИнтервалРегенерации(тир);
        if (интервал <= 0 || игрок.level().getGameTime() % интервал != 0) return;
        if (игрок.getHealth() >= игрок.getMaxHealth()) return;
        игрок.heal(1.0f);
    }

    // ── Кузнец ────────────────────────────────────────────────────────

    /**
     * «У хозяина механизм не ржавеет»: раз в
     * {@code smithRepairIntervalTicks} чинится одно очко прочности
     * самой побитой вещи в руках или на теле. Медленно нарочно —
     * это удобство в дороге, а не замена наковальне.
     */
    private static void кузнецЧинит(ServerPlayer игрок) {
        int интервал = ClassesConfig.кузнецИнтервалРемонта(PlayerClassData.данные(игрок).тир());
        if (интервал <= 0 || игрок.level().getGameTime() % интервал != 0) return;

        ItemStack худшая = null;
        for (ItemStack стопка : снаряжение(игрок)) {
            if (стопка.isEmpty() || !стопка.isDamaged()) continue;
            if (худшая == null || стопка.getDamageValue() > худшая.getDamageValue()) худшая = стопка;
        }
        if (худшая != null) худшая.setDamageValue(худшая.getDamageValue() - 1);
    }

    private static List<ItemStack> снаряжение(Player игрок) {
        List<ItemStack> всё = new ArrayList<>(6);
        всё.add(игрок.getMainHandItem());
        всё.add(игрок.getOffhandItem());
        всё.addAll(игрок.getInventory().armor);
        return всё;
    }

    /**
     * Скованное снаряжение — профильное действие Кузнеца. «Снаряжение» —
     * всё, у чего есть прочность: оружие, инструмент, броня, щит.
     * Проверять по прочности, а не перечислять типы предметов: в паке
     * почти сотня модов, и любой список устареет к первой же сборке.
     *
     * Накрутить нельзя: каждый крафт стоит материалов.
     */
    @SubscribeEvent
    public static void кузнецСковал(PlayerEvent.ItemCraftedEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.SMITH) return;
        ItemStack вещь = событие.getCrafting();
        if (!вещь.isDamageableItem()) return;

        int тир = PlayerClassData.данные(игрок).тир();
        PlayerClassData.прибавитьМастерство(игрок, ClassesConfig.кузнецМастерствоЗаКрафт());

        int опыт = ClassesConfig.кузнецОпытЗаКрафт(тир);
        if (опыт > 0) игрок.giveExperiencePoints(опыт);

        зачароватьСлучайно(игрок, вещь, тир);
    }

    /**
     * «Рука мастера кладёт чары сама»: с шансом
     * {@code smithEnchantChancePerTier} × тир свежая вещь выходит
     * из-под молота уже зачарованной.
     *
     * Берём ванильный подбор чар со стола, ограниченный тегом
     * {@code in_enchanting_table}: без него в выборку попадают
     * сокровища и проклятия, и «награда» иногда оказывалась бы
     * проклятием привязки.
     *
     * Уже зачарованное не трогаем: иначе Кузнец мог бы перекладывать
     * чары, пересобирая вещь.
     */
    private static void зачароватьСлучайно(ServerPlayer игрок, ItemStack вещь, int тир) {
        if (вещь.isEnchanted()) return;
        if (игрок.getRandom().nextDouble() >= ClassesConfig.кузнецШансЗачарования(тир)) return;

        var реестр = игрок.serverLevel().registryAccess();
        Optional<? extends HolderSet<Enchantment>> набор =
            реестр.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);

        EnchantmentHelper.enchantItem(игрок.getRandom(), вещь,
            ClassesConfig.кузнецСилаЗачарования(тир), реестр, набор);
    }

    /**
     * Печь — второе профильное действие. Событие приходит ровно тогда,
     * когда игрок забирает выплавленное и ваниль выдаёт ему опыт, так
     * что «переплавка руды» и «опыт с печки» — одно и то же событие,
     * а не два.
     *
     * Очко даётся за каждые {@code smithSmeltsPerMastery} предметов
     * с округлением вниз, поэтому вынос по одному не даёт ничего:
     * иначе Кузнец добирал бы третий тир, щёлкая по печи стопкой камня.
     */
    @SubscribeEvent
    public static void кузнецПереплавил(PlayerEvent.ItemSmeltedEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.SMITH) return;
        int очки = ClassesConfig.кузнецМастерствоЗаПлавку(событие.getSmelting().getCount());
        if (очки <= 0) return;

        int тир = PlayerClassData.данные(игрок).тир();
        PlayerClassData.прибавитьМастерство(игрок, очки);

        int опыт = ClassesConfig.кузнецОпытЗаПлавку(тир, очки);
        if (опыт > 0) игрок.giveExperiencePoints(опыт);
    }

    // ── Фермер ────────────────────────────────────────────────────────

    /**
     * «Блюда сытят сильнее». Считаем от сытности самого блюда, а не
     * плоской добавкой: иначе печенье кормило бы как стейк. Ваниль
     * уже применила еду к этому моменту, поэтому просто доедаем
     * сверху её же методом.
     */
    @SubscribeEvent
    public static void фермерЕст(LivingEntityUseItemEvent.Finish событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.FARMER) return;

        FoodProperties еда = событие.getItem().get(DataComponents.FOOD);
        if (еда == null || еда.nutrition() <= 0) return;

        double бонус = ClassesConfig.фермерБонусЕды(PlayerClassData.данные(игрок).тир());
        int добавка = (int) Math.round(еда.nutrition() * бонус);
        if (добавка <= 0) return;

        FoodData сытость = игрок.getFoodData();
        сытость.eat(добавка, 0.5f);
    }

    /** Собранная созревшая культура — профильное действие Фермера. */
    @SubscribeEvent
    public static void фермерСобрал(BlockEvent.BreakEvent событие) {
        if (!(событие.getPlayer() instanceof ServerPlayer игрок)) return;
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.FARMER) return;
        if (!(событие.getState().getBlock() instanceof CropBlock культура)) return;
        if (!культура.isMaxAge(событие.getState())) return;

        PlayerClassData.прибавитьМастерство(игрок, ClassesConfig.фермерМастерствоЗаУрожай());
    }

    /**
     * «Щедрый урожай»: с шансом {@code farmerExtraDropChancePerTier} × тир
     * созревшая культура даёт на один предмет больше в каждой стопке.
     *
     * Ловим {@link BlockDropsEvent}, а не {@code BreakEvent}: дроп там
     * уже посчитан ванилью и ещё не выброшен в мир, поэтому прибавка
     * складывается со всем остальным — с Удачей, с косой, с чужими
     * модами на урожай — вместо того чтобы спорить с ними.
     */
    @SubscribeEvent
    public static void фермерЩедрыйУрожай(BlockDropsEvent событие) {
        if (!(событие.getBreaker() instanceof ServerPlayer игрок)) return;
        PlayerClassData данные = PlayerClassData.данные(игрок);
        if (данные.класс != PlayerClassData.Класс.FARMER) return;
        if (!(событие.getState().getBlock() instanceof CropBlock культура)) return;
        if (!культура.isMaxAge(событие.getState())) return;

        double шанс = ClassesConfig.фермерЩедрыйУрожай(данные.тир());
        if (событие.getLevel().getRandom().nextDouble() >= шанс) return;

        for (ItemEntity дроп : событие.getDrops()) {
            ItemStack стопка = дроп.getItem().copy();
            стопка.grow(1);
            дроп.setItem(стопка);
        }
    }

    /** Заражённая трава `plaguecore`, с которой собирается дикий бутон. */
    private static final Set<String> ТРАВА_ГНИЛИ = Set.of(
        "plaguecore:blighted_grass", "plaguecore:blighted_tall_grass");

    /**
     * Дикий сбор бутона чумы — то, что спек (раздел 6) называет
     * «риском»: бутон роняет заражённая трава, и только в заражённом
     * чанке. Это первый и до появления грядки единственный источник
     * семян, поэтому он открыт всем классам, а не одному Фермеру:
     * эксклюзив Фермера — грядка, а не доступ к сырью.
     *
     * Блоки соседнего мода опознаются по идентификатору, а не по типу:
     * жёсткой зависимости на `plaguecore` у нас нет и не будет, и без
     * него этот обработчик просто никогда не срабатывает.
     */
    @SubscribeEvent
    public static void дикийБутон(BlockEvent.BreakEvent событие) {
        if (!(событие.getPlayer() instanceof ServerPlayer игрок)) return;
        ServerLevel уровень = игрок.serverLevel();

        String идентификатор = BuiltInRegistries.BLOCK.getKey(событие.getState().getBlock()).toString();
        if (!ТРАВА_ГНИЛИ.contains(идентификатор)) return;

        BlockPos позиция = событие.getPos();
        int уровеньЧанка = PlagueBridge.уровеньЧанкаВ(уровень, позиция);
        if (уровеньЧанка < ClassesConfig.фермерДикийУровень()) return;
        if (уровень.getRandom().nextDouble() >= ClassesConfig.фермерДикийШанс()) return;

        Block.popResource(уровень, позиция, new ItemStack(ClassItems.PLAGUE_BLOOM.get()));
    }

    // ── Летописец ─────────────────────────────────────────────────────

    /**
     * «Глаза партии»: раз в секунду шлём Летописцу точные числа
     * заражённости — свои и тех, кто в радиусе. Числа живут
     * в `plaguecore`, на клиент не синкаются, поэтому иначе их взять
     * неоткуда (и поэтому же без `plaguecore` обзор просто пуст).
     *
     * Заодно — единственный источник мастерства Летописца: минута
     * рядом хотя бы с одним заражённым. Наблюдать за здоровыми
     * не считается: летопись пишут про беду.
     */
    private static void летописецСмотрит(ServerPlayer летописец) {
        long сейчас = летописец.level().getGameTime();
        if (сейчас % ИНТЕРВАЛ_ОБЗОРА != 0) return;
        if (!PlagueBridge.доступен()) return;

        int тир = PlayerClassData.данные(летописец).тир();
        double радиус = ClassesConfig.летописецРадиус(тир);
        List<ClassNetwork.Insight.Запись> записи = new ArrayList<>();
        boolean естьЗаражённый = false;

        записи.add(запись(летописец, true));
        for (ServerPlayer другой : летописец.serverLevel().players()) {
            if (другой == летописец) continue;
            if (записи.size() >= ClassNetwork.Insight.МАКС_ЗАПИСЕЙ) break;
            if (летописец.distanceToSqr(другой) > радиус * радиус) continue;
            записи.add(запись(другой, false));
        }
        for (ClassNetwork.Insight.Запись з : записи) {
            if (з.стадия() > 0) естьЗаражённый = true;
        }

        PacketDistributor.sendToPlayer(летописец,
            new ClassNetwork.Insight(List.copyOf(записи), уровеньПодПрицелом(летописец)));

        if (естьЗаражённый && сейчас % ClassSwitch.ТИКОВ_В_МИНУТЕ == 0) {
            PlayerClassData.прибавитьМастерство(летописец, ClassesConfig.летописецМастерствоВМинуту());
        }
    }

    /** Идентификатор прибавки к скорости. Один на мод — по нему же и снимается. */
    private static final ResourceLocation СКОРОСТЬ_ЛЕТОПИСЦА =
        ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "chronicler_speed");

    /**
     * «Лёгкий шаг летописца»: прибавка к скорости бега по тиру,
     * 5 ⁄ 8 ⁄ 15 % от базовой.
     *
     * Модификатор временный (transient): в сейв не пишется, а
     * навешивается заново каждым тиком. Так он не может остаться
     * на игроке после смены класса, удаления мода или отката версии —
     * прибитая намертво прибавка к скорости в сейве чинится только
     * командой, и хорошо, если кто-то её заметит.
     */
    private static void датьСкорость(ServerPlayer игрок, int тир) {
        AttributeInstance атрибут = игрок.getAttribute(Attributes.MOVEMENT_SPEED);
        if (атрибут == null) return;

        double нужно = ClassesConfig.летописецСкорость(тир);
        AttributeModifier текущий = атрибут.getModifier(СКОРОСТЬ_ЛЕТОПИСЦА);
        if (текущий != null && текущий.amount() == нужно) return;

        атрибут.addOrUpdateTransientModifier(new AttributeModifier(
            СКОРОСТЬ_ЛЕТОПИСЦА, нужно, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void снятьСкорость(ServerPlayer игрок) {
        AttributeInstance атрибут = игрок.getAttribute(Attributes.MOVEMENT_SPEED);
        if (атрибут != null && атрибут.hasModifier(СКОРОСТЬ_ЛЕТОПИСЦА)) {
            атрибут.removeModifier(СКОРОСТЬ_ЛЕТОПИСЦА);
        }
    }

    /**
     * Уровень заражения чанка, на который Летописец смотрит; если ни
     * во что не целится — того, где стоит. Это и есть обещанная спеком
     * «точная цифра вместо округлённой строки»: интеграции с Jade нет
     * (плагин требовал бы жёсткой зависимости на его API, а Jade в этом
     * паке уже один раз ронял клиент), поэтому число показывает
     * собственная панель Летописца.
     */
    private static int уровеньПодПрицелом(ServerPlayer летописец) {
        HitResult попадание = летописец.pick(48.0, 0f, false);
        BlockPos точка = попадание.getType() == HitResult.Type.BLOCK
            ? ((BlockHitResult) попадание).getBlockPos()
            : летописец.blockPosition();
        return PlagueBridge.уровеньЧанкаВ(летописец.serverLevel(), точка);
    }

    private static ClassNetwork.Insight.Запись запись(ServerPlayer игрок, boolean этоЯ) {
        return new ClassNetwork.Insight.Запись(
            игрок.getGameProfile().getName(),
            PlagueBridge.стадия(игрок),
            PlagueBridge.заражённость(игрок),
            этоЯ);
    }
}
