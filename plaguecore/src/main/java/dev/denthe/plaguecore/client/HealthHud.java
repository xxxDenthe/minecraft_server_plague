package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Строка самочувствия на игровом экране. Спек, раздел 8.
 *
 * Левый верхний угол: там ничего нашего нет, а хотбар, чат и прицел
 * остаются свободны. На нулевой ступени не рисуется вовсе — здоровому
 * человеку сообщать нечего, а постоянная строка «я в порядке» за
 * двадцать часов сессии перестала бы читаться.
 *
 * Чем хуже человеку, тем тусклее и тревожнее строка. На краю она
 * начинает дышать, как плёнка {@link PlagueOverlay}: это уже не подпись,
 * а состояние.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthHud {
    private HealthHud() {}

    private static final int X = 4, Y = 4;

    /** Цвет строки по ступеням 0–4. Серый уходит в больной жёлто-серый. */
    private static final int[] ЦВЕТ = {
        0xFFB0B0A8, 0xFFB0B0A8, 0xFFAFA88C, 0xFFAC9A78, 0xFFA88068
    };

    @SubscribeEvent
    public static void нарисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int ступень = HealthSense.ступень();
        if (ступень <= 0) return;

        Component строка = HealthSense.общееКратко();
        GuiGraphics графика = событие.getGuiGraphics();
        int ширина = mc.font.width(строка);

        int л = X - 4, в = Y - 3, п = X + ширина + 3, н = Y + mc.font.lineHeight + 2;
        графика.fill(л, в, п, н, 0x88000000);
        // Тонкая кайма и латунный засек слева — та же отделка, что у
        // планшета осмотра: строка и экран должны читаться как одна вещь.
        графика.fill(л, в, п, в + 1, ПОДЛОЖКА);
        графика.fill(л, н - 1, п, н, ПОДЛОЖКА);
        графика.fill(п - 1, в, п, н, ПОДЛОЖКА);
        графика.fill(л, в, л + 1, н, ЛАТУНЬ);

        графика.drawString(mc.font, строка, X, Y, цвет(mc, ступень), false);
    }

    /** Отделка строки: та же латунь и та же тёмная кайма, что у экрана осмотра. */
    private static final int ЛАТУНЬ = 0xC08A7241;
    private static final int ПОДЛОЖКА = 0x60000000;

    /** На краю строка дышит: неподвижная надпись перестаёт читаться как болезнь. */
    private static int цвет(Minecraft mc, int ступень) {
        int основной = ЦВЕТ[Math.min(ступень, ЦВЕТ.length - 1)];
        if (ступень < 4 || mc.player == null) return основной;

        float дыхание = (Mth.sin(mc.player.tickCount / 9f) + 1f) / 2f;
        int альфа = 140 + Math.round(дыхание * 115f);
        return (альфа << 24) | (основной & 0xFFFFFF);
    }
}
