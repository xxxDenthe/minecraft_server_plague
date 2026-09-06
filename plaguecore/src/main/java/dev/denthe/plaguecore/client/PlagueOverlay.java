package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Экран больного тускнеет. Спек подсистемы 2, раздел 7.
 *
 * Своими руками, а не через клиентский мод `lmpc_shade` второго
 * участника: лезть в чужой шейдер значит согласовывать версии между
 * двумя владельцами папок ради тридцати строк своего кода.
 *
 * Рисуем поверх всего интерфейса полупрозрачный прямоугольник —
 * так же, как ваниль рисует иней и тыкву на голове. Дыхание задаёт
 * синус: неподвижная плёнка через минуту перестаёт читаться как болезнь.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class PlagueOverlay {
    private PlagueOverlay() {}

    /** Плотность плёнки по стадиям 0–4, 0..1. */
    private static final float[] ПЛОТНОСТЬ = { 0f, 0f, 0.18f, 0.34f, 0.42f };

    /** Насколько плотность гуляет от дыхания. */
    private static final float РАЗМАХ = 0.06f;

    /** Плотность плёнки, пока тело ведут чужие руки. */
    private static final float ОДЕРЖИМОСТЬ = 0.70f;

    /** Насколько сильнее гуляет плёнка при одержимости: это уже не дыхание, а пульс. */
    private static final float РАЗМАХ_ПУЛЬСА = 0.12f;

    /** Тиков на разгон вспышки до белизны. 4 — примерно 0.2 секунды. */
    private static final float ВСПЫШКА_РАЗГОН = 4f;

    /** Тиков полной белизны. 10 — половина секунды. */
    private static final float ВСПЫШКА_ДЕРЖИМ = 10f;

    /** Тиков на угасание. 40 — две секунды. */
    private static final float ВСПЫШКА_УГАСАНИЕ = 40f;

    @SubscribeEvent
    public static void нарисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics графика = событие.getGuiGraphics();
        float частичный = событие.getPartialTick().getGameTimeDeltaPartialTick(false);

        // Вспышка перебивает всё: белое поверх чёрной плёнки даёт серую
        // грязь вместо ослепления, а момент один на всю сессию.
        float белизна = вспышка(mc, частичный);
        if (белизна > 0f) {
            залить(графика, 0xFFFFFF, белизна);
            return;
        }

        boolean ведут = PossessionClient.ведут();
        int стадия = PlagueClientAccess.стадия();
        if (!ведут && (стадия < 2 || стадия >= ПЛОТНОСТЬ.length)) return;

        float такт = mc.player.tickCount + частичный;

        // Одержимость перебивает стадию: человек обязан понять, что это чума,
        // а не лаги, — иначе полезет перезаходить и оборвёт сессию.
        float плотность = ведут ? ОДЕРЖИМОСТЬ : ПЛОТНОСТЬ[стадия];
        float размах = ведут ? РАЗМАХ_ПУЛЬСА : РАЗМАХ;
        float период = ведут ? 6f : 25f;

        float дыхание = Mth.sin(такт / период) * размах;
        залить(графика, 0x000000, Mth.clamp(плотность + дыхание, 0f, 0.85f));
    }

    /** Насколько экран сейчас белый, 0..1. Ноль — вспышки нет. */
    private static float вспышка(Minecraft mc, float частичный) {
        long начало = PlagueClientAccess.вспышкаС();
        if (начало < 0L || mc.level == null) return 0f;

        float прошло = (mc.level.getGameTime() - начало) + частичный;
        if (прошло < 0f) return 0f;
        if (прошло < ВСПЫШКА_РАЗГОН) return прошло / ВСПЫШКА_РАЗГОН;
        if (прошло < ВСПЫШКА_РАЗГОН + ВСПЫШКА_ДЕРЖИМ) return 1f;

        float угасает = прошло - ВСПЫШКА_РАЗГОН - ВСПЫШКА_ДЕРЖИМ;
        if (угасает >= ВСПЫШКА_УГАСАНИЕ) return 0f;
        return 1f - угасает / ВСПЫШКА_УГАСАНИЕ;
    }

    private static void залить(GuiGraphics графика, int цвет, float альфа) {
        if (альфа <= 0f) return;
        int с = ((int) (Mth.clamp(альфа, 0f, 1f) * 255f) << 24) | цвет;
        графика.fill(0, 0, графика.guiWidth(), графика.guiHeight(), с);
    }
}
