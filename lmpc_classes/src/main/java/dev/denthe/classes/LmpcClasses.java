package dev.denthe.classes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Точка входа. Подсистема 3 «Классы», отдельным модом от `plaguecore` —
 * не трогаем чужую половину репозитория, ссылаемся на неё только через
 * мягкие мосты (рефлексия, как `ShadeApi`/`ShadeAccess` между
 * `lmpc_shade` и `lmpc_gmtools`), когда дойдёт до способностей классов.
 */
@Mod(LmpcClasses.MODID)
public class LmpcClasses {
    public static final String MODID = "lmpc_classes";

    public LmpcClasses(IEventBus modEventBus, ModContainer container) {
        PlayerClassData.register(modEventBus);
        ClassItems.register(modEventBus);
        ClassCreativeTab.register(modEventBus);
        ClassesConfig.зарегистрировать(modEventBus, container);
    }
}
