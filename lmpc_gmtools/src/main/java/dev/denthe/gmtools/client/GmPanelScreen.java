package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Панель мастера игры.
 *
 * Пока не введён пароль — маленькое окно с полем ввода. После верного
 * пароля разблокируется на всю сессию клиента и показывает панель на
 * пол-экрана: слева колонка разделов, справа содержимое.
 *
 * Кнопки ничего не делают сами — шлют серверу ванильную команду, право
 * на неё сервер проверяет по OP. Подменённый клиент лишнего не получит,
 * пароль здесь — только замок от случайного открытия.
 */
public class GmPanelScreen extends Screen {

    static final String PASSWORD = "p1dor";

    /** Разблокировано на эту сессию клиента (сбрасывается при перезапуске игры). */
    public static boolean unlocked = false;

    private enum Tab {
        ACTIONS("Действия"), PLAYERS("Игроки"), PLAGUE("Чума");
        final String label;
        Tab(String l) { this.label = l; }
    }

    // ── палитра: холодный камень, один стально-синий акцент (бриф лаунчера) ──
    private static final int SHADOW   = 0x90000000;
    private static final int PANEL    = 0xF01A1E1D;
    private static final int NAV      = 0xFF141817;
    private static final int BORDER   = 0xFF2E3639;
    private static final int ACCENT   = 0xFF7C97A6;
    private static final int TEXT     = 0xFFE6E8E6;
    private static final int DIM      = 0xFF8A9490;
    private static final int ERR      = 0xFFC86A5E;
    private static final int SELECT   = 0x407C97A6;
    private static final int HOVER    = 0x22FFFFFF;

    private static final int HEADER = 30;
    private static final int FOOTER = 22;
    private static final int NAV_W  = 96;
    private static final int PAD    = 14;
    private static final int ROW_H  = 18;

    private static Tab tab = Tab.ACTIONS;

    private int px, py, pw, ph;
    private int contentX, contentY, contentW, contentH;

    private EditBox passwordBox;
    private String error;

    private String selectedPlayer;
    private int listScroll;

    public GmPanelScreen() {
        super(Component.literal("Панель мастера"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ────────────────────────────────────────────────────────────── init ──

    @Override
    protected void init() {
        if (!unlocked) {
            initLogin();
            return;
        }
        layout();
        initNavAndFooter();
        switch (tab) {
            case ACTIONS -> initActions();
            case PLAYERS -> initPlayers();
            case PLAGUE  -> initPlague();
        }
    }

    private void layout() {
        pw = Math.min(width - 60, 448);
        ph = Math.min(height - 60, 288);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        contentX = px + NAV_W + 6;
        contentY = py + HEADER + 18;
        contentW = px + pw - PAD - contentX;
        contentH = ph - HEADER - FOOTER - 28;
    }

    private void initLogin() {
        int w = 240, cx = width / 2;
        passwordBox = new EditBox(font, cx - w / 2, height / 2 - 6, w, 20, Component.literal("Пароль"));
        passwordBox.setHint(Component.literal("Пароль"));
        passwordBox.setMaxLength(32);
        passwordBox.setFormatter((s, i) ->
            FormattedCharSequence.forward("•".repeat(s.length()), Style.EMPTY));
        addRenderableWidget(passwordBox);
        setInitialFocus(passwordBox);
        addRenderableWidget(Button.builder(Component.literal("Войти"), b -> tryUnlock())
            .bounds(cx - w / 2, height / 2 + 22, w, 20).build());
    }

    private void initNavAndFooter() {
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(px + pw - PAD - 70, py + ph - FOOTER + 1, 70, 18).build());
    }

    private void initActions() {
        boolean spectating = minecraft != null && minecraft.player != null && minecraft.player.isSpectator();
        addRenderableWidget(Button.builder(
            Component.literal(spectating ? "Вернуться из наблюдателей" : "Уйти в наблюдатели"),
            b -> { SpectatorToggle.toggle(minecraft); onClose(); })
            .bounds(contentX, contentY + 22, contentW, 20).build());
    }

    private void initPlayers() {
        List<PlayerInfo> players = onlinePlayers();
        if (selectedPlayer != null && players.stream().noneMatch(p -> nameOf(p).equals(selectedPlayer))) {
            selectedPlayer = null;
        }
        if (selectedPlayer == null) return;

        int dx = contentX + Math.round(contentW * 0.42f) + 12;
        int dw = contentX + contentW - dx;
        int y = contentY + 20;
        String n = selectedPlayer;
        addRenderableWidget(Button.builder(Component.literal("Телепорт к игроку"),
            b -> run("tp @s " + n)).bounds(dx, y, dw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Призвать к себе"),
            b -> run("tp " + n + " @s")).bounds(dx, y + 24, dw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Наблюдать за ним"),
            b -> { SpectatorToggle.enterSpectator(minecraft); run("spectate " + n); }).bounds(dx, y + 48, dw, 20).build());
    }

    private void initPlague() {
        addRenderableWidget(Button.builder(Component.literal("Состояние чумы  (/plague info)"),
            b -> { run("plague info"); onClose(); }).bounds(contentX, contentY + 22, contentW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Карта чумы  (/plague gui)"),
            b -> run("plague gui")).bounds(contentX, contentY + 46, contentW, 20).build());
    }

    // ──────────────────────────────────────────────────────────── render ──

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        if (!unlocked) {
            renderLogin(g, mx, my, pt);
            return;
        }

        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.fill(px, py, px + NAV_W, py + ph, NAV);
        outline(g, px, py, pw, ph, BORDER);

        // шапка
        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "LMPC · Мастер игры", px + 24, py + 11, TEXT, false);
        g.fill(px, py + HEADER, px + pw, py + HEADER + 1, ACCENT);

        renderNav(g, mx, my);
        renderFooterHint(g);

        switch (tab) {
            case ACTIONS -> renderActions(g);
            case PLAYERS -> renderPlayers(g, mx, my);
            case PLAGUE  -> renderPlague(g);
        }

        super.render(g, mx, my, pt); // кнопки-виджеты поверх
    }

