package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
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
 * Слева — колонка разделов, справа — их содержимое. У некоторых разделов
 * сверху ещё ряд «папок». Всё компактно: кнопки 14 px, длинный текст
 * переносится, опасные действия просят второй клик.
 *
 * Кнопки ничего не делают сами — шлют серверу ванильную команду, право
 * на неё сервер проверяет по OP. Пароль здесь — только замок от
 * случайного открытия.
 */
public class GmPanelScreen extends Screen {

    static final String PASSWORD = "p1dor";
    public static boolean unlocked = false;

    // возврат из /plague gui (см. GmClientEvents)
    public static boolean returnAfterPlagueGui = false;
    static boolean sawPlagueGui = false;
    static int plagueGuiWait = 0;

    private enum Section {
        SELF("Себе"), PLAYERS("Игроки"), MAP("Карта"),
        WORLD("Мир", "Погода", "Правила"),
        BROADCAST("Вещание", "Заголовок", "Чат", "Звук"),
        PLAGUE("Чума"), EXPERIMENTAL("Опыты");
        final String label;
        final String[] folders;
        Section(String l, String... f) { this.label = l; this.folders = f; }
    }

    private static final int[] folderIdx = new int[Section.values().length];

    // вид карты игроков (переживает переоткрытие панели)
    private static double mapCX, mapCZ;
    private static double mapScale = 0.35;
    private static boolean mapCentered;
    private boolean mapDragging;

    /**
     * Правило и его id для команды /gamerule. Клиенту сервер синхронизирует
     * только часть правил, поэтому текущее значение не показываем — даём
     * две кнопки, вкл и выкл.
     */
    private record Rule(String id, String label) {}

    private static final Rule[] RULES = {
        new Rule("keepInventory",        "Хранить инвентарь"),
        new Rule("mobGriefing",          "Мобы ломают блоки"),
        new Rule("doFireTick",           "Огонь распространяется"),
        new Rule("doInsomnia",           "Фантомы"),
        new Rule("fallDamage",           "Урон от падения"),
        new Rule("doMobSpawning",        "Спавн мобов"),
        new Rule("doMobLoot",            "Дроп с мобов"),
        new Rule("doWeatherCycle",       "Смена погоды"),
        new Rule("naturalRegeneration",  "Регенерация от сытости"),
        new Rule("showDeathMessages",    "Сообщения о смерти"),
    };

    // ── палитра ───────────────────────────────────────────────────────────
    private static final int SHADOW = 0x90000000;
    private static final int PANEL  = 0xF01A1E1D;
    private static final int NAV    = 0xFF141817;
    private static final int BORDER = 0xFF2E3639;
    private static final int ACCENT = 0xFF7C97A6;
    private static final int TEXT   = 0xFFE6E8E6;
    private static final int DIM    = 0xFF8A9490;
    private static final int WARN   = 0xFFD8A24A;
    private static final int ERR    = 0xFFC86A5E;
    private static final int SELECT = 0x407C97A6;
    private static final int HOVER  = 0x22FFFFFF;
    private static final int TRACK  = 0x40000000;

    private static final int HEADER = 30;
    private static final int FOOTER = 22;
    private static final int NAV_W  = 96;
    private static final int PAD    = 14;
    private static final int ROW_H  = 16;
    private static final int BTN_H  = 15;

    private static Section section = Section.SELF;

    private static int folder() {
        return folderIdx[section.ordinal()];
    }

    private int px, py, pw, ph;
    private int contentX, contentY, contentW, contentH;

    private EditBox passwordBox;
    private EditBox reasonBox;
    private String reasonText = "";
    private String error;

    private String selectedPlayer;
    private int listScroll;
    private boolean pickingTpTarget;
    private int hdrTp, hdrState, hdrMod;   // Y заголовков групп в карточке, ставит initPlayers

    private String armed;      // id действия, ждущего подтверждения
    private long armedAt;

