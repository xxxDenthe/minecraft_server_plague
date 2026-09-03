package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Просмотр чужого инвентаря (только чтение). Снимок приходит с сервера
 * по команде /gmtools inv, слоты: 0–8 пояс, 9–35 рюкзак, 36–39 броня,
 * 40 левая рука.
 */
public class InventoryViewScreen extends Screen {

    private static final int SLOT = 18;
    private static final int PANEL = 0xF01A1E1D;
    private static final int SHADOW = 0x90000000;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT = 0xFFE6E8E6;
    private static final int DIM = 0xFF8A9490;
    private static final int SLOT_BG = 0x40000000;

    private final String target;
    private int px, py, pw, ph, gridX, gridY, armorX;
    private long armedClear;

    public InventoryViewScreen(String target) {
        super(Component.literal("Инвентарь"));
        this.target = target;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        pw = 250;
        ph = 210;
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        armorX = px + 16;
        gridX = armorX + SLOT + 10;
        gridY = py + 40;

        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Обновить"),
            b -> send("gmtools inv " + target)).bounds(px + 14, by, 74, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Очистить"), b -> {
            if (armedClear != 0 && System.currentTimeMillis() - armedClear < 3000) {
                send("clear " + target);
                onClose();
            } else {
                armedClear = System.currentTimeMillis();
            }
        }).bounds(px + 92, by, 74, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(px + pw - 14 - 74, by, 74, 18).build());
    }

    private void send(String cmd) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(cmd);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        outline(g, px, py, pw, ph, BORDER);
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "Инвентарь  →  " + target, px + 24, py + 11, TEXT, false);
        g.fill(px, py + 24, px + pw, py + 25, ACCENT);

        GmNetwork.Inventory inv = GmInvData.get();
        List<ItemStack> s = inv != null && inv.name().equals(target) ? inv.slots() : null;
        if (s == null || s.size() < 41) {
            g.drawString(font, "Ждём данные с сервера…", px + 16, gridY, DIM, false);
            super.render(g, mx, my, pt);
            return;
        }

        ItemStack hovered = null;

        // броня 36..39 (сверху вниз: шлем, нагрудник, поножи, ботинки)
        int[] armorOrder = { 39, 38, 37, 36 };
        for (int i = 0; i < 4; i++) {
            hovered = orHover(hovered, drawSlot(g, s.get(armorOrder[i]), armorX, gridY + i * SLOT, mx, my));
        }
        // левая рука 40
        hovered = orHover(hovered, drawSlot(g, s.get(40), armorX, gridY + 4 * SLOT + 6, mx, my));
        g.drawString(font, "рука", armorX - 1, gridY + 4 * SLOT + 6 + SLOT, DIM, false);

        // рюкзак 9..35 (3×9)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                hovered = orHover(hovered, drawSlot(g, s.get(9 + r * 9 + c),
                    gridX + c * SLOT, gridY + r * SLOT, mx, my));
            }
        }
        // пояс 0..8
        int hbY = gridY + 3 * SLOT + 6;
        for (int c = 0; c < 9; c++) {
            hovered = orHover(hovered, drawSlot(g, s.get(c), gridX + c * SLOT, hbY, mx, my));
        }

        if (armedClear != 0 && System.currentTimeMillis() - armedClear < 3000) {
            g.drawString(font, "Нажмите «Очистить» ещё раз для подтверждения",
                px + 14, py + ph - 36, 0xFFC86A5E, false);
        }

        super.render(g, mx, my, pt);
        if (hovered != null && !hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mx, my);
        }
    }

    private static ItemStack orHover(ItemStack cur, ItemStack maybe) {
        return maybe != null ? maybe : cur;
    }

    /** Рисует слот, возвращает предмет, если курсор над ним. */
    private ItemStack drawSlot(GuiGraphics g, ItemStack st, int x, int y, int mx, int my) {
        g.fill(x, y, x + SLOT - 2, y + SLOT - 2, SLOT_BG);
        outline(g, x, y, SLOT - 2, SLOT - 2, BORDER);
        if (!st.isEmpty()) {
            g.renderItem(st, x, y);
            g.renderItemDecorations(font, st, x, y);
        }
        boolean hov = mx >= x && mx < x + SLOT - 2 && my >= y && my < y + SLOT - 2;
        if (hov) g.fill(x, y, x + SLOT - 2, y + SLOT - 2, 0x30FFFFFF);
        return hov && !st.isEmpty() ? st : null;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
