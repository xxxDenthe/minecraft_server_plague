package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Баланс классов в `config/lmpc_classes-common.toml`. Тот же приём,
 * что в `plaguecore`: числа, которые придётся крутить по ощущениям,
 * живут в файле, а не в коде.
 */
public final class ClassesConfig {
    private ClassesConfig() {}

    private static final ModConfigSpec.Builder СТРОИТЕЛЬ = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue КУЛДАУН_СМЕНЫ_КЛАССА = СТРОИТЕЛЬ
        .comment("Минимальный перерыв между сменами класса, в минутах.")
        .defineInRange("classSwitchCooldownMinutes", 30, 0, 1440);

    private static final ModConfigSpec.DoubleValue ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ = СТРОИТЕЛЬ
        .comment("Какая доля мастерства старого класса остаётся при смене.",
                 "0.3 — треть сохраняется, остальное срезается.")
        .defineInRange("masteryKeepFraction", 0.3, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue КУЛОН_БАЗОВАЯ_ЗАЩИТА = СТРОИТЕЛЬ
        .comment("Доп. защита от кулона Клирика (0..1), складывается с бронёй в plaguecore.",
                 "Полная — только у Клирика, у остальных классов — половина (clericPendantOtherClassFraction).")
        .defineInRange("clericPendantProtection", 0.15, 0.0, 0.9);

    private static final ModConfigSpec.DoubleValue КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ = СТРОИТЕЛЬ
        .comment("Доля clericPendantProtection, которую кулон даёт не-Клирику.")
        .defineInRange("clericPendantOtherClassFraction", 0.5, 0.0, 1.0);

    private static final ModConfigSpec.IntValue ОТВАР_КУЛДАУН = СТРОИТЕЛЬ
        .comment("Кулдаун улучшенного отвара Клирика на одного игрока, в минутах.")
        .defineInRange("clericBrewCooldownMinutes", 10, 0, 120);

    private static final ModConfigSpec.DoubleValue ОТВАР_ЛЕЧЕНИЕ = СТРОИТЕЛЬ
        .comment("Сколько очков заражённости снимает улучшенный отвар за раз.")
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

    public static final ModConfigSpec SPEC = СТРОИТЕЛЬ.build();

    /** Минимальный перерыв между сменами класса, в тиках (20 в секунде). */
    public static long кулдаунСменыТики() {
        return КУЛДАУН_СМЕНЫ_КЛАССА.get() * 1200L;
    }

    /** Доля мастерства старого класса, остающаяся при смене. */
    public static double доляМастерстваПриСмене() {
        return ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ.get();
    }

    /** Базовая защита от кулона (для Клирика — целиком). */
    public static float кулонБазоваяЗащита() {
        return КУЛОН_БАЗОВАЯ_ЗАЩИТА.get().floatValue();
    }

    /** Доля базовой защиты кулона для не-Клирика. */
    public static float кулонДоляНеКлирику() {
        return КУЛОН_ДОЛЯ_НЕ_КЛИРИКУ.get().floatValue();
    }

    /** Кулдаун улучшенного отвара, в тиках. */
    public static long отварКулдаунТики() {
        return ОТВАР_КУЛДАУН.get() * 1200L;
    }

    /** Сколько очков снимает улучшенный отвар. */
    public static float отварЛечение() {
        return ОТВАР_ЛЕЧЕНИЕ.get().floatValue();
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

    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