    public GmPanelScreen() {
        super(Component.literal("Панель мастера"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────────────────────────────────────────────────────────── init ──

    @Override
    protected void init() {
        if (!unlocked) {
            initLogin();
            return;
        }
        layout();
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(px + pw - PAD - 64, py + ph - FOOTER + 2, 64, 16).build());

        switch (section) {
            case SELF -> initSelf();
            case PLAYERS -> initPlayers();
            case MAP -> initMap();
            case WORLD -> initWorld();
            case BROADCAST -> initBroadcast();
            case PLAGUE -> initPlague();
            case EXPERIMENTAL -> initExperimental();
        }
    }

    private void layout() {
        pw = Math.min(width - 60, 452);
        ph = Math.min(height - 60, 292);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int folderBar = hasFolders() ? 16 : 0;
        contentX = px + NAV_W + 8;
        contentY = py + HEADER + 10 + folderBar;
        contentW = px + pw - PAD - contentX;
        contentH = py + ph - FOOTER - 8 - contentY;
    }

    private boolean hasFolders() {
        return section.folders.length > 0;
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

    // ── Себе ──────────────────────────────────────────────────────────────

    private void initSelf() {
        int y = contentY + 14;
        boolean spec = mePlayer() != null && mePlayer().isSpectator();
        y = add(contentX, y, contentW, spec ? "Вернуться из наблюдателей" : "Уйти в наблюдатели",
            () -> { SpectatorToggle.toggle(minecraft); onClose(); });
        add(contentX, y, contentW,
            Fullbright.isOn() ? "Ночное зрение: вкл" : "Ночное зрение: выкл",
            () -> { Fullbright.toggle(); rebuildWidgets(); });
    }

    // ── Игроки ────────────────────────────────────────────────────────────

    private void initPlayers() {
        List<PlayerInfo> players = onlinePlayers();
        if (selectedPlayer != null && players.stream().noneMatch(p -> nameOf(p).equals(selectedPlayer))) {
            selectedPlayer = null;
        }
        if (selectedPlayer == null) return;
        if (pickingTpTarget) {
            initTpPicker();
            return;
        }

        int dx = detailX();
        int dw = detailW();
        int half = (dw - 4) / 2;
        int third = (dw - 4) / 3;
        int y = contentY + 24;
        String n = selectedPlayer;

        addRenderableWidget(Button.builder(Component.literal("◀ Таблица"),
            b -> { selectedPlayer = null; rebuildWidgets(); })
            .bounds(contentX, contentY + contentH - 14, 76, 13).build());

        // верхняя строка: выдача и наблюдение
        addRenderableWidget(Button.builder(Component.literal("Выдать предмет…"),
            b -> minecraft.setScreen(new ItemGiveScreen(this, n)))
            .bounds(dx, y, half, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Наблюдать"),
            b -> { SpectatorToggle.enterSpectator(minecraft); run("spectate " + n); })
            .bounds(dx + half + 4, y, dw - half - 4, BTN_H).build());
        y += BTN_H + 12;

        // Телепорт
        hdrTp = y - 10;
        y = add(dx, y, dw, "Телепорт к нему", () -> { run("tp @s " + n); onClose(); });
        y = add(dx, y, dw, "Призвать к себе", () -> run("tp " + n + " @s"));
        y = add(dx, y, dw, "К другому игроку…", () -> { pickingTpTarget = true; rebuildWidgets(); });

        // Состояние
        y += 12;
        hdrState = y - 10;
        addRenderableWidget(Button.builder(Component.literal("Лечить"),
            b -> run("effect give " + n + " minecraft:regeneration 3 4 true"))
            .bounds(dx, y, third, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Кормить"),
            b -> run("effect give " + n + " minecraft:saturation 1 4 true"))
            .bounds(dx + third + 2, y, third, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Оба"),
            b -> { run("effect give " + n + " minecraft:regeneration 3 4 true");
                   run("effect give " + n + " minecraft:saturation 1 4 true"); })
            .bounds(dx + (third + 2) * 2, y, dw - (third + 2) * 2, BTN_H).build());
        y += BTN_H + 2;
        y = add(dx, y, dw, "Снять все эффекты", () -> run("effect clear " + n));

        // Модерация
        y += 12;
        hdrMod = y - 10;
        reasonBox = new EditBox(font, dx, y, dw, 14, Component.literal("причина"));
        reasonBox.setHint(Component.literal("причина"));
        reasonBox.setMaxLength(80);
        reasonBox.setValue(reasonText);                 // переживаем rebuildWidgets
        reasonBox.setResponder(s -> reasonText = s);
        addRenderableWidget(reasonBox);
        y += 18;
        addRenderableWidget(Button.builder(Component.literal("Кик"),
            b -> run("kick " + n + reason())).bounds(dx, y, half, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal(armedLabel("ban", "Бан")),
            b -> { if (arm("ban")) run("ban " + n + reason()); })
            .bounds(dx + half + 4, y, dw - half - 4, BTN_H).build());
        y += BTN_H + 2;
        add(dx, y, dw, armedLabel("kill", "Убить"), () -> { if (arm("kill")) run("kill " + n); });
    }

    private void initTpPicker() {
        // левый список станет выбором цели; кнопка «отмена» справа
        addRenderableWidget(Button.builder(Component.literal("Отмена"),
            b -> { pickingTpTarget = false; armed = null; rebuildWidgets(); })
            .bounds(detailX(), contentY + 24, detailW(), BTN_H).build());
    }

    // ── Мир ───────────────────────────────────────────────────────────────

    private void initWorld() {
        if (folder() == 0) {
            int y = contentY + 12, w = (contentW - 8) / 3;
            addRenderableWidget(Button.builder(Component.literal("Ясно"),
                b -> run("weather clear")).bounds(contentX, y, w, BTN_H).build());
            addRenderableWidget(Button.builder(Component.literal("Дождь"),
                b -> run("weather rain")).bounds(contentX + w + 4, y, w, BTN_H).build());
            addRenderableWidget(Button.builder(Component.literal("Гроза"),
                b -> run("weather thunder")).bounds(contentX + (w + 4) * 2, y, contentW - (w + 4) * 2, BTN_H).build());
        }
        // «Правила» рисуются и кликаются вручную — компактные строки-тумблеры
    }

    // ── Вещание ───────────────────────────────────────────────────────────

    private void initBroadcast() {
        int y = contentY + 16;
        switch (folder()) {
            case 0 -> add(contentX, y, contentW, "Открыть редактор заголовка",
                () -> minecraft.setScreen(new BroadcastEditorScreen(this, BroadcastEditorScreen.Mode.TITLE)));
            case 1 -> add(contentX, y, contentW, "Открыть редактор сообщения",
                () -> minecraft.setScreen(new BroadcastEditorScreen(this, BroadcastEditorScreen.Mode.CHAT)));
            case 2 -> add(contentX, y, contentW, "Открыть выбор звука",
                () -> minecraft.setScreen(new SoundPickerScreen(this)));
        }
    }

    // ── Чума ──────────────────────────────────────────────────────────────

    private void initPlague() {
        int y = contentY + 14;
        y = add(contentX, y, contentW, "Состояние чумы  (/plague info)",
            () -> { run("plague info"); onClose(); });
        add(contentX, y, contentW, "Карта чумы  (/plague gui)", () -> {
            returnAfterPlagueGui = true;
            sawPlagueGui = false;
            plagueGuiWait = 0;
            run("plague gui");
        });
    }

    // ── Опыты ─────────────────────────────────────────────────────────────

    private void initExperimental() {
        int y = contentY + 50, w = (contentW - 8) / 2;
        expBtn(contentX, y, w, "exp:freeze", "Заморозить мир", "tick freeze");
        expBtn(contentX + w + 4, y, contentW - w - 4, "exp:unfreeze", "Разморозить", "tick unfreeze");
        y += BTN_H + 4;
        expBtn(contentX, y, w, "exp:step1", "Шаг ×1", "tick step 1");
        expBtn(contentX + w + 4, y, contentW - w - 4, "exp:step20", "Шаг ×20", "tick step 20");
    }

    /** Кнопка опыта: любое нажатие требует подтверждения вторым кликом. */
    private void expBtn(int x, int y, int w, String id, String label, String cmd) {
        addRenderableWidget(Button.builder(Component.literal(armedLabel(id, label)),
            b -> { if (arm(id)) run(cmd); }).bounds(x, y, w, BTN_H).build());
    }

    // ── Карта ─────────────────────────────────────────────────────────────

    private void initMap() {
        addRenderableWidget(Button.builder(Component.literal("К себе"),
            b -> { mapCentered = false; }).bounds(contentX, contentY + contentH - 15, 56, 14).build());
    }

    private int mapH() {
        return contentH - 20;
    }

    private void renderMap(GuiGraphics g, int mx, int my) {
        int mapX = contentX, mapY = contentY, mapW = contentW, mapH = mapH();
        var me = mePlayer();
        if (!mapCentered && me != null) {
            mapCX = me.getX();
            mapCZ = me.getZ();
            mapCentered = true;
        }

        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0E1110);
        outline(g, mapX, mapY, mapW, mapH, BORDER);
        g.enableScissor(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1);

        double midX = mapX + mapW / 2.0, midY = mapY + mapH / 2.0;

        // фон: рельеф прогруженных чанков, как на карте-предмете
        renderMapTerrain(g, mapX, mapY, mapW, mapH, midX, midY);

        // сетка через каждые 100 блоков
        double gridPx = 100 * mapScale;
        if (gridPx >= 8) {
            double firstX = midX - ((mapCX % 100) * mapScale);
            for (double x = firstX - Math.ceil(mapW / gridPx) * gridPx; x < mapX + mapW + gridPx; x += gridPx) {
                if (x >= mapX && x <= mapX + mapW) g.fill((int) x, mapY, (int) x + 1, mapY + mapH, 0x18FFFFFF);
            }
            double firstY = midY - ((mapCZ % 100) * mapScale);
            for (double y = firstY - Math.ceil(mapH / gridPx) * gridPx; y < mapY + mapH + gridPx; y += gridPx) {
                if (y >= mapY && y <= mapY + mapH) g.fill(mapX, (int) y, mapX + mapW, (int) y + 1, 0x18FFFFFF);
            }
        }
        // оси мира (0,0)
        int zeroX = (int) Math.round(midX + (0 - mapCX) * mapScale);
        int zeroY = (int) Math.round(midY + (0 - mapCZ) * mapScale);
        if (zeroX >= mapX && zeroX <= mapX + mapW) g.fill(zeroX, mapY, zeroX + 1, mapY + mapH, 0x40FFFFFF);
        if (zeroY >= mapY && zeroY <= mapY + mapH) g.fill(mapX, zeroY, mapX + mapW, zeroY + 1, 0x40FFFFFF);

        String hover = null;
        for (GmNetwork.Pos p : GmMapData.players()) {
            int sx = (int) Math.round(midX + (p.x() - mapCX) * mapScale);
            int sy = (int) Math.round(midY + (p.z() - mapCZ) * mapScale);
            if (sx < mapX - 10 || sx > mapX + mapW + 10 || sy < mapY - 10 || sy > mapY + mapH + 10) continue;
            boolean self = me != null && p.id().equals(me.getUUID());
            if (self) g.fill(sx - 6, sy - 6, sx + 6, sy + 6, ACCENT);
            drawHead(g, p.id(), sx - 4, sy - 4);
            if (mx >= sx - 5 && mx < sx + 5 && my >= sy - 5 && my < sy + 5
                && my >= mapY && my < mapY + mapH && mx >= mapX && mx < mapX + mapW) {
                hover = p.name();
            }
        }
        g.disableScissor();

        long age = GmMapData.ageMs();
        String status = age == Long.MAX_VALUE
            ? "нет данных с сервера — нужны права оператора и мод на сервере"
            : "обновлено " + (age / 1000) + " с назад   ·   игроков: " + GmMapData.players().size();
        g.drawString(font, font.plainSubstrByWidth(status, mapW - 64), mapX + 62, mapY + mapH + 4, DIM, false);

        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    private static final MapTerrainCache mapTerrain = new MapTerrainCache();

    private void renderMapTerrain(GuiGraphics g, int mapX, int mapY, int mapW, int mapH,
                                  double midX, double midY) {
        var level = minecraft != null ? minecraft.level : null;
        if (level == null) return;

        mapTerrain.beginFrame();
        double halfW = (mapW / 2.0) / mapScale, halfH = (mapH / 2.0) / mapScale;
        int minCX = net.minecraft.util.Mth.floor(mapCX - halfW) >> 4;
        int maxCX = net.minecraft.util.Mth.floor(mapCX + halfW) >> 4;
        int minCZ = net.minecraft.util.Mth.floor(mapCZ - halfH) >> 4;
        int maxCZ = net.minecraft.util.Mth.floor(mapCZ + halfH) >> 4;
        if (maxCX - minCX > 80 || maxCZ - minCZ > 80) return;   // слишком отдалено — рельеф не рисуем

        int step = mapScale >= 0.45 ? 1 : (mapScale >= 0.18 ? 2 : 4);
        int cell = Math.max(1, (int) Math.ceil(mapScale * step));

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                int[] cols = mapTerrain.chunk(level, cx, cz);
                if (cols == null) continue;
                for (int x = 0; x < 16; x += step) {
                    for (int z = 0; z < 16; z += step) {
                        int col = cols[x * 16 + z];
                        if (col == 0) continue;
                        int sx = (int) Math.round(midX + ((cx << 4) + x - mapCX) * mapScale);
                        int sy = (int) Math.round(midY + ((cz << 4) + z - mapCZ) * mapScale);
                        if (sx < mapX - cell || sx > mapX + mapW || sy < mapY - cell || sy > mapY + mapH) continue;
                        g.fill(sx, sy, sx + cell, sy + cell, col);
                    }
                }
            }
        }
    }

    private void drawHead(GuiGraphics g, UUID id, int x, int y) {
        var info = minecraft != null && minecraft.getConnection() != null
            ? minecraft.getConnection().getPlayerInfo(id) : null;
        var skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
        PlayerFaceRenderer.draw(g, skin, x, y, 8);
    }

    // ───────────────────────────────────────────────────────────── render ──

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        if (!unlocked) {
            renderLogin(g, mx, my, pt);
            return;
        }
        if (armed != null && Util.getMillis() - armedAt > 3000) {
            armed = null;
            rebuildWidgets();
        }

        g.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.fill(px, py, px + NAV_W, py + ph, NAV);
        outline(g, px, py, pw, ph, BORDER);

        g.fill(px + 12, py + 12, px + 18, py + 18, ACCENT);
        g.drawString(font, "LMPC · Мастер игры", px + 24, py + 11, TEXT, false);
        g.fill(px, py + HEADER, px + pw, py + HEADER + 1, ACCENT);

        renderNav(g, mx, my);
        if (hasFolders()) renderFolders(g, mx, my);
        renderFooterHint(g);

        switch (section) {
            case SELF -> g.drawString(font, "Быстрые действия для себя", contentX, contentY, DIM, false);
            case PLAYERS -> renderPlayers(g, mx, my);
            case MAP -> renderMap(g, mx, my);
            case WORLD -> renderWorld(g, mx, my);
            case BROADCAST -> renderBroadcast(g);
            case PLAGUE -> renderPlague(g);
            case EXPERIMENTAL -> renderExperimental(g);
        }

        super.render(g, mx, my, pt);
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
        if (error != null) g.drawCenteredString(font, error, width / 2, height / 2 + 46, ERR);
        super.render(g, mx, my, pt);
    }

