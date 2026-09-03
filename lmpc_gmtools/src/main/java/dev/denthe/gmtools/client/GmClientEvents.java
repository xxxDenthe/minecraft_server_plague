package dev.denthe.gmtools.client;

import dev.denthe.gmtools.GmTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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

        returnFromPlagueGui(mc);
    }

    /** Экран plaguecore не знает про нашу панель, поэтому возврат ловим здесь. */
    private static final String PLAGUE_GUI = "dev.denthe.plaguecore.client.PlagueMapScreen";

    private static void returnFromPlagueGui(Minecraft mc) {
        if (!GmPanelScreen.returnAfterPlagueGui) return;

        Screen s = mc.screen;
        boolean isPlagueGui = s != null && s.getClass().getName().equals(PLAGUE_GUI);

        if (isPlagueGui) {
            GmPanelScreen.sawPlagueGui = true;
        } else if (GmPanelScreen.sawPlagueGui && s == null) {
            // карту чумы закрыли — возвращаем панель
            GmPanelScreen.returnAfterPlagueGui = false;
            GmPanelScreen.sawPlagueGui = false;
            mc.setScreen(new GmPanelScreen());
        } else if (GmPanelScreen.sawPlagueGui && !(s instanceof GmPanelScreen)) {
            // ушли с карты не в null, а куда-то ещё — не навязываемся
            GmPanelScreen.returnAfterPlagueGui = false;
            GmPanelScreen.sawPlagueGui = false;
        } else if (!GmPanelScreen.sawPlagueGui && ++GmPanelScreen.plagueGuiWait > 100) {
            // команда не открыла карту за 5 секунд (нет прав / не тот сервер)
            GmPanelScreen.returnAfterPlagueGui = false;
            GmPanelScreen.plagueGuiWait = 0;
        }
    }
}
