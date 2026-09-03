package dev.denthe.gmtools;

import net.neoforged.fml.common.Mod;

/**
 * Точка входа. Клиентская часть (панель, экраны) — в пакете client,
 * подписчики регистрируются через @EventBusSubscriber(value = Dist.CLIENT).
 * Серверная часть — рассылка позиций игроков для карты (пакет net).
 */
@Mod(GmTools.MODID)
public class GmTools {
    public static final String MODID = "lmpc_gmtools";

    public GmTools() {}
}
