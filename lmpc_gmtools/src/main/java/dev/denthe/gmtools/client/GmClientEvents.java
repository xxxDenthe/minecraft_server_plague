package dev.denthe.gmtools.client;

import dev.denthe.gmtools.GmTools;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Реакция на хоткеи. Пока панель ни разу не открыта паролем, обе клавиши
 * ведут только на экран пароля — быстрый наблюдатель тоже под замком,
 * чтобы «весь доступ» открывался одним паролем, как просил владелец.
 */
@EventBusSubscriber(modid = GmTools.MODID, value = Dist.CLIENT)
public final class GmClientEvents {
    private GmClientEvents() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (GmKeyMappings.OPEN_PANEL.consumeClick()) {
            mc.setScreen(new GmPanelScreen());
        }
        while (GmKeyMappings.TOGGLE_SPECTATOR.consumeClick()) {
            if (GmPanelScreen.unlocked) {
                SpectatorToggle.toggle(mc);
            } else {
                mc.setScreen(new GmPanelScreen());
            }
        }
    }
}