    private void renderLogin(GuiGraphics g, int mx, int my, float pt) {
        int w = 260, h = 118, x = width / 2 - w / 2, y = height / 2 - h / 2 - 6;
        g.fill(x + 4, y + 4, x + w + 4, y + h + 4, SHADOW);
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, BORDER);
        g.fill(x + 14, y + 14, x + 20, y + 20, ACCENT);
        g.drawString(font, "LMPC · Мастер игры", x + 26, y + 13, TEXT, false);
        g.fill(x, y + 30, x + w, y + 31, ACCENT);
        g.drawCenteredString(font, "Введите пароль для доступа", width / 2, y + 42, DIM);
        if (error != null) {
            g.drawCenteredString(font, error, width / 2, height / 2 + 46, ERR);
        }
        super.render(g, mx, my, pt);
    }

    private void renderNav(GuiGraphics g, int mx, int my) {
        int bx = px + 8, bw = NAV_W - 16, bh = 22;
        int by = py + HEADER + 12;
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            if (active) {
                g.fill(bx - 4, by, bx - 2, by + bh, ACCENT);
                g.fill(bx, by, bx + bw, by + bh, SELECT);
            } else if (hover) {
                g.fill(bx, by, bx + bw, by + bh, HOVER);
            }
            g.drawString(font, t.label, bx + 6, by + 7, active ? TEXT : DIM, false);
            by += bh + 4;
        }
    }

    private void renderFooterHint(GuiGraphics g) {
        g.fill(px, py + ph - FOOTER, px + pw, py + ph - FOOTER + 1, BORDER);
        g.drawString(font, "Esc — закрыть   ·   Home — наблюдатель",
            px + 12, py + ph - FOOTER + 6, DIM, false);
    }

    private void renderActions(GuiGraphics g) {
        g.drawString(font, "Режим игры", contentX, contentY, DIM, false);
        g.drawString(font, "Сейчас: " + currentModeRu(), contentX, contentY + 46, TEXT, false);
        g.drawString(font, "Позиция запоминается — возврат ставит вас на прежнее место.",
            contentX, contentY + 62, DIM, false);
    }

    private void renderPlayers(GuiGraphics g, int mx, int my) {
        List<PlayerInfo> players = onlinePlayers();

        int listX = contentX;
        int listW = Math.round(contentW * 0.42f);
        int listY = contentY;
        int listH = contentH;

        g.drawString(font, "Игроки онлайн: " + players.size(), listX, listY - 12, DIM, false);
        g.fill(listX, listY, listX + listW, listY + listH, 0x30000000);
        outline(g, listX, listY, listW, listH, BORDER);

        if (players.isEmpty()) {
            g.drawString(font, "никого", listX + 8, listY + 8, DIM, false);
            return;
        }

        int maxScroll = Math.max(0, players.size() * ROW_H - listH);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        int y = listY - listScroll;
        for (PlayerInfo p : players) {
            String name = nameOf(p);
            boolean sel = name.equals(selectedPlayer);
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H
                && my >= listY && my < listY + listH;
            if (sel) g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, SELECT);
            else if (hover) g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, HOVER);
            g.fill(listX + 6, y + ROW_H / 2 - 2, listX + 10, y + ROW_H / 2 + 2, pingColor(p.getLatency()));
            String shown = font.plainSubstrByWidth(name, listW - 20);
            g.drawString(font, shown, listX + 16, y + 5, sel ? TEXT : DIM, false);
            y += ROW_H;
        }
        g.disableScissor();

        // деталь выбранного игрока
        int dx = listX + listW + 12;
        if (selectedPlayer == null) {
            g.drawString(font, "Выберите игрока слева", dx, listY + 4, DIM, false);
            return;
        }
        g.fill(dx, listY + 2, dx + 4, listY + 12, ACCENT);
        g.drawString(font, selectedPlayer, dx + 10, listY + 3, TEXT, false);
        PlayerInfo pi = players.stream().filter(p -> nameOf(p).equals(selectedPlayer)).findFirst().orElse(null);
        if (pi != null) {
            g.drawString(font, gameModeRu(pi.getGameMode()) + "  ·  " + pi.getLatency() + " мс",
                dx + 10, listY + 15, DIM, false);
        }
    }

    private void renderPlague(GuiGraphics g) {
        g.drawString(font, "Мод plague core", contentX, contentY, DIM, false);
        g.drawString(font, "Вывод команд уходит в чат.", contentX, contentY + 72, DIM, false);
    }

    // ───────────────────────────────────────────────────────────── mouse ──

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (unlocked) {
            int bx = px + 8, bw = NAV_W - 16, bh = 22, by = py + HEADER + 12;
            for (Tab t : Tab.values()) {
                if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
                    if (t != tab) { tab = t; listScroll = 0; rebuildWidgets(); }
                    return true;
                }
                by += bh + 4;
            }
            if (tab == Tab.PLAYERS) {
                int listX = contentX;
                int listW = Math.round(contentW * 0.42f);
                int listY = contentY, listH = contentH;
                if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                    int idx = (int) ((my - listY + listScroll) / ROW_H);
                    List<PlayerInfo> players = onlinePlayers();
                    if (idx >= 0 && idx < players.size()) {
                        selectedPlayer = nameOf(players.get(idx));
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dxs, double dys) {
        if (unlocked && tab == Tab.PLAYERS) {
            listScroll -= (int) (dys * ROW_H);
            return true;
        }
        return super.mouseScrolled(mx, my, dxs, dys);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!unlocked && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && passwordBox != null && passwordBox.isFocused()) {
            tryUnlock();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ──────────────────────────────────────────────────────────── helpers ──

    private void tryUnlock() {
        if (PASSWORD.equals(passwordBox.getValue())) {
            unlocked = true;
            error = null;
            rebuildWidgets();
        } else {
            error = "Неверный пароль";
            passwordBox.setValue("");
        }
    }

    private void run(String cmd) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(cmd);
        }
    }

    private List<PlayerInfo> onlinePlayers() {
        if (minecraft == null || minecraft.getConnection() == null) return List.of();
        UUID self = minecraft.player != null ? minecraft.player.getUUID() : null;
        return minecraft.getConnection().getOnlinePlayers().stream()
            .filter(p -> p.getProfile() != null && p.getProfile().getName() != null)
            .filter(p -> self == null || !p.getProfile().getId().equals(self))
            .sorted(Comparator.comparing(p -> p.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static String nameOf(PlayerInfo p) {
        return p.getProfile().getName();
    }

    private static int pingColor(int ms) {
        if (ms < 0) return DIM;
        if (ms < 150) return 0xFF6FB06F;
        if (ms < 300) return 0xFFC8B45E;
        return 0xFFC86A5E;
    }

    private String currentModeRu() {
        if (minecraft == null || minecraft.gameMode == null) return "?";
        return gameModeRu(minecraft.gameMode.getPlayerMode());
    }

    private static String gameModeRu(GameType t) {
        if (t == null) return "?";
        return switch (t) {
            case SURVIVAL -> "Выживание";
            case CREATIVE -> "Творческий";
            case ADVENTURE -> "Приключение";
            case SPECTATOR -> "Наблюдатель";
        };
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
