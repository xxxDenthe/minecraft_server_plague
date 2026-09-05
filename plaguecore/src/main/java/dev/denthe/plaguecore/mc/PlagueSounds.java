package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Свои звуки мода. Пока один — кашель.
 *
 * Вариантов семь, Minecraft сам берёт случайный из sounds.json: один
 * и тот же сэмпл каждые полминуты приедается за первый же вечер.
 */
public final class PlagueSounds {
    private PlagueSounds() {}

    public static final DeferredRegister<SoundEvent> ЗВУКИ =
        DeferredRegister.create(Registries.SOUND_EVENT, PlagueCore.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_COUGH =
        ЗВУКИ.register("player_cough", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "player_cough")));

    public static void register(IEventBus шина) {
        ЗВУКИ.register(шина);
    }
}
