package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import dev.denthe.plaguecore.mc.PossessionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентская половина одержимости.
 * Заметка `docs/superpowers/notes/2026-09-06-oderzhimost.md`.
 *
 * Один класс на обе роли, потому что один клиент может оказаться
 * и той и другой: админ на четвёртой стадии — обычное дело.
 *
 * **Жертва.** Её собственные нажатия выбрасываются, вместо них
 * подставляются пришедшие с сервера. Тело при этом двигает всё тот же
 * ванильный код, и сервер видит обычную ходьбу — иначе была бы резинка.
 *
 * **Админ.** Его нажатия снимаются и уходят на сервер, а своему телу
 * не достаются: наблюдатель иначе улетел бы от картинки.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class PossessionClient {
    private PossessionClient() {}

    // ── как жертва ─────────────────────────────────────────────────────
    private static boolean ведут;
    private static boolean чума;
    private static int флаги;
    private static float рыскание;
    private static float тангаж;

    // ── как админ ──────────────────────────────────────────────────────
    /** Номер сущности, которой правим. -1 — не правим никем. */
    private static int кукла = -1;

    /** Снято с клавиатуры в этом тике, ждёт отправки. */
    private static int своиФлаги;

    /** Ведут ли нас прямо сейчас. Читает {@link PlagueOverlay}. */
    public static boolean ведут() { return ведут; }

    /** Ведёт ли нас чума, а не человек. Пока не различаем — задел на оформление. */
    public static boolean чума() { return чума; }

    public static void принять(PlagueNetwork.Drive пакет) {
        ведут = пакет.ведут();
        чума = пакет.чума();
        флаги = пакет.флаги();
        рыскание = пакет.рыскание();
        тангаж = пакет.тангаж();
    }

    public static void принятьКуклу(PlagueNetwork.Puppet пакет) {
        кукла = пакет.сущность();
        if (кукла < 0) вернутьКамеру();
    }

    // ── подмена нажатий ────────────────────────────────────────────────

    /**
     * Зовётся миксином в самом конце {@code KeyboardInput.tick}, то есть
     * когда свои клавиши уже прочитаны, а движение ещё не посчитано.
     * Другой точки нет: раньше — нечего подменять, позже — поздно.
     */
    public static void подменить(Input ввод) {
        if (кукла >= 0) {
            снятьСвои(ввод);
            return;
        }
        if (!ведут) return;

        ввод.up = PossessionRules.есть(флаги, PossessionRules.ВПЕРЁД);
        ввод.down = PossessionRules.есть(флаги, PossessionRules.НАЗАД);
        ввод.left = PossessionRules.есть(флаги, PossessionRules.ВЛЕВО);
        ввод.right = PossessionRules.есть(флаги, PossessionRules.ВПРАВО);
        ввод.jumping = PossessionRules.есть(флаги, PossessionRules.ПРЫЖОК);
        ввод.shiftKeyDown = PossessionRules.есть(флаги, PossessionRules.КРАДУЧИСЬ);
        ввод.forwardImpulse = импульс(ввод.up, ввод.down);
        ввод.leftImpulse = импульс(ввод.left, ввод.right);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Взгляд ставится насильно каждый тик. Между тиками мышь жертвы
        // ещё поворачивает голову, и картинка слегка дёргается — так и надо:
        // это читается как борьба с чумой, а не как поломка.
        mc.player.setYRot(рыскание);
        mc.player.setXRot(тангаж);
        mc.player.yRotO = рыскание;
        mc.player.xRotO = тангаж;
        mc.player.setYHeadRot(рыскание);
    }

    /** Снять нажатия админа и обнулить их, чтобы наблюдатель не улетел. */
    private static void снятьСвои(Input ввод) {
        Minecraft mc = Minecraft.getInstance();
        своиФлаги = PossessionRules.флаги(
            ввод.up, ввод.down, ввод.left, ввод.right,
            ввод.jumping, ввод.shiftKeyDown,
            mc.options.keyAttack.isDown());

        ввод.up = ввод.down = ввод.left = ввод.right = false;
        ввод.jumping = ввод.shiftKeyDown = false;
        ввод.forwardImpulse = 0f;
        ввод.leftImpulse = 0f;
    }

    private static float импульс(boolean вперёд, boolean назад) {
        if (вперёд == назад) return 0f;
        return вперёд ? 1f : -1f;
    }

    // ── тик ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        if (кукла < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Камера ставится не сразу: сущность жертвы появляется на нашем
        // клиенте только после телепорта и подгрузки чанка. Пробуем каждый
        // тик, пока не найдём.
        Entity тело = mc.level.getEntity(кукла);
        if (тело != null && mc.getCameraEntity() != тело) {
            mc.setCameraEntity(тело);
        }

        PacketDistributor.sendToServer(new PlagueNetwork.Steer(
            своиФлаги, mc.player.getYRot(), mc.player.getXRot()));
    }

    private static void вернутьКамеру() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        своиФлаги = 0;
    }
}
