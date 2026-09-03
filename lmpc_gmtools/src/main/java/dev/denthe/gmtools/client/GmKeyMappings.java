package dev.denthe.gmtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.denthe.gmtools.GmTools;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Два хоткея, оба переназначаются в настройках управления.
 * Insert — панель, Home — быстрый наблюдатель.
 */
@EventBusSubscriber(modid = GmTools.MODID, value = Dist.CLIENT)
public final class GmKeyMappings {
    private GmKeyMappings() {}

    public static final String CATEGORY = "key.categories.lmpc_gmtools";

    public static final KeyMapping OPEN_PANEL = new KeyMapping(
        "key.lmpc_gmtools.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, CATEGORY);

    public static final KeyMapping TOGGLE_SPECTATOR = new KeyMapping(
        "key.lmpc_gmtools.spectator", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY);

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent e) {
        e.register(OPEN_PANEL);
        e.register(TOGGLE_SPECTATOR);
    }
}
