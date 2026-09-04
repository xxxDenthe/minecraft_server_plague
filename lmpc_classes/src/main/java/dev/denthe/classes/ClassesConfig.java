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

    public static final ModConfigSpec SPEC = СТРОИТЕЛЬ.build();

    /** Минимальный перерыв между сменами класса, в тиках (20 в секунде). */
    public static long кулдаунСменыТики() {
        return КУЛДАУН_СМЕНЫ_КЛАССА.get() * 1200L;
    }

    /** Доля мастерства старого класса, остающаяся при смене. */
    public static double доляМастерстваПриСмене() {
        return ДОЛЯ_МАСТЕРСТВА_ПРИ_СМЕНЕ.get();
    }

    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
