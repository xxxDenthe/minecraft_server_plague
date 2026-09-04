package dev.denthe.shade;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Точка входа. Вся работа — на клиенте (пакет client): пост-эффект на
 * кадр, туман, споры, пасмурное небо. На сервере мод просто загружен.
 */
@Mod(LmpcShade.MODID)
public final class LmpcShade {
    public static final String MODID = "lmpc_shade";

    public LmpcShade(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ShadeConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // ссылка на client-класс только под клиентом — на сервере не грузим
            modBus.addListener(dev.denthe.shade.client.ShadeSky::onRegister);
        }
    }
}
