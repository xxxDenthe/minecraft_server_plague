package dev.denthe.plaguecore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Экран «Состояние здоровья»: человек осматривает сам себя.
 * Спек интерфейса, раздел 7.
 *
 * Экран ничего не решает и ничего не спрашивает у сервера — он рисует
 * то, что собрал {@link HealthSense}. Ни одного числа болезни наружу
 * не выходит: ни заражения, ни стадии, ни порогов.
 */
public class HealthScreen extends Screen {

    /** Размер панели. Сундук — 176 × 166; здесь шире, текст длинный. */
    protected static final int ШИРИНА = 248, ВЫСОТА = 166;

    protected static final int ФОН = 0xFF101410;
    protected static final int РАМКА = 0xFF4A4A4A;
    protected static final int ТЕКСТ = 0xFFE0E0E0;
    protected static final int ТУСКЛЫЙ = 0xFF909090;

    protected int левый, верхний;

    protected HealthScreen() {
        super(Component.translatable("plaguecore.health.title"));
    }

    /** Открыть осмотр самого себя. */
    public static void открытьСвой() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen());
    }

    /**
     * Игру не останавливаем. Сервер сессионный, пауза там всё равно
     * не работает, а стоять с открытым экраном посреди гнили должно
     * быть опасно.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        левый = (width - ШИРИНА) / 2;
        верхний = (height - ВЫСОТА) / 2;
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        renderBackground(графика, мышьX, мышьY, кадр);
        панель(графика);
        графика.drawString(font, title, левый + 8, верхний + 8, ТЕКСТ, false);
        super.render(графика, мышьX, мышьY, кадр);
    }

    /** Тёмная панель с тонкой рамкой. Палитра — от админского экрана карты. */
    protected void панель(GuiGraphics графика) {
        int x = левый, y = верхний;
        графика.fill(x - 1, y - 1, x + ШИРИНА + 1, y + ВЫСОТА + 1, РАМКА);
        графика.fill(x, y, x + ШИРИНА, y + ВЫСОТА, ФОН);
    }
}
