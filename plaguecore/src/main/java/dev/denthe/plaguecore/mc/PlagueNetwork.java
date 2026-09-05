package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.VoiceKnobs;
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

import java.util.ArrayList;
import java.util.List;

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
    private static final String VERSION = "4";

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

    /**
     * Ручки голоса на клиент — чтобы ползунки в панели мастера игры
     * встали туда, где сервер их держит на самом деле. Значения идут
     * массивом в порядке {@link VoiceKnobs#ВСЕ}: имена клиент знает сам
     * из того же списка, гонять их по сети незачем.
     */
    public record Voice(float[] значения) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Voice> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "voice"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Voice> CODEC =
            StreamCodec.of(
                (buf, v) -> {
                    buf.writeVarInt(v.значения.length);
                    for (float з : v.значения) buf.writeFloat(з);
                },
                buf -> {
                    float[] з = new float[buf.readVarInt()];
                    for (int i = 0; i < з.length; i++) з[i] = buf.readFloat();
                    return new Voice(з);
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Словарь тайнописи на клиент. Клиент обязан его знать: руны
     * подставляет он сам, при отрисовке книги.
     *
     * Подсказка приходит непустой только Летописцу — решает сервер,
     * а не клиент. Пустая строка вместо подсказки, а не отдельный
     * флаг: так у подменённого клиента нечего включать.
     */
    public record Words(List<Words.Запись> записи) implements CustomPacketPayload {

        public record Запись(String корень, boolean раскрыт, String подсказка) {}

        public static final CustomPacketPayload.Type<Words> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "words"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Words> CODEC =
            StreamCodec.of(
                (buf, w) -> {
                    buf.writeVarInt(w.записи.size());
                    for (Запись з : w.записи) {
                        buf.writeUtf(з.корень(), 64);
                        buf.writeBoolean(з.раскрыт());
                        buf.writeUtf(з.подсказка(), 256);
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    if (n < 0 || n > 4096) {
                        throw new IllegalArgumentException("подозрительный размер словаря: " + n);
                    }
                    List<Запись> список = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        список.add(new Запись(buf.readUtf(64), buf.readBoolean(), buf.readUtf(256)));
                    }
                    return new Words(List.copyOf(список));
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Чужая рука на пульте тела. Идёт жертве каждый тик, пока сессия жива.
     * Заметка 2026-09-06-oderzhimost.
     *
     * Один пакет и на «тебя ведут», и на само нажатие: отдельный пакет
     * начала сессии добавил бы гонку — нажатие пришло бы раньше, чем
     * разрешение его слушать. {@code ведут = false} закрывает сессию,
     * флаги при этом не смотрят.
     *
     * {@code чума} нужна только клиенту, чтобы отличать своё оформление:
     * чума и админ ведут тело совершенно одинаково.
     */
    public record Drive(boolean ведут, boolean чума, int флаги,
                        float рыскание, float тангаж) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Drive> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "drive"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Drive> CODEC =
            StreamCodec.of(
                (buf, d) -> {
                    buf.writeBoolean(d.ведут);
                    buf.writeBoolean(d.чума);
                    buf.writeByte(d.флаги & 0xFF);
                    buf.writeFloat(d.рыскание);
                    buf.writeFloat(d.тангаж);
                },
                buf -> new Drive(buf.readBoolean(), buf.readBoolean(),
                    buf.readByte() & 0xFF, buf.readFloat(), buf.readFloat()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Нажатия админа на сервер. Шлются каждый тик, пока он за пультом.
     *
     * Проверка одна и на сервере: отправитель обязан прямо сейчас вести
     * чьё-то тело. Иначе любой подменённый клиент слал бы сюда мусор,
     * а на том конце труба, которая двигает чужого игрока.
     */
    public record Steer(int флаги, float рыскание, float тангаж) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Steer> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "steer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Steer> CODEC =
            StreamCodec.of(
                (buf, s) -> {
                    buf.writeByte(s.флаги & 0xFF);
                    buf.writeFloat(s.рыскание);
                    buf.writeFloat(s.тангаж);
                },
                buf -> new Steer(buf.readByte() & 0xFF, buf.readFloat(), buf.readFloat()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Кем правит админ. Идёт только ему и только дважды за сессию:
     * номер сущности в начале и -1 в конце.
     *
     * Клиент админа обязан это знать: камеру на чужое тело он ставит сам,
     * картинка по сети не ходит.
     */
    public record Puppet(int сущность) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Puppet> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "puppet"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Puppet> CODEC =
            StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.сущность),
                buf -> new Puppet(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Послать жертве очередное нажатие. */
    public static void отправитьУправление(ServerPlayer жертва, Drive пакет) {
        PacketDistributor.sendToPlayer(жертва, пакет);
    }

    /** Послать игроку словарь тайнописи. */
    public static void отправитьСлова(ServerPlayer кому, List<Words.Запись> записи) {
        PacketDistributor.sendToPlayer(кому, new Words(List.copyOf(записи)));
    }

    /** Послать текущие ручки голоса одному игроку. */
    public static void отправитьГолос(ServerPlayer кому) {
        PacketDistributor.sendToPlayer(кому, new Voice(VoiceKnobs.снимок()));
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

        registrar.playToClient(Words.TYPE, Words.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьСлова(payload)));

        registrar.playToClient(Voice.TYPE, Voice.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьГолос(payload)));

        registrar.playToClient(Drive.TYPE, Drive.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьУправление(payload)));

        registrar.playToClient(Puppet.TYPE, Puppet.CODEC,
            (payload, ctx) -> ctx.enqueueWork(
                () -> dev.denthe.plaguecore.client.PlagueClientAccess.принятьКуклу(payload)));

        registrar.playToServer(Steer.TYPE, Steer.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) Possession.принятьНажатие(player, payload);
            }));

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
