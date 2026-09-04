package dev.denthe.classes.client;

import dev.denthe.classes.ClassItems;
import dev.denthe.classes.ClassNetwork;
import dev.denthe.classes.ClassesConfig;
import dev.denthe.classes.LmpcClasses;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;

/**
 * Наведение Клирика на союзника с отваром в руке. Спек, раздел 4.
 *
 * Держит ПКМ на игроке-цели {@code clericFeedChannelTicks} тиков —
 * если цель за это время сдвинулась дальше чем на пол-блока, канал
 * срывается и прогресс сгорает. Полностью клиентское: сервер видит
 * только готовый {@link ClassNetwork.FeedRequest} и сам всё проверяет
 * заново, этот класс отвечает только за глаз и за то, когда его послать.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = LmpcClasses.MODID)
public final class BrewTargeting {
    private BrewTargeting() {}

    private static Player цель;
    private static int прогресс;
    private static Vec3 позицияПриСтарте;
    private static boolean отправлено;

    @SubscribeEvent
    public static void тик(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        Player игрок = mc.player;
        if (игрок == null || mc.level == null) {
            сброситьЦель();
            return;
        }

        Player новаяЦель = найтиЦель(mc, игрок);
        if (новаяЦель != цель) {
            сброситьЦель();
            цель = новаяЦель;
            if (цель != null) обвести(цель, true);
        }
        if (цель == null) return;

        boolean держитКнопку = mc.options.keyUse.isDown();
        if (!держитКнопку) {
            прогресс = 0;
            позицияПриСтарте = null;
            отправлено = false;
            return;
        }

        if (позицияПриСтарте == null) позицияПриСтарте = цель.position();
        else if (цель.position().distanceToSqr(позицияПриСтарте) > 0.25) {
            прогресс = 0;
            позицияПриСтарте = цель.position();
        }

        if (отправлено) return;
        прогресс++;
        if (прогресс >= ClassesConfig.скормитьДлительностьТики()) {
            PacketDistributor.sendToServer(new ClassNetwork.FeedRequest(цель.getUUID()));
            отправлено = true;
            прогресс = 0;
        }
    }

    private static Player найтиЦель(Minecraft mc, Player игрок) {
        if (!держитОтвар(игрок)) return null;
        HitResult попадание = mc.hitResult;
        if (!(попадание instanceof EntityHitResult сущность)) return null;
        if (!(сущность.getEntity() instanceof Player другой) || другой == игрок) return null;
        return другой;
    }

    private static boolean держитОтвар(Player игрок) {
        return естьВРуке(игрок.getMainHandItem()) || естьВРуке(игрок.getOffhandItem());
    }

    private static boolean естьВРуке(ItemStack стопка) {
        return стопка.is(ClassItems.CLERICS_BREW.get());
    }

    private static void сброситьЦель() {
        if (цель != null) обвести(цель, false);
        цель = null;
        прогресс = 0;
        позицияПриСтарте = null;
        отправлено = false;
    }

    @SubscribeEvent
    public static void надпись(RenderNameTagEvent событие) {
        if (!(событие.getEntity() instanceof Player игрокВКадре) || игрокВКадре != цель) return;

        событие.setCanRender(TriState.TRUE);
        if (!Minecraft.getInstance().options.keyUse.isDown() || отправлено) {
            событие.setContent(Component.literal("⚕ ПКМ — напоить").withStyle(ChatFormatting.GOLD));
            return;
        }

        int длительность = ClassesConfig.скормитьДлительностьТики();
        int сегментов = 10;
        int заполнено = Math.min(сегментов, прогресс * сегментов / Math.max(1, длительность));
        StringBuilder бар = new StringBuilder();
        for (int i = 0; i < сегментов; i++) бар.append(i < заполнено ? '█' : '░');

        событие.setContent(Component.literal(бар.toString())
            .withStyle(ChatFormatting.GREEN));
    }

    // ── обводка ───────────────────────────────────────────────────────

    private static Method методОбщегоФлага;
    private static boolean методИскался;

    /**
     * Обводка цели видна сквозь стены — тот же эффект, что у зелья
     * свечения. {@code Entity.setGlowingTag} рассчитан на сервер
     * (синк по сети); нам нужен чисто клиентский, локальный для
     * одного игрока эффект, поэтому — рефлексия на общий флаг сущности,
     * тем же приёмом, что ночное зрение у `lmpc_gmtools` (`Fullbright`).
     */
    private static void обвести(Entity сущность, boolean включить) {
        if (!методИскался) {
            методИскался = true;
            try {
                методОбщегоФлага = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class);
                методОбщегоФлага.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                методОбщегоФлага = null;
            }
        }
        if (методОбщегоФлага == null) return;
        try {
            методОбщегоФлага.invoke(сущность, 6, включить);
        } catch (ReflectiveOperationException e) {
            // молчим — без обводки взаимодействие всё равно работает
        }
    }
}
