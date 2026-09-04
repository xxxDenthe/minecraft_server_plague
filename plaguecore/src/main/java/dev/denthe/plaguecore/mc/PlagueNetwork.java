package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Сеть для админского экрана. Спек, раздел 12.1.
 *
 * Два пакета: снимок сетки на клиент и действие с клиента на сервер.
 *
 * Действие приходит номером, а не строкой команды. Это принципиально:
 * если бы клиент присылал текст для диспетчера, любой игрок с изменённым
 * клиентом отправил бы туда что угодно. Номер и два числа сервер
 * проверяет сам и сам собирает команду.
 */
// bus не указываем: в 21.1 шина определяется по типу события,
// а RegisterPayloadHandlersEvent — модовое (IModBusEvent)
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueNetwork {
    private PlagueNetwork() {}

    /** Версия протокола. Меняется, если поменяется формат пакетов. */
    private static final String VERSION = "1";

    // ── номера действий ────────────────────────────────────────────────
    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_NIGHT = 1;
    public static final int ACTION_FASTFORWARD = 2; // a = сколько ночей
    public static final int ACTION_PAUSE = 3;
    public static final int ACTION_RESUME = 4;
    public static final int ACTION_GENERATE = 5;    // a = проценты
    public static final int ACTION_SEED = 6;        // a = cx, b = cz
    public static final int ACTION_REMOVE = 7;      // a = cx, b = cz

    /** Снимок состояния для экрана. Уровни — 3969 байт, это меньше пакета чата с картинкой. */
    public record Snapshot(int size, int originX, int originZ,
                           int night, int phase, boolean paused,
                           boolean terrainReady, int epicenterCount,
                           byte[] levels) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Snapshot> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "snapshot"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
            StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.size);
                    buf.writeVarInt(s.originX);
                    buf.writeVarInt(s.originZ);
                    buf.writeVarInt(s.night);
                    buf.writeVarInt(s.phase);
                    buf.writeBoolean(s.paused);
                    buf.writeBoolean(s.terrainReady);
                    buf.writeVarInt(s.epicenterCount);
                    buf.writeVarInt(s.levels.length);
                    buf.writeBytes(s.levels);
                },
                buf -> {
                    int size = buf.readVarInt();
                    int ox = buf.readVarInt();
                    int oz = buf.readVarInt();
                    int night = buf.readVarInt();
                    int phase = buf.readVarInt();
                    boolean paused = buf.readBoolean();
                    boolean terrain = buf.readBoolean();
                    int epicenters = buf.readVarInt();
                    int len = buf.readVarInt();
                    if (len < 0 || len > 1 << 20) {
                        throw new IllegalArgumentException("подозрительная длина сетки: " + len);
                    }
                    byte[] levels = new byte[len];
                    buf.readBytes(levels);
                    return new Snapshot(size, ox, oz, night, phase, paused, terrain, epicenters, levels);
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

        public int levelAt(int cx, int cz) {
            int dx = cx - originX;
            int dz = cz - originZ;
            if (dx < 0 || dx >= size || dz < 0 || dz >= size) return 0;
            return levels[dz * size + dx];
        }

        public int countInfected() {
            int n = 0;
            for (byte b : levels) if (b > 0) n++;
            return n;
        }
    }

    /** Действие с админского экрана. */
    public record Action(int action, int a, int b) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Action> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
            StreamCodec.of(
                (buf, x) -> {
                    buf.writeVarInt(x.action);
                    buf.writeVarInt(x.a);
                    buf.writeVarInt(x.b);
                },
                buf -> new Action(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Стадия игрока на клиент. Одно число: клиенту незачем знать
     * точную заражённость, а нам незачем её ему доверять.
     */
    public record Stage(int стадия) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Stage> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "stage"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Stage> CODEC =
            StreamCodec.of(
                (buf, s) -> buf.writeVarInt(s.стадия),
                buf -> new Stage(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void зарегистрировать(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьСнимок(payload)));

        registrar.playToClient(Stage.TYPE, Stage.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьСтадию(payload)));

        registrar.playToServer(Action.TYPE, Action.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) выполнить(player, payload);
            }));
    }

    /**
     * Выполнение действия на сервере.
     *
     * Права проверяются здесь, а не только в GUI: экран можно открыть
     * подменённым клиентом, поэтому доверять ему нельзя. Сама работа
     * делегируется тем же командам /plague — чтобы логика жила
     * в одном месте, а не в двух.
     */
    private static void выполнить(ServerPlayer player, Action a) {
        if (!player.hasPermissions(2)) return;

        String команда = switch (a.action()) {
            case ACTION_NIGHT -> "plague night";
            case ACTION_FASTFORWARD -> "plague fastforward " + Mth.clamp(a.a(), 1, 500);
            case ACTION_PAUSE -> "plague pause";
            case ACTION_RESUME -> "plague resume";
            case ACTION_GENERATE -> "plague generate " + (Mth.clamp(a.a(), 1, 100) / 100f);
            case ACTION_SEED -> "plague seed " + (a.a() * 16 + 8) + " " + (a.b() * 16 + 8);
            case ACTION_REMOVE -> "plague remove " + (a.a() * 16 + 8) + " " + (a.b() * 16 + 8);
            default -> null; // ACTION_REFRESH и всё неизвестное — только перерисовка
        };

        if (команда != null) {
            player.server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), команда);
        }
        отправитьСнимок(player);
    }

    /** Отправить игроку свежий снимок состояния. */
    public static void отправитьСнимок(ServerPlayer player) {
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        PlagueState st = PlagueState.get(overworld);
        PlagueGrid g = st.grid();

        PacketDistributor.sendToPlayer(player, new Snapshot(
            g.size(), g.originX(), g.originZ(),
            st.night(), st.phase(), st.isPaused(),
            st.isTerrainInitialized(), st.epicenters().size(),
            g.levelsCopy()));
    }

    /** Сказать игроку его стадию. Шлётся при смене, а не каждый тик. */
    public static void отправитьСтадию(ServerPlayer игрок, int стадия) {
        PacketDistributor.sendToPlayer(игрок, new Stage(стадия));
    }
}
