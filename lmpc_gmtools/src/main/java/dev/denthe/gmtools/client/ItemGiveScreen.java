package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Выдача предмета игроку. Свой список предметов вместо JEI: реестр
 * предметов есть на клиенте целиком, поэтому сетка иконок с поиском
 * строится без всякого серверного кода, а «Выдать» шлёт /give.
 */
public class ItemGiveScreen extends Screen {

    private static final int CELL = 18;
    private static final int PANEL = 0xF01A1E1D;
    private static final int SHADOW = 0x90000000;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT = 0xFFE6E8E6;
    private static final int DIM = 0xFF8A9490;
    private static final int SELECT = 0x807C97A6;
    private static final int TRACK = 0x40000000;

    private record Entry(Item item, String id, String search) {}

    /** Каталог строится один раз: ~1200 предметов, имена уже в нижнем регистре. */
    private static List<Entry> catalog;

    private final Screen parent;
    private final String target;

    private int px, py, pw, ph;
    private int gridX, gridY, gridW, gridH, cols;

    private EditBox searchBox;
    private String query = "";
    private List<Entry> shown = List.of();
    private int scroll;
    private Entry selected;
    private int count = 1;

    public ItemGiveScreen(Screen parent, String target) {
        super(Component.literal("Выдать предмет"));
        this.parent = parent;
        this.target = target;
    }

    private static List<Entry> catalog() {
        if (catalog == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                var key = BuiltInRegistries.ITEM.getKey(item);
                String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                list.add(new Entry(item, key.toString(), key.getPath() + " " + name));
            }
            catalog = list;
        }
        return catalog;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 60, 460);
        ph = Math.min(height - 60, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int pad = 12;
        searchBox = new EditBox(font, px + pad, py + 28, pw - pad * 2, 16, Component.literal("поиск"));
        searchBox.setHint(Component.literal("поиск: имя или id"));
        searchBox.setValue(query);
        searchBox.setResponder(s -> { query = s; refilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        gridX = px + pad;
        gridY = py + 50;
        gridW = pw - pad * 2;
        gridH = ph - 50 - 46;
        cols = Math.max(1, gridW / CELL);

        addRenderableWidget(new CountSlider(px + pad, py + ph - 40, 180, 16));

        addRenderableWidget(Button.builder(Component.literal("Выдать"), b -> give())
            .bounds(px + pw - pad - 100, py + ph - 40, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
            .bounds(px + pw - pad - 100, py + ph - 20, 100, 16).build());

        refilter();
    }

    private void refilter() {
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (Entry e : catalog()) {
            boolean ok = true;
            for (String t : tokens) {
                if (!t.isEmpty() && !e.search.contains(t)) { ok = false; break; }
            }
            if (ok) out.add(e);
        }
        shown = out;
        scroll = 0;
    }

    private void give() {
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("give " + target + " " + selected.id + " " + count);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        outline(g, px, py, pw, ph, BORDER);
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "Выдать предмет  →  " + target, px + 24, py + 11, TEXT, false);
        g.fill(px, py + 24, px + pw, py + 25, ACCENT);

        g.fill(gridX, gridY, gridX + gridW, gridY + gridH, TRACK);
        outline(g, gridX, gridY, gridW, gridH, BORDER);

        int rows = (shown.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, rows * CELL - gridH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(gridX + 1, gridY + 1, gridX + gridW - 1, gridY + gridH - 1);
        Entry hovered = null;
        for (int i = 0; i < shown.size(); i++) {
            int cx = gridX + (i % cols) * CELL;
            int cy = gridY + (i / cols) * CELL - scroll;
            if (cy < gridY - CELL || cy > gridY + gridH) continue;
            Entry e = shown.get(i);
            boolean hov = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL
                && my >= gridY && my < gridY + gridH;
            if (e == selected) g.fill(cx, cy, cx + CELL, cy + CELL, SELECT);
            else if (hov) g.fill(cx, cy, cx + CELL, cy + CELL, 0x30FFFFFF);
            g.renderItem(new ItemStack(e.item), cx + 1, cy + 1);
            if (hov) hovered = e;
        }
        g.disableScissor();

        g.drawString(font, "Найдено: " + shown.size(), gridX, gridY + gridH + 4, DIM, false);
        if (selected != null) {
            g.drawString(font, font.plainSubstrByWidth(
                new ItemStack(selected.item).getHoverName().getString() + "  ·  " + selected.id,
                pw - 24), gridX, py + ph - 58, TEXT, false);
        } else {
            g.drawString(font, "Выберите предмет в сетке", gridX, py + ph - 58, DIM, false);
        }

        super.render(g, mx, my, pt);

        if (hovered != null) {
            g.renderTooltip(font, new ItemStack(hovered.item), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            int col = (int) ((mx - gridX) / CELL);
            int row = (int) ((my - gridY + scroll) / CELL);
            int idx = row * cols + col;
            if (col < cols && idx >= 0 && idx < shown.size()) {
                selected = shown.get(idx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dxs, double dys) {
        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            scroll -= (int) (dys * CELL);
            return true;
        }
        return super.mouseScrolled(mx, my, dxs, dys);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private class CountSlider extends AbstractSliderButton {
        CountSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (count - 1) / 63.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Количество: " + count));
        }

        @Override
        protected void applyValue() {
            count = 1 + (int) Math.round(value * 63);
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
