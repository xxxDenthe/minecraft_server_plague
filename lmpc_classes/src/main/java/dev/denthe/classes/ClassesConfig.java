package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Баланс классов в `config/lmpc_classes-common.toml`. Тот же приём,
 * что в `plaguecore`: числа, которые придётся крутить по ощущениям,
 * живут в файле, а не в коде.
 *
 * Пути ключей нарочно плоские, без секций {@code push/pop}: у владельца
 * уже лежит настроенный файл, а смена пути ключа молча сбросила бы
 * значение к умолчанию.
 *
 * **Тип COMMON, а не SERVER.** Значит, клиент читает свой файл, а не
 * серверный: гримуар покажет чужой порог тира, если у игрока пак другой
 * версии. Мирюсь сознательно — пак раздаётся лаунчером одним куском,
 * а SERVER-конфиг переехал бы в `world/serverconfig/` и сломал бы уже
 * настроенный файл владельца.
 */
public final class ClassesConfig {
    private ClassesConfig() {}

    private static final ModConfigSpec.Builder СТРОИТЕЛЬ = new ModConfigSpec.Builder();

    // ── смена класса ──────────────────────────────────────────────────

    private static final ModConfigSpec.IntValue КУЛДАУН_СМЕНЫ_КЛАССА = СТРОИТЕЛЬ
        .comment("Минимальный перерыв между сменами класса, в минутах.")
        .defineInRange("classSwitchCooldownMinutes", 30, 0, 1440);

    private static final ModConfigSpec.DoubleValue ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ = СТРОИТЕЛЬ
        .comment("Какая доля мастерства старого класса остаётся при смене.",
                 "0.3 — треть сохраняется, остальное срезается.")
        .defineInRange("masteryKeepFraction", 0.3, 0.0, 1.0);

    // ── мастерство и тиры, общие для всех классов (спек 2.1 и 11) ─────

    private static final ModConfigSpec.IntValue ПОРОГ_ТИРА_2 = СТРОИТЕЛЬ
        .comment("Мастерство, с которого начинается второй тир класса (из 100).")
        .defineInRange("masteryTier2At", 25, 1, ClassMastery.МАКСИМУМ);

    private static final ModConfigSpec.IntValue ПОРОГ_ТИРА_3 = СТРОИТЕЛЬ
        .comment("Мастерство, с которого начинается третий тир класса (из 100).")
        .defineInRange("masteryTier3At", 60, 1, ClassMastery.МАКСИМУМ);

    private static final ModConfigSpec.DoubleValue СИЛА_ЗА_ТИР = СТРОИТЕЛЬ
        .comment("Насколько сильнее пассивка за каждый тир выше первого.",
                 "0.15 — второй тир +15%, третий +30%. Общий множитель для всех классов.")
        .defineInRange("masteryPowerPerTier", 0.15, 0.0, 2.0);

    private static final ModConfigSpec.DoubleValue КУЛДАУН_ЗА_ТИР = СТРОИТЕЛЬ
        .comment("Насколько короче кулдаун активок за каждый тир выше первого.",
                 "0.2 — второй тир −20%, третий −40%. Ниже 10% от исходного не опускается.")
        .defineInRange("masteryCooldownCutPerTier", 0.2, 0.0, 0.9);

    // ── Клирик (спек, раздел 4) ───────────────────────────────────────

    private static final ModConfigSpec.DoubleValue КУЛОН_БАЗОВАЯ_ЗАЩИТА = СТРОИТЕЛЬ
        .comment("Доп. защита от кулона Клирика (0..1), складывается с бронёй в plaguecore.",
                 "Полная — только у Клирика, у остальных классов — доля clericPendantOtherClassFraction.",
                 "У Клирика растёт с тиром мастерства, но выше 0.9 не поднимается.")
        .defineInRange("clericPendantProtection", 0.15, 0.0, 0.9);

