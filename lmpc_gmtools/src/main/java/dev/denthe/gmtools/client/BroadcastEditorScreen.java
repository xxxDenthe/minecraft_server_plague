package dev.denthe.gmtools.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор объявления: крупный текст на весь экран (/title) или
 * сообщение в чат (/tellraw). Выбор цвета и начертания, живой
 * предпросмотр. JSON для команды собираем руками — так надёжнее, чем
 * тащить сериализатор компонентов с реестрами.
 */
public class BroadcastEditorScreen extends Screen {

    public enum Mode { TITLE, CHAT }

    /** Черновик переживает закрытие экрана. */
    public static final class Draft {
        String main = "";
        String sub = "";
        ChatFormatting color = null;
        boolean bold, italic, underline, strike, obf;
    }

    public static final Draft TITLE_DRAFT = new Draft();
    public static final Draft CHAT_DRAFT = new Draft();

    private static final int PANEL = 0xF01A1E1D;
    private static final int SHADOW = 0x90000000;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT = 0xFFE6E8E6;
    private static final int DIM = 0xFF8A9490;
    private static final int TRACK = 0x40000000;
    private static final int ON = 0x807C97A6;

    private final Screen parent;
    private final Mode mode;
    private final Draft d;

    private int px, py, pw, ph;
    private int styleY, colorY, prevY;   // считаются в init, чтобы ничего не наезжало
    private EditBox mainBox;
    private EditBox subBox;

    private final List<ChatFormatting> colors = new ArrayList<>();

