package dev.denthe.shade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Синхронизация цветокора с сервера. Один пакет сервер → клиент: карта
 * id→строка. Клиент накладывает её поверх своего локального конфига
 * ({@link dev.denthe.shade.client.ShadeSync}), при выходе с сервера —
 * откатывает.
 *
 * bus не указываем — {@link RegisterPayloadHandlersEvent} модовый,
 * шина определяется по типу события (как в plaguecore и lmpc_gmtools).
 */
@EventBusSubscriber(modid = LmpcShade.MODID)
public final class ShadeNet {
    private ShadeNet() {}

    private static final String VERSION = "1";

    public record Sync(Map<String, String> values) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LmpcShade.MODID, "sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.values.size());
                p.values.forEach((k, v) -> {
                    buf.writeUtf(k, 64);
                    buf.writeUtf(v, 64);
                });
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > 128) throw new IllegalArgumentException("shade sync size: " + n);
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) m.put(buf.readUtf(64), buf.readUtf(64));
                return new Sync(m);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToClient(Sync.TYPE, Sync.CODEC, (payload, ctx) ->
            ctx.enqueueWork(() -> dev.denthe.shade.client.ShadeSync.apply(payload.values())));
    }
}
