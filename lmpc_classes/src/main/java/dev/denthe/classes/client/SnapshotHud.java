package dev.denthe.classes.client;

import dev.denthe.classes.ClassNetwork;
import dev.denthe.classes.LmpcClasses;
import dev.denthe.classes.SnapshotGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Снимок Летописца на экране — маленькая карта заражения вокруг точки
 * съёмки. Спек классов, раздел 7: снимок «открывает всей партии точные
 * уровни заражения чанков в радиусе снимка, а не только под ногами».
 *
 * Панель видят все, а не только Летописец: в этом весь смысл активки —
 * она работает на партию. Живёт снимок ограниченное время
 * ({@code chroniclerSnapshotMinutes}) и гаснет сам.
 *
 * <p>Карта нарисована заливками, без текстур: каждый чанк — квадратик,
 * цвет по уровню заражения, белая рамка — чанк, где стоит смотрящий.
 * Заводить ради этого модель, атлас или экран было бы дороже самой
 * способности.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = LmpcClasses.MODID)
public final class SnapshotHud {
    private SnapshotHud() {}

    /** Сторона квадратика чанка на экране, пиксели. */
    private static final int КЛЕТКА = 7;

    private static ClassNetwork.Snapshot снимок;
    private static long гаснетТик = Long.MIN_VALUE;

    /** Пришёл снимок. Зовётся из {@code ClassNetwork} уже в потоке клиента. */
    public static void принять(ClassNetwork.Snapshot пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        снимок = пакет;
        гаснетТик = mc.level.getGameTime() + пакет.тиков();
    }

    @SubscribeEvent
    public static void рисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (снимок == null || mc.level.getGameTime() > гаснетТик) return;

        int сторона = снимок.сторона();
        if (сторона <= 0) return;

        GuiGraphics графика = событие.getGuiGraphics();
        int карта = сторона * КЛЕТКА;
        Component подпись = Component.translatable("hud.lmpc_classes.snapshot", снимок.автор());
        int ширина = Math.max(карта, mc.font.width(подпись)) + 8;
        int высота = карта + mc.font.lineHeight + 12;

        // Правый верхний угол: слева уже живёт летопись Летописца.
        int x = событие.getGuiGraphics().guiWidth() - ширина - 4;
        int y = 4;

        графика.fill(x, y, x + ширина, y + высота, 0x88120E08);
        графика.renderOutline(x, y, ширина, высота, 0x664A3A7A);
        графика.drawString(mc.font, подпись.copy().withStyle(ChatFormatting.BOLD),
            x + 4, y + 4, ClassStyle.цвет(dev.denthe.classes.PlayerClassData.Класс.CHRONICLER), false);

        int картаX = x + 4;
        int картаY = y + mc.font.lineHeight + 8;
        int мойЧанкX = mc.player.blockPosition().getX() >> 4;
        int мойЧанкZ = mc.player.blockPosition().getZ() >> 4;

        byte[] уровни = снимок.уровни();
        for (int dz = 0; dz < сторона; dz++) {
            for (int dx = 0; dx < сторона; dx++) {
                int индекс = SnapshotGrid.индекс(dx, dz, сторона);
                int уровень = индекс < уровни.length
                    ? SnapshotGrid.распаковать(уровни[индекс]) : SnapshotGrid.НЕТ_ДАННЫХ;
                int левый = картаX + dx * КЛЕТКА;
                int верхний = картаY + dz * КЛЕТКА;
                графика.fill(левый, верхний, левый + КЛЕТКА - 1, верхний + КЛЕТКА - 1, цвет(уровень));

                if (снимок.чанкX() + dx == мойЧанкX && снимок.чанкZ() + dz == мойЧанкZ) {
                    графика.renderOutline(левый - 1, верхний - 1, КЛЕТКА + 1, КЛЕТКА + 1, 0xFFF0E6D2);
                }
            }
        }
    }

    /**
     * Цвет клетки по уровню заражения. Палитра проекта: чума тёмная
     * и серая, к Гнили уходит в бурый, лиловый — только на предельном
     * уровне, редким акцентом.
     */
    private static int цвет(int уровень) {
        return switch (уровень) {
            case 0 -> 0xFF3A4A2E;
            case 1 -> 0xFF5A5A2E;
            case 2 -> 0xFF6B4A2A;
            case 3 -> 0xFF6B2E2A;
            case 4 -> 0xFF4A2A3A;
            case 5 -> 0xFF4A3A7A;
            default -> 0x40202020;   // вне сетки мира — почти прозрачная дыра
        };
    }
}
