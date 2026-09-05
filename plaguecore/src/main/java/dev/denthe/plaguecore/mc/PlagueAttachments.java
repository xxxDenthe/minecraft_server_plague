package dev.denthe.plaguecore.mc;

import com.mojang.serialization.Codec;
import dev.denthe.plaguecore.PlagueCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Личное заражение игрока — заглушка до подсистемы 2, дизайна которой ещё
 * нет. Стадия 0..4 хранится как обычное вложение (attachment) на игроке,
 * переживает перезапуск сервера.
 *
 * Пока стадию некому считать по-настоящему: она правится только командой
 * `/plague setstage`, вручную, для проверки визуальных эффектов
 * ({@link PlayerInfectionEffects}). Когда подсистема 2 получит дизайн,
 * настоящий расчёт станет писать сюда же — держатель эффектов не изменится.
 */
public final class PlagueAttachments {
    private PlagueAttachments() {}

    private static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PlagueCore.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> СТАДИЯ =
        ВЛОЖЕНИЯ.register("infection_stage", () -> AttachmentType.builder(() -> 0)
            .serialize(Codec.intRange(0, 4))
            .build());

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
