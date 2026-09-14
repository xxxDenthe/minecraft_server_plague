package dev.denthe.plaguecore.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиша «Состояние здоровья». Спек, раздел 12.
 *
 * Y свободна во всём паке — проверено по образцу настроек
 * `launcher/pack-config/config/lmpc-default-options.txt`. Занят только
 * Ctrl+Y отменой в mapwright, а это другой экран.
 *
 * Клавиша регистрируется обычным способом NeoForge и переназначается
 * в стандартных настройках управления: жёстко прибитая клавиша — это
 * гарантированная жалоба от того, у кого её нет на клавиатуре.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthKeys {
    private HealthKeys() {}

    public static final KeyMapping КЛАВИША = new KeyMapping(
        "key.plaguecore.health",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        "key.categories.plaguecore");

    // bus не указываем: в 21.1 шина определяется по типу события,
    // а RegisterKeyMappingsEvent — модовое
    @SubscribeEvent
    public static void зарегистрировать(RegisterKeyMappingsEvent событие) {
        событие.register(КЛАВИША);
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (КЛАВИША.consumeClick()) {
            if (mc.crosshairPickEntity instanceof Player кто && кто != mc.player) {
                PacketDistributor.sendToServer(new PlagueNetwork.Look(кто.getId()));
            } else {
                HealthScreen.открытьСвой();
            }
        }
    }
}
