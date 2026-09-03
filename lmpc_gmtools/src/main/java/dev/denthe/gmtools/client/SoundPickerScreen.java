package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Проиграть звук всем или конкретному игроку. Список звуков — реестр
 * SoundEvent, весь на клиенте. Команда:
 *   execute at &lt;T&gt; run playsound &lt;id&gt; master &lt;T&gt; ~ ~ ~ &lt;громкость&gt; &lt;тон&gt;
 * «execute at» нужен, чтобы звук шёл из точки самого игрока, а не GM.
 */
public class SoundPickerScreen extends Screen {

    private static final int ROW = 12;
    private static final int PANEL = 0xF01A1E1D;
    private static final int SHADOW = 0x90000000;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT = 0xFFE6E8E6;
    private static final int DIM = 0xFF8A9490;
    private static final int SELECT = 0x807C97A6;
    private static final int TRACK = 0x40000000;

    private static List<String> catalog;

    private final Screen parent;

    private int px, py, pw, ph;
    private int listX, listY, listW, listH;

    private EditBox searchBox;
    private String query = "";
    private List<String> shown = List.of();
    private int scroll;
    private String selected;

    private float volume = 1f;
    private float pitch = 1f;
    private String target;   // null = все

    public SoundPickerScreen(Screen parent) {
        super(Component.literal("Звук"));
        this.parent = parent;
    }

    private static List<String> catalog() {
        if (catalog == null) {
            List<String> l = new ArrayList<>();
            for (var key : BuiltInRegistries.SOUND_EVENT.keySet()) l.add(key.toString());
            l.sort(String::compareTo);
            catalog = l;
        }
        return catalog;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 60, 440);
        ph = Math.min(height - 60, 292);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int pad = 12;
        searchBox = new EditBox(font, px + pad, py + 28, pw - pad * 2, 16, Component.literal("поиск"));
        searchBox.setHint(Component.literal("поиск звука"));
        searchBox.setValue(query);
        searchBox.setResponder(s -> { query = s; refilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        listX = px + pad;
        listY = py + 50;
        listW = pw - pad * 2;
        listH = ph - 50 - 74;

        int cy = listY + listH + 6;
        addRenderableWidget(new Slider(px + pad, cy, 150, 14, true));
        addRenderableWidget(new Slider(px + pad + 158, cy, 150, 14, false));

        addRenderableWidget(Button.builder(Component.literal(targetLabel()), b -> {
            cycleTarget();
            rebuildWidgets();
        }).bounds(px + pw - pad - 130, cy, 130, 14).build());

        int by = py + ph - 22;
        addRenderableWidget(Button.builder(Component.literal("Играть"), b -> play())
            .bounds(px + pw - pad - 100, by, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Стоп всем"), b -> stop())
            .bounds(px + pw - pad - 208, by, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
            .bounds(px + pad, by, 80, 16).build());

        refilter();
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        for (String s : catalog()) if (q.isEmpty() || s.contains(q)) out.add(s);
        shown = out;
        scroll = 0;
    }

    private String targetLabel() {
        return "Цель: " + (target == null ? "Все" : target);
    }

    private void cycleTarget() {
        List<String> names = new ArrayList<>();
        names.add(null);
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().getOnlinePlayers().forEach(p -> {
                if (p.getProfile() != null && p.getProfile().getName() != null) {
                    names.add(p.getProfile().getName());
                }
            });
        }
        int i = names.indexOf(target);
        target = names.get((i + 1) % names.size());
    }

    private void play() {
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        String t = target == null ? "@a" : target;
        minecraft.getConnection().sendCommand(String.format(Locale.ROOT,
            "execute at %s run playsound %s master %s ~ ~ ~ %.2f %.2f", t, selected, t, volume, pitch));
    }

    private void stop() {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand("stopsound @a");
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        outline(g, px, py, pw, ph, BORDER);
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "Проиграть звук", px + 24, py + 11, TEXT, false);
        g.fill(px, py + 24, px + pw, py + 25, ACCENT);

        g.fill(listX, listY, listX + listW, listY + listH, TRACK);
        outline(g, listX, listY, listW, listH, BORDER);

        int maxScroll = Math.max(0, shown.size() * ROW - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        int y = listY - scroll;
        for (String s : shown) {
            if (y > listY - ROW && y < listY + listH) {
                boolean sel = s.equals(selected);
                boolean hov = mx >= listX && mx < listX + listW && my >= y && my < y + ROW
                    && my >= listY && my < listY + listH;
                if (sel) g.fill(listX + 1, y, listX + listW - 1, y + ROW, SELECT);
                else if (hov) g.fill(listX + 1, y, listX + listW - 1, y + ROW, 0x22FFFFFF);
                g.drawString(font, font.plainSubstrByWidth(s, listW - 8), listX + 4, y + 2,
                    sel ? TEXT : DIM, false);
            }
            y += ROW;
        }
        g.disableScissor();

        g.drawString(font, "Найдено: " + shown.size()
            + (selected != null ? "   ·   выбран: " + selected : ""),
            listX, listY + listH + ROW * 4 - 2, DIM, false);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + scroll) / ROW);
            if (idx >= 0 && idx < shown.size()) {
                selected = shown.get(idx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dxs, double dys) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll -= (int) (dys * ROW);
            return true;
        }
        return super.mouseScrolled(mx, my, dxs, dys);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private class Slider extends AbstractSliderButton {
        private final boolean isVolume;

        Slider(int x, int y, int w, int h, boolean isVolume) {
            super(x, y, w, h, Component.empty(),
                isVolume ? volume : (pitch - 0.5) / 1.5);
            this.isVolume = isVolume;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(isVolume
                ? String.format(Locale.ROOT, "Громкость: %.2f", volume)
                : String.format(Locale.ROOT, "Тон: %.2f", pitch)));
        }

        @Override
        protected void applyValue() {
            if (isVolume) volume = (float) value;
            else pitch = (float) (0.5 + value * 1.5);
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
