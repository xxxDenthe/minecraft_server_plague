package dev.denthe.gmtools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Панель мастера игры. Пока не введён пароль — только поле ввода.
 * После верного пароля разблокируется на всю сессию клиента.
 *
 * Кнопки ничего не делают сами: они шлют серверу команду, а право на
 * команду сервер проверяет по OP. Клиент с подменённым модом всё равно
 * не получит больше, чем ему положено.
 */
public class GmPanelScreen extends Screen {

    static final String PASSWORD = "p1dor";

    /** Разблокировано на эту сессию клиента (сбрасывается при перезапуске игры). */
    public static boolean unlocked = false;

    private static final int ACCENT = 0xFF728B99;   // стально-синий акцент из брифа лаунчера
    private static final int PANEL_BG = 0xE0101410;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int DIM = 0xFF909090;
    private static final int ERR = 0xFFC05050;

    private static final int W = 220;

    private EditBox passwordBox;
    private String error;

    public GmPanelScreen() {
        super(Component.literal("Панель мастера"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        if (!unlocked) {
            passwordBox = new EditBox(font, cx - W / 2, height / 2 - 10, W, 20,
                Component.literal("Пароль"));
            passwordBox.setHint(Component.literal("Пароль"));
            passwordBox.setMaxLength(32);
            addRenderableWidget(passwordBox);
            setInitialFocus(passwordBox);
            addRenderableWidget(Button.builder(Component.literal("Войти"), b -> tryUnlock())
                .bounds(cx - W / 2, height / 2 + 16, W, 20).build());
            return;
        }

        int y = height / 2 - 74;
        addRenderableWidget(Button.builder(Component.literal("Режим наблюдателя"), b -> {
            SpectatorToggle.toggle(minecraft);
            onClose();
        }).bounds(cx - W / 2, y, W, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Состояние чумы  ·  /plague info"), b -> {
            sendCommand("plague info");
            onClose();
        }).bounds(cx - W / 2, y + 24, W, 20).build());

        y += 58;
        for (String name : onlinePlayerNames()) {
            addRenderableWidget(Button.builder(Component.literal("Телепорт → " + name), b -> {
                sendCommand("tp " + name);
                onClose();
            }).bounds(cx - W / 2, y, W, 20).build());
            y += 22;
        }

        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(cx - W / 2, y + 6, W, 20).build());
    }

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

    private void sendCommand(String cmd) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(cmd);
        }
    }

    private List<String> onlinePlayerNames() {
        if (minecraft == null || minecraft.getConnection() == null) return List.of();
        return minecraft.getConnection().getOnlinePlayers().stream()
            .map(info -> info.getProfile().getName())
            .filter(n -> n != null && !n.isBlank())
            .sorted()
            .toList();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        int cx = width / 2;
        int top = unlocked ? height / 2 - 88 : height / 2 - 40;
        int bot = unlocked ? height / 2 + 120 : height / 2 + 48;
        int left = cx - W / 2 - 12;
        int right = cx + W / 2 + 12;

        g.fill(left, top, right, bot, PANEL_BG);
        g.fill(left, top, right, top + 1, ACCENT);
        g.fill(left, bot - 1, right, bot, ACCENT);

        g.drawCenteredString(font, "LMPC · Панель мастера", cx, top + 8, TEXT);
        if (!unlocked) {
            g.drawCenteredString(font, "Введите пароль для доступа", cx, top + 22, DIM);
            if (error != null) {
                g.drawCenteredString(font, error, cx, height / 2 + 40, ERR);
            }
        }

        super.render(g, mx, my, pt);
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
}
