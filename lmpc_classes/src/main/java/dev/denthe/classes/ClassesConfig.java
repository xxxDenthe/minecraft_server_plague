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

    public static final ModConfigSpec SPEC = СТРОИТЕЛЬ.build();

    /** Минимальный перерыв между сменами класса, в тиках (20 в секунде). */
    public static long кулдаунСменыТики() {
        return КУЛДАУН_СМЕНЫ_КЛАССА.get() * 1200L;
    }

    public static void зарегистрировать(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