    public BroadcastEditorScreen(Screen parent, Mode mode) {
        super(Component.literal("Редактор объявления"));
        this.parent = parent;
        this.mode = mode;
        this.d = mode == Mode.TITLE ? TITLE_DRAFT : CHAT_DRAFT;
        for (ChatFormatting c : ChatFormatting.values()) {
            if (c.isColor()) colors.add(c);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 40, 430);
        ph = Math.min(height - 40, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int pad = 14, w = pw - pad * 2;

        mainBox = new EditBox(font, px + pad, py + 34, w, 16,
            Component.literal(mode == Mode.TITLE ? "заголовок" : "текст"));
        mainBox.setHint(Component.literal(mode == Mode.TITLE ? "заголовок" : "текст сообщения"));
        mainBox.setMaxLength(200);
        mainBox.setValue(d.main);
        mainBox.setResponder(s -> d.main = s);
        addRenderableWidget(mainBox);
        setInitialFocus(mainBox);

        int cursor = py + 34 + 16 + 8;
        if (mode == Mode.TITLE) {
            subBox = new EditBox(font, px + pad, cursor, w, 16, Component.literal("подзаголовок"));
            subBox.setHint(Component.literal("подзаголовок (необязательно)"));
            subBox.setMaxLength(200);
            subBox.setValue(d.sub);
            subBox.setResponder(s -> d.sub = s);
            addRenderableWidget(subBox);
            cursor += 16 + 8;
        }

        styleY = cursor + 12;               // под полями, с местом под подпись «Начертание:»
        colorY = styleY + 14 + 14;          // под тумблерами
        prevY  = colorY + 32 + 14;          // под двумя рядами образцов цвета

        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> send())
            .bounds(px + pw - pad - 96, by, 96, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
            .bounds(px + pad, by, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Сброс стиля"), b -> {
            d.color = null;
            d.bold = d.italic = d.underline = d.strike = d.obf = false;
        }).bounds(px + pad + 84, by, 92, 18).build());
    }

    private void send() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        var conn = minecraft.getConnection();
        if (mode == Mode.CHAT) {
            if (!d.main.isEmpty()) conn.sendCommand("tellraw @a " + json(d.main));
        } else {
            conn.sendCommand("title @a times 10 70 20");
            conn.sendCommand("title @a subtitle " + json(d.sub));
            conn.sendCommand("title @a title " + json(d.main.isEmpty() ? " " : d.main));
        }
        minecraft.setScreen(parent);
    }

    /** {"text":"...","color":"...","bold":true,...} — вручную, с экранированием. */
    private String json(String raw) {
        StringBuilder sb = new StringBuilder("{\"text\":\"");
        sb.append(raw.replace("\\", "\\\\").replace("\"", "\\\""));
        sb.append('"');
        if (d.color != null) sb.append(",\"color\":\"").append(d.color.getName()).append('"');
        if (d.bold) sb.append(",\"bold\":true");
        if (d.italic) sb.append(",\"italic\":true");
        if (d.underline) sb.append(",\"underlined\":true");
        if (d.strike) sb.append(",\"strikethrough\":true");
        if (d.obf) sb.append(",\"obfuscated\":true");
        return sb.append('}').toString();
    }

    private Style previewStyle() {
        Style st = Style.EMPTY;
        if (d.color != null) st = st.withColor(d.color);
        return st.withBold(d.bold).withItalic(d.italic).withUnderlined(d.underline)
            .withStrikethrough(d.strike).withObfuscated(d.obf);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        outline(g, px, py, pw, ph, BORDER);
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, mode == Mode.TITLE ? "Крупный текст на весь экран" : "Сообщение в чат",
            px + 24, py + 11, TEXT, false);
        g.fill(px, py + 24, px + pw, py + 25, ACCENT);

        // начертание
        int sx = px + 14;
        g.drawString(font, "Начертание", sx, styleY - 11, DIM, false);
        sx = toggle(g, sx, styleY, "Ж", d.bold, mx, my);
        sx = toggle(g, sx, styleY, "К", d.italic, mx, my);
        sx = toggle(g, sx, styleY, "П", d.underline, mx, my);
        sx = toggle(g, sx, styleY, "З", d.strike, mx, my);
        toggle(g, sx, styleY, "О", d.obf, mx, my);

        // цвет
        int cx = px + 14;
        g.drawString(font, "Цвет", cx, colorY - 11, DIM, false);
        for (int i = 0; i < colors.size(); i++) {
            int qx = cx + (i % 8) * 16;
            int qy = colorY + (i / 8) * 16;
            Integer col = colors.get(i).getColor();
            g.fill(qx, qy, qx + 14, qy + 14, 0xFF000000 | (col == null ? 0xFFFFFF : col));
            if (colors.get(i) == d.color) outline(g, qx - 1, qy - 1, 16, 16, 0xFFFFFFFF);
        }

        // предпросмотр
        g.drawString(font, "Предпросмотр", px + 14, prevY - 11, DIM, false);
        int boxH = mode == Mode.TITLE ? 34 : 20;
        g.fill(px + 14, prevY, px + pw - 14, prevY + boxH, TRACK);
        MutableComponent prev = Component.literal(d.main.isEmpty() ? "(пусто)" : d.main)
            .withStyle(previewStyle());
        g.drawCenteredString(font, prev, px + pw / 2, prevY + 5, 0xFFFFFFFF);
        if (mode == Mode.TITLE && !d.sub.isEmpty()) {
            g.drawCenteredString(font, Component.literal(d.sub).withStyle(previewStyle().withBold(false)),
                px + pw / 2, prevY + 20, 0xFFCCCCCC);
        }

        super.render(g, mx, my, pt);
    }

    private int toggle(GuiGraphics g, int x, int y, String label, boolean on, int mx, int my) {
        boolean hov = mx >= x && mx < x + 16 && my >= y && my < y + 14;
        g.fill(x, y, x + 16, y + 14, on ? ON : (hov ? 0x30FFFFFF : TRACK));
        outline(g, x, y, 16, 14, BORDER);
        g.drawString(font, label, x + 5, y + 3, on ? 0xFFFFFFFF : DIM, false);
        return x + 20;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int sx = px + 14;
        String[] keys = { "b", "i", "u", "s", "o" };
        for (int k = 0; k < 5; k++) {
            int bx = sx + k * 20;
            if (mx >= bx && mx < bx + 16 && my >= styleY && my < styleY + 14) {
                switch (keys[k]) {
                    case "b" -> d.bold = !d.bold;
                    case "i" -> d.italic = !d.italic;
                    case "u" -> d.underline = !d.underline;
                    case "s" -> d.strike = !d.strike;
                    case "o" -> d.obf = !d.obf;
                }
                return true;
            }
        }
        int cx = px + 14;
        for (int i = 0; i < colors.size(); i++) {
            int qx = cx + (i % 8) * 16, qy = colorY + (i / 8) * 16;
            if (mx >= qx && mx < qx + 14 && my >= qy && my < qy + 14) {
                d.color = (colors.get(i) == d.color) ? null : colors.get(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
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
