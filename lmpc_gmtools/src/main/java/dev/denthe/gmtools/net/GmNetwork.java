package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сеть карты игроков. Один пакет: сервер раз в секунду шлёт OP-игрокам
 * позиции всех, кто онлайн. Клиент видит координаты только ближних
 * игроков, поэтому «карта всех» без сервера невозможна.
 *
 * bus не указываем — RegisterPayloadHandlersEvent модовый, шина
 * определяется по типу события (как в plaguecore).
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmNetwork {
    private GmNetwork() {}

    private static final String VERSION = "1";

    /** dim: 0 — Обычный, 1 — Ад, 2 — Край, 3 — прочее. */
    public record Pos(UUID id, String name, float x, float z, byte dim) {}

    public record Positions(List<Pos> players) implements CustomPacketPayload {

        public static final Type<Positions> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GmTools.MODID, "positions"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Positions> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.players.size());
                for (Pos pos : p.players) {
                    buf.writeUUID(pos.id());
                    buf.writeUtf(pos.name(), 48);
                    buf.writeFloat(pos.x());
                    buf.writeFloat(pos.z());
                    buf.writeByte(pos.dim());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > 500) throw new IllegalArgumentException("подозрительное число игроков: " + n);
                List<Pos> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Pos(buf.readUUID(), buf.readUtf(48),
                        buf.readFloat(), buf.readFloat(), buf.readByte()));
                }
                return new Positions(list);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToClient(Positions.TYPE, Positions.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.gmtools.client.GmMapClientAccess.accept(payload)));
    }
}
