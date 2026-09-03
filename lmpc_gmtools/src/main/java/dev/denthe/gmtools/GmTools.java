package dev.denthe.gmtools;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * Точка входа. Мод целиком клиентский — вся логика в пакете client,
 * подписчики регистрируются через @EventBusSubscriber, поэтому здесь
 * делать нечего.
 */
@Mod(value = GmTools.MODID, dist = Dist.CLIENT)
public class GmTools {
    public static final String MODID = "lmpc_gmtools";

    public GmTools() {}
}
