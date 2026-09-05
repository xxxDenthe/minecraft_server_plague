package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
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

        switch (PlayerClassData.данные(игрок).класс) {
            case SMITH -> кузнецЧинит(игрок);
            case CHRONICLER -> летописецСмотрит(игрок);
            default -> { }
        }
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

    /** Работа на наковальне — профильное действие Кузнеца, отсюда и мастерство. */
    @SubscribeEvent
    public static void кузнецУНаковальни(AnvilRepairEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerClassData.данные(игрок).класс != PlayerClassData.Класс.SMITH) return;
        PlayerClassData.прибавитьМастерство(игрок, ClassesConfig.кузнецМастерствоЗаРемонт());
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