    private static final ModConfigSpec.DoubleValue КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ = СТРОИТЕЛЬ
        .comment("Доля clericPendantProtection, которую кулон даёт не-Клирику.")
        .defineInRange("clericPendantOtherClassFraction", 0.5, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ОТВАР_КУЛДАУН = СТРОИТЕЛЬ
        .comment("Кулдаун улучшенного отвара Клирика на одного игрока, в минутах.",
                 "Сокращается тиром мастерства (masteryCooldownCutPerTier).")
        .defineInRange("clericBrewCooldownMinutes", 10, 0, 120);

    private static final ModConfigSpec.DoubleValue ОТВАР_ЛЕЧЕНИЕ = СТРОИТЕЛЬ
        .comment("Сколько очков заражённости снимает улучшенный отвар за раз.",
                 "Растёт с тиром мастерства (masteryPowerPerTier).")
        .defineInRange("clericBrewCureAmount", 40.0, 0.0, 100.0);

    private static final ModConfigSpec.IntValue ОТВАР_ИММУНИТЕТ = СТРОИТЕЛЬ
        .comment("Иммунитет от улучшенного отвара, в минутах.")
        .defineInRange("clericBrewImmunityMinutes", 5, 0, 60);

    private static final ModConfigSpec.IntValue СКОРМИТЬ_ДЛИТЕЛЬНОСТЬ = СТРОИТЕЛЬ
        .comment("Сколько тиков держать ПКМ на союзнике, чтобы напоить его отваром. 60 — три секунды.")
        .defineInRange("clericFeedChannelTicks", 60, 10, 600);

    private static final ModConfigSpec.DoubleValue СКОРМИТЬ_ДИСТАНЦИЯ = СТРОИТЕЛЬ
        .comment("Максимальное расстояние до союзника, чтобы его напоить, в блоках.",
                 "Не магия — Клирик должен стоять вплотную. 1.5 — чуть больше одного блока.")
        .defineInRange("clericFeedMaxDistance", 1.5, 0.5, 4.0);

    private static final ModConfigSpec.IntValue КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ = СТРОИТЕЛЬ
        .comment("Мастерство Клирика за одно удачное лечение отваром (из 100).",
                 "Лечение союзника даёт вдвое больше, чем лечение себя.")
        .defineInRange("clericMasteryPerCure", 4, 0, 100);

    // ── Кузнец (спек, раздел 5) ───────────────────────────────────────

    private static final ModConfigSpec.IntValue КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА = СТРОИТЕЛЬ
        .comment("Раз во сколько тиков у Кузнеца само чинится одно очко прочности.",
                 "100 — раз в пять секунд, чинится самая побитая вещь в руках или на теле.",
                 "Стоит вместо бонуса на машинах Create: Create — зависимость пака, а не мода,",
                 "и дотянуться до неё из lmpc_classes нечем. С тиром интервал короче.",
                 "0 — выключить пассивный ремонт совсем.")
        .defineInRange("smithRepairIntervalTicks", 100, 0, 12000);

    private static final ModConfigSpec.IntValue КУЗНЕЦ_МАСТЕРСТВО_ЗА_РЕМОНТ = СТРОИТЕЛЬ
        .comment("Мастерство Кузнеца за одну работу на наковальне (из 100).")
        .defineInRange("smithMasteryPerRepair", 2, 0, 100);

    // ── Фермер (спек, раздел 6) ───────────────────────────────────────

    private static final ModConfigSpec.DoubleValue ФЕРМЕР_БОНУС_ЕДЫ = СТРОИТЕЛЬ
        .comment("Доп. насыщение Фермера от съеденного блюда, долей от его сытности.",
                 "0.5 — плюс половина. Растёт с тиром мастерства.")
        .defineInRange("farmerFoodBonus", 0.5, 0.0, 3.0);

    private static final ModConfigSpec.IntValue ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ = СТРОИТЕЛЬ
        .comment("Мастерство Фермера за одну собранную созревшую культуру (из 100).")
        .defineInRange("farmerMasteryPerHarvest", 1, 0, 100);

    private static final ModConfigSpec.IntValue ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА = СТРОИТЕЛЬ
        .comment("Во сколько раз грядка бутона чумы растёт медленнее ванильных культур.",
                 "2 — вдвое медленнее. 1 — наравне с пшеницей.",
                 "Смысл замедления: грядка не должна обнулять риск похода в Гниль.",
                 "Правится прямо в игре: /lmpcclasses tune farmerBloomGrowthDivisor <число>")
        .defineInRange("farmerBloomGrowthDivisor", 2, 1, 20);

    private static final ModConfigSpec.DoubleValue ФЕРМЕР_ДИКИЙ_ШАНС = СТРОИТЕЛЬ
        .comment("Вероятность, что заражённая трава в Гнили обронит бутон чумы.",
                 "Это дикий сбор из спека — источник бутона до первой грядки.",
                 "Правится прямо в игре: /lmpcclasses tune farmerBloomWildChance <число>")
        .defineInRange("farmerBloomWildChance", 0.25, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ФЕРМЕР_ДИКИЙ_УРОВЕНЬ = СТРОИТЕЛЬ
        .comment("С какого уровня заражения чанка трава начинает ронять бутон (1..5).",
                 "2 и выше — то, что спек называет Гнилью и её подступами.")
        .defineInRange("farmerBloomWildMinLevel", 2, 1, 5);

    // ── Летописец (спек, раздел 7) ────────────────────────────────────

    private static final ModConfigSpec.DoubleValue ЛЕТОПИСЕЦ_РАДИУС = СТРОИТЕЛЬ
        .comment("Радиус, в котором Летописец видит точную заражённость игроков, в блоках.",
                 "Растёт с тиром мастерства. 0 — только собственная заражённость.")
        .defineInRange("chroniclerInsightRadius", 16.0, 0.0, 128.0);

    private static final ModConfigSpec.IntValue ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ = СТРОИТЕЛЬ
        .comment("Мастерство Летописца за минуту рядом хотя бы с одним заражённым (из 100).")
        .defineInRange("chroniclerMasteryPerMinute", 1, 0, 100);

    public static final ModConfigSpec SPEC = СТРОИТЕЛЬ.build();

    // ── чтение ────────────────────────────────────────────────────────

    /** Минимальный перерыв между сменами класса, в тиках (20 в секунде). */
    public static long кулдаунСменыТики() {
        return КУЛДАУН_СМЕНЫ_КЛАССА.get() * 1200L;
    }

    /** Доля мастерства старого класса, остающаяся при смене. */
    public static double доляМастерстваПриСмене() {
        return ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ.get();
    }

    public static int порогТира2() { return ПОРОГ_ТИРА_2.get(); }

    public static int порогТира3() { return ПОРОГ_ТИРА_3.get(); }

    /** Множитель силы пассивки для тира. */
    public static float силаТира(int тир) {
        return ClassMastery.множительСилы(тир, СИЛА_ЗА_ТИР.get());
    }

    /** Множитель кулдауна для тира. */
    public static float кулдаунТира(int тир) {
        return ClassMastery.множительКулдауна(тир, КУЛДАУН_ЗА_ТИР.get());
    }

    /** Базовая защита от кулона (для Клирика первого тира — целиком). */
    public static float кулонБазоваяЗащита() {
        return КУЛОН_БАЗОВАЯ_ЗАЩИТА.get().floatValue();
    }

    /** Доля базовой защиты кулона для не-Клирика. */
    public static float кулонДоляНеКлирику() {
        return КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ.get().floatValue();
    }

    /** Кулдаун улучшенного отвара для тира Клирика, в тиках. */
    public static long отварКулдаунТики(int тир) {
        return Math.round(ОТВАР_КУЛДАУН.get() * 1200L * (double) кулдаунТира(тир));
    }

    /** Сколько очков снимает улучшенный отвар на этом тире. */
    public static float отварЛечение(int тир) {
        return ОТВАР_ЛЕЧЕНИЕ.get().floatValue() * силаТира(тир);
    }

    /** Длительность иммунитета от отвара, в тиках. */
    public static long отварИммунитетТики() {
        return ОТВАР_ИММУНИТЕТ.get() * 1200L;
    }

    /** Сколько тиков держать ПКМ на союзнике, чтобы напоить его. */
    public static int скормитьДлительностьТики() {
        return СКОРМИТЬ_ДЛИТЕЛЬНОСТЬ.get();
    }

    /** Максимальное расстояние до союзника, чтобы его напоить, в блоках. */
    public static double скормитьДистанция() {
        return СКОРМИТЬ_ДИСТАНЦИЯ.get();
    }

    public static int клирикМастерствоЗаЛечение() {
        return КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ.get();
    }

    /**
     * Интервал пассивного ремонта Кузнеца для тира, в тиках;
     * 0 — ремонт выключен. Чем выше тир, тем короче интервал,
     * но не короче одного тика.
     */
    public static int кузнецИнтервалРемонта(int тир) {
        int базовый = КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА.get();
        if (базовый <= 0) return 0;
        return Math.max(1, Math.round(базовый / силаТира(тир)));
    }

    public static int кузнецМастерствоЗаРемонт() {
        return КУЗНЕЦ_МАСТЕРСТВО_ЗА_РЕМОНТ.get();
    }

    /** Доп. насыщение Фермера, долей от сытности блюда, для тира. */
    public static double фермерБонусЕды(int тир) {
        return ФЕРМЕР_БОНУС_ЕДЫ.get() * силаТира(тир);
    }

    public static int фермерМастерствоЗаУрожай() {
        return ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ.get();
    }

    /** Во сколько раз грядка бутона растёт медленнее ванильных культур. */
    public static int фермерДелительРоста() {
        return ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА.get();
    }

    /** Вероятность, что заражённая трава обронит бутон чумы. */
    public static double фермерДикийШанс() {
        return ФЕРМЕР_ДИКИЙ_ШАНС.get();
    }

    /** С какого уровня заражения чанка трава начинает ронять бутон. */
    public static int фермерДикийУровень() {
        return ФЕРМЕР_ДИКИЙ_УРОВЕНЬ.get();
    }

    /** Радиус обзора Летописца для тира, в блоках. */
    public static double летописецРадиус(int тир) {
        return ЛЕТОПИСЕЦ_РАДИУС.get() * силаТира(тир);
    }

    public static int летописецМастерствоВМинуту() {
        return ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ.get();
    }

    // ── правка чисел прямо в игре ─────────────────────────────────────

    /**
     * Числа, которые можно крутить командой {@code /lmpcclasses tune},
     * не выходя из игры и не перезапуская сервер.
     *
     * Заведено по прямой просьбе владельца про скорость грядки, но
     * ограничивать список одним ключом смысла нет: все эти числа
     * подбираются одинаково — на живой сессии, по ощущению. Это то же
     * правило проекта «игровые числа наружу», доведённое до конца:
     * за день до сессии баланс должен править не пересборка мода
     * и даже не перезапуск сервера, а одна строка в чате.
     *
     * Порядок вставки сохраняется — по нему же команда печатает список.
     */
    private static final Map<String, ModConfigSpec.ConfigValue<?>> НАСТРАИВАЕМЫЕ =
        new LinkedHashMap<>();

    static {
        НАСТРАИВАЕМЫЕ.put("classSwitchCooldownMinutes", КУЛДАУН_СМЕНЫ_КЛАССА);
        НАСТРАИВАЕМЫЕ.put("masteryKeepFraction", ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ);
        НАСТРАИВАЕМЫЕ.put("masteryTier2At", ПОРОГ_ТИРА_2);
        НАСТРАИВАЕМЫЕ.put("masteryTier3At", ПОРОГ_ТИРА_3);
        НАСТРАИВАЕМЫЕ.put("masteryPowerPerTier", СИЛА_ЗА_ТИР);
        НАСТРАИВАЕМЫЕ.put("masteryCooldownCutPerTier", КУЛДАУН_ЗА_ТИР);
        НАСТРАИВАЕМЫЕ.put("clericPendantProtection", КУЛОН_БАЗОВАЯ_ЗАЩИТА);
        НАСТРАИВАЕМЫЕ.put("clericBrewCooldownMinutes", ОТВАР_КУЛДАУН);
        НАСТРАИВАЕМЫЕ.put("clericBrewCureAmount", ОТВАР_ЛЕЧЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("clericMasteryPerCure", КЛИРИК_МАСТЕРСТВО_ЗА_ЛЕЧЕНИЕ);
        НАСТРАИВАЕМЫЕ.put("smithRepairIntervalTicks", КУЗНЕЦ_ИНТЕРВАЛ_РЕМОНТА);
        НАСТРАИВАЕМЫЕ.put("smithMasteryPerRepair", КУЗНЕЦ_МАСТЕРСТВО_ЗА_РЕМОНТ);
        НАСТРАИВАЕМЫЕ.put("farmerFoodBonus", ФЕРМЕР_БОНУС_ЕДЫ);
        НАСТРАИВАЕМЫЕ.put("farmerMasteryPerHarvest", ФЕРМЕР_МАСТЕРСТВО_ЗА_УРОЖАЙ);
        НАСТРАИВАЕМЫЕ.put("farmerBloomGrowthDivisor", ФЕРМЕР_ДЕЛИТЕЛЬ_РОСТА);
        НАСТРАИВАЕМЫЕ.put("farmerBloomWildChance", ФЕРМЕР_ДИКИЙ_ШАНС);
        НАСТРАИВАЕМЫЕ.put("farmerBloomWildMinLevel", ФЕРМЕР_ДИКИЙ_УРОВЕНЬ);
        НАСТРАИВАЕМЫЕ.put("chroniclerInsightRadius", ЛЕТОПИСЕЦ_РАДИУС);
        НАСТРАИВАЕМЫЕ.put("chroniclerMasteryPerMinute", ЛЕТОПИСЕЦ_МАСТЕРСТВО_В_МИНУТУ);
    }

    /** Имена настраиваемых чисел, в порядке объявления. */
    public static Set<String> настраиваемые() {
        return НАСТРАИВАЕМЫЕ.keySet();
    }

    /** Текущее значение числа; {@code null}, если такого ключа нет. */
    public static Object значение(String ключ) {
        ModConfigSpec.ConfigValue<?> поле = НАСТРАИВАЕМЫЕ.get(ключ);
        return поле == null ? null : поле.get();
    }

    /**
     * Записать новое значение и сохранить файл конфига. Возвращает
     * то, что реально записалось, или {@code null}, если ключ неизвестен
     * либо конфиг ещё не загружен.
     *
     * Значение за границами {@code defineInRange} не отвергается тихо:
     * ModConfigSpec поправит его при следующей загрузке, а до тех пор
     * в памяти жило бы то, чего в файле нет. Поэтому запись сразу
     * перечитывается и наружу отдаётся именно она.
     */
    public static Object задать(String ключ, double новое) {
        ModConfigSpec.ConfigValue<?> поле = НАСТРАИВАЕМЫЕ.get(ключ);
        if (поле == null) return null;
        try {
            if (поле instanceof ModConfigSpec.IntValue целое) {
                целое.set((int) Math.round(новое));
            } else if (поле instanceof ModConfigSpec.DoubleValue дробное) {
                дробное.set(новое);
            } else {
                return null;
            }
            поле.save();
            return поле.get();
        } catch (RuntimeException e) {
            // Конфиг ещё не загружен или значение не лезет в диапазон —
            // для команды это обычный отказ, а не повод ронять сервер.
            return null;
        }
    }

    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