    private void renderNav(GuiGraphics g, int mx, int my) {
        int bx = px + 8, bw = NAV_W - 16, bh = ROW_H + 4, by = py + HEADER + 12;
        for (Section s : Section.values()) {
            boolean active = s == section;
            boolean hover = in(mx, my, bx, by, bw, bh);
            if (active) {
                g.fill(bx - 4, by, bx - 2, by + bh, ACCENT);
                g.fill(bx, by, bx + bw, by + bh, SELECT);
            } else if (hover) {
                g.fill(bx, by, bx + bw, by + bh, HOVER);
            }
            g.drawString(font, s.label, bx + 6, by + 6, active ? TEXT : DIM, false);
            by += bh + 3;
        }
    }

    private void renderFolders(GuiGraphics g, int mx, int my) {
        int fx = px + NAV_W + 8, fy = py + HEADER + 8;
        for (int i = 0; i < section.folders.length; i++) {
            String label = section.folders[i];
            int w = font.width(label) + 14;
            boolean active = i == folder();
            boolean hover = in(mx, my, fx, fy, w, 14);
            g.fill(fx, fy, fx + w, fy + 14, active ? SELECT : (hover ? HOVER : TRACK));
            if (active) g.fill(fx, fy + 13, fx + w, fy + 14, ACCENT);
            g.drawString(font, label, fx + 7, fy + 3, active ? TEXT : DIM, false);
            fx += w + 4;
        }
    }

