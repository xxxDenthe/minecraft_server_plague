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

    @SubscribeEvent
    public static void нарисовать(RenderGuiEvent.Post событие) {
        boolean ведут = PossessionClient.ведут();
        int стадия = PlagueClientAccess.стадия();
        if (!ведут && (стадия < 2 || стадия >= ПЛОТНОСТЬ.length)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float такт = mc.player.tickCount
            + событие.getPartialTick().getGameTimeDeltaPartialTick(false);

        // Одержимость перебивает стадию: человек обязан понять, что это чума,
        // а не лаги, — иначе полезет перезаходить и оборвёт сессию.
        float плотность = ведут ? ОДЕРЖИМОСТЬ : ПЛОТНОСТЬ[стадия];
        float размах = ведут ? РАЗМАХ_ПУЛЬСА : РАЗМАХ;
        float период = ведут ? 6f : 25f;

        float дыхание = Mth.sin(такт / период) * размах;
        float альфа = Mth.clamp(плотность + дыхание, 0f, 0.85f);

        GuiGraphics графика = событие.getGuiGraphics();
        int цвет = ((int) (альфа * 255f) << 24);   // чёрный с нужной прозрачностью
        графика.fill(0, 0, графика.guiWidth(), графика.guiHeight(), цвет);
    }
}
