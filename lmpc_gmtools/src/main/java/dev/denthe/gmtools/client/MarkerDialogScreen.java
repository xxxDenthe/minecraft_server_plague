package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Окошко новой метки: выбор иконки и имени. Открывается по ПКМ на
 * карте, координаты берутся из точки клика. Ставит метку командой
 * `/gmtools mark x z icon имя`.
 */
public class MarkerDialogScreen extends Screen {

    private static final int PANEL = 0xF01A1E1D;
    private static final int SHADOW = 0x90000000;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT = 0xFFE6E8E6;
    private static final int DIM = 0xFF8A9490;
    private static final int SELECT = 0x807C97A6;
    private static final int CELL = 20;
    private static final int COLS = 8;

    private final Screen parent;
    private final double worldX, worldZ;

    private int px, py, pw, ph, gridX, gridY;
    private EditBox nameBox;
    private int icon = 0;

    public MarkerDialogScreen(Screen parent, double worldX, double worldZ) {
        super(Component.literal("Новая метка"));
        this.parent = parent;
        this.worldX = worldX;
        this.worldZ = worldZ;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int rows = (MarkerIcons.count() + COLS - 1) / COLS;
        pw = 20 + COLS * CELL;
        ph = 96 + rows * CELL;
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        nameBox = new EditBox(font, px + 12, py + 34, pw - 24, 16, Component.literal("название"));
        nameBox.setHint(Component.literal("название метки"));
        nameBox.setMaxLength(40);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        gridX = px + 12;
        gridY = py + 60;

        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Поставить"), b -> confirm())
            .bounds(px + pw - 12 - 90, by, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
            .bounds(px + 12, by, 70, 18).build());
    }

    private void confirm() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) name = "метка";
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(String.format(Locale.ROOT,
                "gmtools mark %.1f %.1f %d %s", worldX, worldZ, icon, name));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        outline(g, px, py, pw, ph, BORDER);
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "Новая метка", px + 24, py + 11, TEXT, false);
        g.fill(px, py + 24, px + pw, py + 25, ACCENT);
        g.drawString(font, String.format(Locale.ROOT, "X %.0f   Z %.0f", worldX, worldZ),
            px + 12, py + 54 - 10, DIM, false);

        for (int i = 0; i < MarkerIcons.count(); i++) {
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            boolean hov = mx >= cx && mx < cx + CELL - 2 && my >= cy && my < cy + CELL - 2;
            if (i == icon) g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, SELECT);
            else if (hov) g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0x30FFFFFF);
            outline(g, cx, cy, CELL - 2, CELL - 2, BORDER);
            g.renderItem(MarkerIcons.stack(i), cx + 1, cy + 1);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < MarkerIcons.count(); i++) {
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            if (mx >= cx && mx < cx + CELL - 2 && my >= cy && my < cy + CELL - 2) {
                icon = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && nameBox != null && nameBox.isFocused()) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