    private void renderFooterHint(GuiGraphics g) {
        g.fill(px, py + ph - FOOTER, px + pw, py + ph - FOOTER + 1, BORDER);
        g.drawString(font, "Esc — закрыть   ·   Home — наблюдатель",
            px + 12, py + ph - FOOTER + 7, DIM, false);
    }

    private void renderPlayers(GuiGraphics g, int mx, int my) {
        if (selectedPlayer == null && !pickingTpTarget) {
            renderPlayerTable(g, mx, my);
            return;
        }

        List<PlayerInfo> players = onlinePlayers();

        int listX = contentX;
        int listW = Math.round(contentW * 0.40f);
        int listY = contentY;
        int listH = contentH - 16;

        String title = pickingTpTarget
            ? "Кому телепортировать " + selectedPlayer + "?"
            : "Игроки онлайн: " + players.size();
        g.drawString(font, font.plainSubstrByWidth(title, contentW), listX, listY - 12,
            pickingTpTarget ? WARN : DIM, false);

        g.fill(listX, listY, listX + listW, listY + listH, TRACK);
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
            boolean sel = name.equals(selectedPlayer) && !pickingTpTarget;
            boolean hover = in(mx, my, listX, Math.max(y, listY), listW, ROW_H)
                && my < listY + listH && my >= y;
            if (sel) g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, SELECT);
            else if (hover) g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, HOVER);
            g.fill(listX + 6, y + ROW_H / 2 - 2, listX + 10, y + ROW_H / 2 + 2, pingColor(p.getLatency()));
            g.drawString(font, font.plainSubstrByWidth(name, listW - 20), listX + 16, y + 4,
                sel ? TEXT : DIM, false);
            y += ROW_H;
        }
        g.disableScissor();

        int dx = detailX();
        if (selectedPlayer == null) {
            g.drawString(font, "Выберите игрока слева", dx, listY + 4, DIM, false);
            return;
        }
        if (pickingTpTarget) {
            drawWrapped(g, "Выберите в списке слева, к кому телепортировать "
                + selectedPlayer + ". Потребуется подтверждение.", dx, listY + 4, detailW(), DIM);
            return;
        }

        g.fill(dx, listY + 2, dx + 4, listY + 12, ACCENT);
        g.drawString(font, selectedPlayer, dx + 10, listY + 3, TEXT, false);
        PlayerInfo pi = players.stream().filter(p -> nameOf(p).equals(selectedPlayer)).findFirst().orElse(null);
        if (pi != null) {
            g.drawString(font, gameModeRu(pi.getGameMode()) + "  ·  " + pi.getLatency() + " мс",
                dx + 10, listY + 15, DIM, false);
        }
        g.drawString(font, "ТЕЛЕПОРТ", dx, hdrTp, ACCENT, false);
        g.drawString(font, "СОСТОЯНИЕ", dx, hdrState, ACCENT, false);
        g.drawString(font, "МОДЕРАЦИЯ", dx, hdrMod, ACCENT, false);
    }

    /** Живая таблица: ник, HP, еда, режим, расстояние. Клик по строке — карточка игрока. */
    private void renderPlayerTable(GuiGraphics g, int mx, int my) {
        List<PlayerInfo> players = onlinePlayers();
        java.util.Map<UUID, GmNetwork.Pos> pos = new java.util.HashMap<>();
        for (GmNetwork.Pos p : GmMapData.players()) pos.put(p.id(), p);
        var me = mePlayer();

        int x = contentX, w = contentW, top = contentY;
        int cName = x + 2;
        int cHp = x + w - 190;
        int cFood = x + w - 128;
        int cMode = x + w - 96;
        int cDist = x + w - 34;

        g.drawString(font, "Игроки: " + players.size()
            + (GmMapData.ageMs() == Long.MAX_VALUE ? "   (HP/еда — оператору при моде на сервере)" : ""),
            x, top - 12, DIM, false);
        g.drawString(font, "ник", cName, top, DIM, false);
        g.drawString(font, "HP", cHp, top, DIM, false);
        g.drawString(font, "еда", cFood, top, DIM, false);
        g.drawString(font, "реж.", cMode, top, DIM, false);
        g.drawString(font, "дист", cDist, top, DIM, false);
        g.fill(x, top + 10, x + w, top + 11, BORDER);

        int listY = top + 14, listH = contentH - 14;
        int maxScroll = Math.max(0, players.size() * ROW_H - listH);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        g.enableScissor(x, listY, x + w, listY + listH);
        int y = listY - listScroll;
        for (PlayerInfo pi : players) {
            String name = nameOf(pi);
            GmNetwork.Pos p = pos.get(pi.getProfile().getId());
            boolean hover = in(mx, my, x, Math.max(y, listY), w, ROW_H) && my >= y && my < listY + listH;
            if (hover) g.fill(x, y, x + w, y + ROW_H, HOVER);

            g.drawString(font, font.plainSubstrByWidth(name, cHp - cName - 6), cName, y + 4, TEXT, false);
            if (p != null) {
                int bar = 30;
                g.fill(cHp, y + 5, cHp + bar, y + 11, 0x40000000);
                g.fill(cHp, y + 5, cHp + Math.round(bar * Math.min(1f, p.health() / 20f)), y + 11, hpColor(p.health()));
                g.drawString(font, String.valueOf(Math.round(p.health())), cHp + bar + 3, y + 4, TEXT, false);
                g.drawString(font, String.valueOf(p.food()), cFood, y + 4, TEXT, false);
            } else {
                g.drawString(font, "—", cHp, y + 4, DIM, false);
                g.drawString(font, "—", cFood, y + 4, DIM, false);
            }
            GameType gm = p != null ? GameType.byId(p.mode()) : pi.getGameMode();
            g.drawString(font, gameModeShort(gm), cMode, y + 4, DIM, false);

            String dist = "—";
            if (p != null && me != null) {
                double ddx = p.x() - me.getX(), ddz = p.z() - me.getZ();
                dist = String.valueOf((int) Math.sqrt(ddx * ddx + ddz * ddz));
            }
            g.drawString(font, dist, cDist, y + 4, DIM, false);
            y += ROW_H;
        }
        g.disableScissor();
    }

    private static int hpColor(float hp) {
        if (hp > 14) return 0xFF6FB06F;
        if (hp > 7) return 0xFFC8B45E;
        return 0xFFC86A5E;
    }

    private static String gameModeShort(GameType t) {
        if (t == null) return "?";
        return switch (t) {
            case SURVIVAL -> "выж";
            case CREATIVE -> "твор";
            case ADVENTURE -> "прикл";
            case SPECTATOR -> "набл";
        };
    }

    private static final int RULE_ON_X  = 62;   // отступ кнопки «Вкл» от правого края области
    private static final int RULE_OFF_X = 30;
    private static final int RULE_BW = 30;

    private void renderWorld(GuiGraphics g, int mx, int my) {
        if (folder() == 0) {
            drawWrapped(g, "Смена погоды сейчас. Автосмену отключает правило «Смена погоды».",
                contentX, contentY + 34, contentW, DIM);
            return;
        }
        int y = contentY + 2;
        for (Rule r : RULES) {
            g.drawString(font, font.plainSubstrByWidth(r.label(), contentW - RULE_ON_X - 4),
                contentX + 2, y + 4, TEXT, false);
            int onX = contentX + contentW - RULE_ON_X;
            int offX = contentX + contentW - RULE_OFF_X;
            miniBtn(g, onX, y, "Вкл", in(mx, my, onX, y, RULE_BW, ROW_H - 2));
            miniBtn(g, offX, y, "Выкл", in(mx, my, offX, y, RULE_BW, ROW_H - 2));
            y += ROW_H;
        }
    }

    private void miniBtn(GuiGraphics g, int x, int y, String label, boolean hover) {
        g.fill(x, y, x + RULE_BW, y + ROW_H - 2, hover ? HOVER : TRACK);
        outline(g, x, y, RULE_BW, ROW_H - 2, BORDER);
        g.drawString(font, label, x + (RULE_BW - font.width(label)) / 2, y + 3, TEXT, false);
    }

    private void renderPlague(GuiGraphics g) {
        g.drawString(font, "Мод plague core", contentX, contentY, DIM, false);
        drawWrapped(g, "«Состояние» пишет сводку в чат. «Карта» открывает экран чумы — "
            + "по Esc вернётесь сюда.", contentX, contentY + 62, contentW, DIM);
    }

    private void renderExperimental(GuiGraphics g) {
        g.drawString(font, "⚠ Опасные функции — каждая по двойному клику", contentX, contentY, WARN, false);
        drawWrapped(g, "Стоп-кадр останавливает весь мир: мобов, рост, физику. "
            + "«Шаг» проматывает мир на N тактов замороженным. Не забудьте разморозить.",
            contentX, contentY + 12, contentW, DIM);
    }

    private void renderBroadcast(GuiGraphics g) {
        String hint = switch (folder()) {
            case 0 -> "Крупный текст в центре экрана всем игрокам (/title). "
                + "В редакторе — цвет, начертание, подзаголовок, предпросмотр.";
            case 1 -> "Строка в чат всем от лица сервера (/tellraw). "
                + "В редакторе — цвет и начертание.";
            default -> "Любой звук игры всем или одному игроку, с громкостью и тоном.";
        };
        drawWrapped(g, hint, contentX, contentY + 34, contentW, DIM);
    }

    // ────────────────────────────────────────────────────────────── mouse ──

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (unlocked) {
            // разделы
            int bx = px + 8, bw = NAV_W - 16, bh = ROW_H + 4, by = py + HEADER + 12;
            for (Section s : Section.values()) {
                if (in(mx, my, bx, by, bw, bh)) {
                    if (s != section) {
                        section = s;
                        listScroll = 0;
                        pickingTpTarget = false;
                        armed = null;
                        rebuildWidgets();
                    }
                    return true;
                }
                by += bh + 3;
            }
            // папки
            if (hasFolders()) {
                int fx = px + NAV_W + 8, fy = py + HEADER + 8;
                for (int i = 0; i < section.folders.length; i++) {
                    int w = font.width(section.folders[i]) + 14;
                    if (in(mx, my, fx, fy, w, 14)) {
                        folderIdx[section.ordinal()] = i;
                        pickingTpTarget = false;
                        armed = null;
                        rebuildWidgets();
                        return true;
                    }
                    fx += w + 4;
                }
            }
            // карта: начать перетаскивание
            if (section == Section.MAP && in(mx, my, contentX, contentY, contentW, mapH())) {
                mapDragging = true;
                return true;
            }
            // правила: две кнопки на строку
            if (section == Section.WORLD && folder() == 1) {
                int y = contentY + 2;
                int onX = contentX + contentW - RULE_ON_X;
                int offX = contentX + contentW - RULE_OFF_X;
                for (Rule r : RULES) {
                    if (in(mx, my, onX, y, RULE_BW, ROW_H - 2)) {
                        run("gamerule " + r.id() + " true");
                        return true;
                    }
                    if (in(mx, my, offX, y, RULE_BW, ROW_H - 2)) {
                        run("gamerule " + r.id() + " false");
                        return true;
                    }
                    y += ROW_H;
                }
            }
            // таблица игроков: клик по строке — карточка
            if (section == Section.PLAYERS && selectedPlayer == null && !pickingTpTarget) {
                int listY = contentY + 14, listH = contentH - 14;
                if (in(mx, my, contentX, listY, contentW, listH)) {
                    List<PlayerInfo> players = onlinePlayers();
                    int idx = (int) ((my - listY + listScroll) / ROW_H);
                    if (idx >= 0 && idx < players.size()) {
                        selectedPlayer = nameOf(players.get(idx));
                        listScroll = 0;
                        rebuildWidgets();
                    }
                    return true;
                }
            }
            // узкий список игроков (когда карточка открыта)
            if (section == Section.PLAYERS && (selectedPlayer != null || pickingTpTarget)) {
                int listX = contentX;
                int listW = Math.round(contentW * 0.40f);
                int listY = contentY, listH = contentH - 16;
                if (in(mx, my, listX, listY, listW, listH)) {
                    List<PlayerInfo> players = onlinePlayers();
                    int idx = (int) ((my - listY + listScroll) / ROW_H);
                    if (idx >= 0 && idx < players.size()) {
                        String name = nameOf(players.get(idx));
                        if (pickingTpTarget) {
                            if (!name.equals(selectedPlayer)) {
                                if (arm("tp2:" + name)) {
                                    run("tp " + selectedPlayer + " " + name);
                                    pickingTpTarget = false;
                                }
                            }
                        } else {
                            selectedPlayer = name;
                        }
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
        if (unlocked && section == Section.PLAYERS) {
            listScroll -= (int) (dys * ROW_H);
            return true;
        }
        if (unlocked && section == Section.MAP && in(mx, my, contentX, contentY, contentW, mapH())) {
            double midX = contentX + contentW / 2.0, midY = contentY + mapH() / 2.0;
            double wx = mapCX + (mx - midX) / mapScale;
            double wz = mapCZ + (my - midY) / mapScale;
            mapScale = Math.max(0.02, Math.min(6.0, mapScale * (dys > 0 ? 1.2 : 1 / 1.2)));
            mapCX = wx - (mx - midX) / mapScale;
            mapCZ = wz - (my - midY) / mapScale;
            return true;
        }
        return super.mouseScrolled(mx, my, dxs, dys);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dragX, double dragY) {
        if (unlocked && section == Section.MAP && mapDragging) {
            mapCX -= dragX / mapScale;
            mapCZ -= dragY / mapScale;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        mapDragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!unlocked && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                && passwordBox != null && passwordBox.isFocused()) {
            tryUnlock();
            return true;
        }
        if (unlocked && key == GLFW.GLFW_KEY_ESCAPE && pickingTpTarget) {
            pickingTpTarget = false;
            armed = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ──────────────────────────────────────────────────────────── helpers ──

    private int add(int x, int y, int w, String label, Runnable onClick) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> onClick.run())
            .bounds(x, y, w, BTN_H).build());
        return y + BTN_H + 1;
    }

    private int detailX() {
        return contentX + Math.round(contentW * 0.40f) + 12;
    }

    private int detailW() {
        return contentX + contentW - detailX();
    }

    private boolean arm(String id) {
        if (id.equals(armed)) {
            armed = null;
            return true;
        }
        armed = id;
        armedAt = Util.getMillis();
        rebuildWidgets();
        return false;
    }

    private String armedLabel(String id, String normal) {
        return id.equals(armed) ? "Точно? ещё раз" : normal;
    }

    private String reason() {
        String r = reasonText.trim();
        return r.isEmpty() ? "" : " " + r;
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

    private void run(String cmd) {
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(cmd);
        }
    }

    private net.minecraft.client.player.LocalPlayer mePlayer() {
        return minecraft != null ? minecraft.player : null;
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

    private static String gameModeRu(GameType t) {
        if (t == null) return "?";
        return switch (t) {
            case SURVIVAL -> "Выживание";
            case CREATIVE -> "Творческий";
            case ADVENTURE -> "Приключение";
            case SPECTATOR -> "Наблюдатель";
        };
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(text), maxWidth)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
