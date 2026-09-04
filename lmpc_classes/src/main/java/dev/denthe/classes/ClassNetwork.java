package dev.denthe.classes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/**
 * Сеть Клирика: напоить союзника наведением. Спек, раздел 4.
 *
 * Прогресс-бар и обводка живут целиком на клиенте ({@code
 * client.BrewTargeting}) — сервер только получает готовый запрос
 * и сам проверяет класс, дистанцию, руку с отваром и кулдаун цели.
 * Не доверять клиенту здесь — то же правило, что в PlagueNetwork
 * у plaguecore: право проверяется на сервере, а не только в UI.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ClassNetwork {
    private ClassNetwork() {}

    private static final String VERSION = "1";
    private static final double МАКС_ДИСТАНЦИЯ = 6.0;

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

    @SubscribeEvent
    public static void зарегистрировать(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(FeedRequest.TYPE, FeedRequest.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer отправитель) напоить(отправитель, payload.targetId());
            }));
    }

    private static void напоить(ServerPlayer клирик, UUID targetId) {
        if (PlayerClassData.данные(клирик).класс != PlayerClassData.Класс.CLERIC) return;

        if (!(клирик.level() instanceof ServerLevel мир)) return;
        var цель = мир.getServer().getPlayerList().getPlayer(targetId);
        if (цель == null || цель == клирик) return;
        if (цель.level() != мир) return;
        if (клирик.distanceToSqr(цель) > МАКС_ДИСТАНЦИЯ * МАКС_ДИСТАНЦИЯ) return;

        InteractionHand рука = рукаСОтваром(клирик);
        if (рука == null) return;

        PlayerClassData д = PlayerClassData.данные(цель);
        long сейчас = мир.getGameTime();
        if (сейчас < д.отварГотовТик) return;

        ItemStack стопка = клирик.getItemInHand(рука);
        if (!клирик.hasInfiniteMaterials()) {
            стопка.shrink(1);
            ItemStack бутылка = new ItemStack(Items.GLASS_BOTTLE);
            if (стопка.isEmpty()) клирик.setItemInHand(рука, бутылка);
            else if (!клирик.getInventory().add(бутылка)) клирик.drop(бутылка, false);
        }

        PlagueBridge.cure(цель, ClassesConfig.отварЛечение());
        PlagueBridge.grantImmunity(цель, ClassesConfig.отварИммунитетТики());
        д.отварГотовТик = сейчас + ClassesConfig.отварКулдаунТики();

        мир.playSound(null, цель.blockPosition(),
            SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.2f);
        клирик.displayClientMessage(Component.literal(
            "Напоил " + цель.getGameProfile().getName() + "."), true);
        цель.displayClientMessage(Component.literal(
            клирик.getGameProfile().getName() + " напоил вас отваром."), true);
    }

    private static InteractionHand рукаСОтваром(ServerPlayer игрок) {
        if (игрок.getMainHandItem().is(ClassItems.CLERICS_BREW.get())) return InteractionHand.MAIN_HAND;
        if (игрок.getOffhandItem().is(ClassItems.CLERICS_BREW.get())) return InteractionHand.OFF_HAND;
        return null;
    }
}
