package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

    private static final String VERSION = "3";

    /**
     * dim: 0 — Обычный, 1 — Ад, 2 — Край, 3 — прочее (Ада и Края в игре
     * не будет, поле оставлено на будущее).
     * mode: id режима игры (0 выживание … 3 наблюдатель).
     */
    public record Pos(UUID id, String name, float x, float z, byte dim,
                      float health, byte food, byte mode) {}

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
                    buf.writeFloat(pos.health());
                    buf.writeByte(pos.food());
                    buf.writeByte(pos.mode());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > 500) throw new IllegalArgumentException("подозрительное число игроков: " + n);
                List<Pos> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Pos(buf.readUUID(), buf.readUtf(48),
                        buf.readFloat(), buf.readFloat(), buf.readByte(),
                        buf.readFloat(), buf.readByte(), buf.readByte()));
                }
                return new Positions(list);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Инвентарь игрока: 36 основных + 4 брони + рука = 41 слот. */
    public record Inventory(String name, List<ItemStack> slots) implements CustomPacketPayload {

        public static final Type<Inventory> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GmTools.MODID, "inventory"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Inventory> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.name(), 48);
                buf.writeVarInt(p.slots().size());
                for (ItemStack s : p.slots()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
            },
            buf -> {
                String name = buf.readUtf(48);
                int n = buf.readVarInt();
                if (n < 0 || n > 256) throw new IllegalArgumentException("слотов: " + n);
                List<ItemStack> slots = new ArrayList<>(n);
                for (int i = 0; i < n; i++) slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                return new Inventory(name, slots);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Общие метки на карте (видят все операторы). */
    public record Mark(String name, float x, float z) {}

    public record Marks(List<Mark> marks) implements CustomPacketPayload {

        public static final Type<Marks> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(GmTools.MODID, "marks"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Marks> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.marks().size());
                for (Mark m : p.marks()) {
                    buf.writeUtf(m.name(), 48);
                    buf.writeFloat(m.x());
                    buf.writeFloat(m.z());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > 500) throw new IllegalArgumentException("меток: " + n);
                List<Mark> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(new Mark(buf.readUtf(48), buf.readFloat(), buf.readFloat()));
                return new Marks(list);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(VERSION);
        r.playToClient(Positions.TYPE, Positions.CODEC, (payload, ctx) -> ctx.enqueueWork(
            () -> dev.denthe.gmtools.client.GmMapClientAccess.accept(payload)));
        r.playToClient(Inventory.TYPE, Inventory.CODEC, (payload, ctx) -> ctx.enqueueWork(
            () -> dev.denthe.gmtools.client.GmMapClientAccess.acceptInventory(payload)));
        r.playToClient(Marks.TYPE, Marks.CODEC, (payload, ctx) -> ctx.enqueueWork(
            () -> dev.denthe.gmtools.client.GmMapClientAccess.acceptMarks(payload)));
    }
}
