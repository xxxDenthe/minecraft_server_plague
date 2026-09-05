package dev.denthe.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сеть мода. Спек, разделы 4 и 7.
 *
 * Два пакета:
 * <ul>
 * <li>{@link FeedRequest} — клиент → сервер: Клирик докрутил канал
 *     наведения и просит напоить цель. Прогресс-бар и обводка живут
 *     целиком на клиенте ({@code client.BrewTargeting}), сервер
 *     получает готовый запрос и сам проверяет класс, дистанцию, руку
 *     с отваром и кулдаун цели. Не доверять клиенту здесь — то же
 *     правило, что в PlagueNetwork у plaguecore.</li>
 * <li>{@link Insight} — сервер → клиент: заражённость игроков вокруг
 *     Летописца. Числа считает `plaguecore`, у клиента их взять
 *     неоткуда — заражённость лежит в чужом вложении и на клиент
 *     не синкается.</li>
 * </ul>
 *
 * До 0.6.0 сервер молчал на каждый отказ напоить: игрок три секунды
 * держал ПКМ и не получал ничего, включая объяснение. Теперь у каждой
 * ветки отказа своя строка.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassNetwork {
    private ClassNetwork() {}

    private static final String VERSION = "3";

    /** Клиент закончил канал наведения — просит напоить цель. */
    public record FeedRequest(UUID targetId) implements CustomPacketPayload {
        public static final Type<FeedRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "feed_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FeedRequest> CODEC =
            StreamCodec.of(
                (buf, x) -> buf.writeUUID(x.targetId),
                buf -> new FeedRequest(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Обзор Летописца: кто рядом и насколько заражён. Отправляется
     * только Летописцам и только пока есть что показать.
     */
    public record Insight(List<Запись> записи, int уровеньЧанка) implements CustomPacketPayload {

        /**
         * @param имя          ник игрока
         * @param стадия       стадия чумы 0..4; −1 — `plaguecore` не отвечает
         * @param заражённость очки заражённости; отрицательное — неизвестно
         * @param этоЯ         сам Летописец, для выделения строки
         */
        public record Запись(String имя, int стадия, float заражённость, boolean этоЯ) {}

        /** Больше строк экран всё равно не покажет — и заодно потолок на размер пакета. */
        public static final int МАКС_ЗАПИСЕЙ = 16;

        public static final Type<Insight> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "insight"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Insight> CODEC =
            StreamCodec.of(
                (buf, x) -> {
                    buf.writeVarInt(x.уровеньЧанка + 1);
                    buf.writeVarInt(x.записи.size());
                    for (Запись з : x.записи) {
                        buf.writeUtf(з.имя(), 32);
                        buf.writeVarInt(з.стадия() + 1);   // −1 не влезает в VarInt дёшево
                        buf.writeFloat(з.заражённость());
                        buf.writeBoolean(з.этоЯ());
                    }
                },
                buf -> {
                    int уровень = buf.readVarInt() - 1;
                    int сколько = Math.min(buf.readVarInt(), МАКС_ЗАПИСЕЙ);
                    List<Запись> список = new ArrayList<>(сколько);
                    for (int i = 0; i < сколько; i++) {
                        список.add(new Запись(
                            buf.readUtf(32), buf.readVarInt() - 1, buf.readFloat(), buf.readBoolean()));
                    }
                    return new Insight(List.copyOf(список), уровень);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Снимок Летописца: точные уровни заражения чанков вокруг точки
     * съёмки. Спек, раздел 7 — эффект достаётся <b>всей партии</b>,
     * а не только автору снимка, поэтому пакет уходит всем.
     *
     * Сетка передаётся плоским массивом байт стороной {@code сторона}
     * чанков: это десятки байт, дешевле любой структуры и ровно то,
     * что рисует экран.
     *
     * @param чанкX   чанк левого верхнего угла сетки
     * @param чанкZ   чанк левого верхнего угла сетки
     * @param сторона сторона квадрата в чанках
     * @param уровни  уровни 0..5 построчно; −1 закодирован как 0xFF
     * @param тиков   сколько снимок живёт на экране
     * @param автор   ник Летописца — снимок подписан, это лоровая вещь
     */
    public record Snapshot(int чанкX, int чанкZ, int сторона, byte[] уровни, int тиков, String автор)
            implements CustomPacketPayload {

        /** Потолок на размер пакета: 21×21 чанка — треть мира, больше незачем. */
        public static final int МАКС_СТОРОНА = 21;

        public static final Type<Snapshot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "snapshot"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
            StreamCodec.of(
                (buf, x) -> {
                    buf.writeVarInt(x.чанкX);
                    buf.writeVarInt(x.чанкZ);
                    buf.writeVarInt(x.сторона);
                    buf.writeByteArray(x.уровни);
                    buf.writeVarInt(x.тиков);
                    buf.writeUtf(x.автор, 32);
                },
                buf -> {
                    int x = buf.readVarInt();
                    int z = buf.readVarInt();
                    int сторона = Math.min(buf.readVarInt(), МАКС_СТОРОНА);
                    byte[] уровни = buf.readByteArray(МАКС_СТОРОНА * МАКС_СТОРОНА);
                    return new Snapshot(x, z, сторона, уровни, buf.readVarInt(), buf.readUtf(32));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void зарегистрировать(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(FeedRequest.TYPE, FeedRequest.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer отправитель) напоить(отправитель, payload.targetId());
            }));

        // Обработчик клиентский, но метод-посредник свой: ссылка на класс
        // из `client` внутри лямбды заставила бы выделенный сервер грузить
        // его при регистрации. Так он грузится только когда пакет реально
        // пришёл, то есть только на клиенте.
        registrar.playToClient(Insight.TYPE, Insight.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> приняли(payload)));

        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> принялиСнимок(payload)));
    }

    private static void приняли(Insight payload) {
        dev.denthe.classes.client.ChroniclerHud.принять(payload);
    }

    private static void принялиСнимок(Snapshot payload) {
        dev.denthe.classes.client.SnapshotHud.принять(payload);
    }

    private static void напоить(ServerPlayer клирик, UUID targetId) {
        if (PlayerClassData.данные(клирик).класс != PlayerClassData.Класс.CLERIC) {
            отказ(клирик, "msg.lmpc_classes.feed.not_cleric");
            return;
        }
        if (!(клирик.level() instanceof ServerLevel мир)) return;

        ServerPlayer цель = мир.getServer().getPlayerList().getPlayer(targetId);
        if (цель == null || цель == клирик || цель.level() != мир) return;

        double макс = ClassesConfig.скормитьДистанция();
        if (клирик.distanceToSqr(цель) > макс * макс) {
            отказ(клирик, "msg.lmpc_classes.feed.too_far");
            return;
        }

        InteractionHand рука = рукаСОтваром(клирик);
        if (рука == null) {
            отказ(клирик, "msg.lmpc_classes.feed.no_brew");
            return;
        }

        long осталось = ClericsBrewItem.применить(клирик, цель);
        if (осталось > 0) {
            клирик.displayClientMessage(Component.translatable(
                "msg.lmpc_classes.feed.target_cooldown",
                цель.getGameProfile().getName(), ClassSwitch.минутОсталось(осталось)), true);
            return;
        }

        потратитьОтвар(клирик, рука);
        клирик.displayClientMessage(Component.translatable(
            "msg.lmpc_classes.feed.done", цель.getGameProfile().getName()), true);
        цель.displayClientMessage(Component.translatable(
            "msg.lmpc_classes.feed.received", клирик.getGameProfile().getName()), true);
    }

    private static void отказ(ServerPlayer кому, String ключ) {
        кому.displayClientMessage(Component.translatable(ключ), true);
    }

    /** Бутылка тратится только после того, как отвар реально подействовал. */
    private static void потратитьОтвар(ServerPlayer клирик, InteractionHand рука) {
        if (клирик.hasInfiniteMaterials()) return;
        ItemStack стопка = клирик.getItemInHand(рука);
        стопка.shrink(1);
        ItemStack бутылка = new ItemStack(Items.GLASS_BOTTLE);
        if (стопка.isEmpty()) клирик.setItemInHand(рука, бутылка);
        else if (!клирик.getInventory().add(бутылка)) клирик.drop(бутылка, false);
    }

    private static InteractionHand рукаСОтваром(ServerPlayer игрок) {
        if (игрок.getMainHandItem().is(ClassItems.CLERICS_BREW.get())) return InteractionHand.MAIN_HAND;
        if (игрок.getOffhandItem().is(ClassItems.CLERICS_BREW.get())) return InteractionHand.OFF_HAND;
        return null;
    }
}
