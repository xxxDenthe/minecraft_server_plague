package dev.denthe.gmtools.client;

import dev.denthe.gmtools.net.GmNetwork;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
import java.util.Locale;
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
        PLAGUE("Чума", "Общее", "Голос"),
        GRAPHICS("Графика", "Кадр", "Ночь", "Туман", "Небо"),
        EXPERIMENTAL("Опыты"), LOG("Журнал");
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
    private int hdrState, hdrMod;   // Y заголовков групп в карточке игрока, ставит initPlayers

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
            case GRAPHICS -> initGraphics();
            case EXPERIMENTAL -> initExperimental();
            case LOG -> add(contentX, contentY + 30, contentW, "Показать журнал в чате",
                () -> { run("gmtools log"); onClose(); });
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
        y = add(contentX, y, contentW, "Режим-призрак (вкл/выкл)",
            () -> { run("gmtools ghost"); onClose(); });
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
        if (pickingTpTarget) {
            addRenderableWidget(Button.builder(Component.literal("Отмена"),
                b -> { pickingTpTarget = false; armed = null; rebuildWidgets(); })
                .bounds(contentX + contentW - 64, contentY - 13, 64, 12).build());
            return;
        }
        if (selectedPlayer == null) return;   // таблица рисуется вручную

        String n = selectedPlayer;
        int x = contentX, w = contentW;

        addRenderableWidget(Button.builder(Component.literal("◀ Таблица"),
            b -> { selectedPlayer = null; rebuildWidgets(); }).bounds(x, contentY, 72, 13).build());

        int y = contentY + 22;
        buttonRow(x, y, w, new String[] { "Выдать…", "Наблюдать", "Телепорт", "Призвать" },
            new Runnable[] {
                () -> minecraft.setScreen(new ItemGiveScreen(this, n)),
                () -> { SpectatorToggle.enterSpectator(minecraft); run("spectate " + n); },
                () -> { run("tp @s " + n); onClose(); },
                () -> run("tp " + n + " @s"),
            });
        y += BTN_H + 3;
        buttonRow(x, y, w, new String[] { "Инвентарь", "Заморозить / разморозить" },
            new Runnable[] {
                () -> run("gmtools inv " + n),
                () -> run("gmtools freeze " + n),
            });
        y += BTN_H + 3;
        addRenderableWidget(Button.builder(Component.literal("Телепортировать к другому игроку…"),
            b -> { pickingTpTarget = true; rebuildWidgets(); }).bounds(x, y, w, BTN_H).build());

        y += BTN_H + 14;
        hdrState = y - 11;
        buttonRow(x, y, w, new String[] { "Лечить", "Кормить", "Оба", "Снять эфф." },
            new Runnable[] {
                () -> run("effect give " + n + " minecraft:regeneration 3 4 true"),
                () -> run("effect give " + n + " minecraft:saturation 1 4 true"),
                () -> { run("effect give " + n + " minecraft:regeneration 3 4 true");
                        run("effect give " + n + " minecraft:saturation 1 4 true"); },
                () -> run("effect clear " + n),
            });

        y += BTN_H + 14;
        hdrMod = y - 11;
        reasonBox = new EditBox(font, x, y, w, 14, Component.literal("причина"));
        reasonBox.setHint(Component.literal("причина кика / бана (необязательно)"));
        reasonBox.setMaxLength(80);
        reasonBox.setValue(reasonText);
        reasonBox.setResponder(s -> reasonText = s);
        addRenderableWidget(reasonBox);
        y += 18;
        buttonRow(x, y, w, new String[] { "Кик", armedLabel("ban", "Бан"), armedLabel("kill", "Убить") },
            new Runnable[] {
                () -> run("kick " + n + reason()),
                () -> { if (arm("ban")) run("ban " + n + reason()); },
                () -> { if (arm("kill")) run("kill " + n); },
            });
    }

    /** Ряд одинаковых кнопок на всю ширину. */
    private void buttonRow(int x, int y, int w, String[] labels, Runnable[] actions) {
        int n = labels.length, gap = 3;
        int bw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int bx = x + i * (bw + gap);
            int ww = (i == n - 1) ? x + w - bx : bw;
            Runnable a = actions[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> a.run())
                .bounds(bx, y, ww, BTN_H).build());
        }
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
        if (folder() == 1) {
            initVoice();
            return;
        }
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

    // ── Голос больного ────────────────────────────────────────────────────
    // Ползунки правят голос в Simple Voice Chat: чем сильнее стадия, тем
    // ниже и тяжелее речь. Считается голос на сервере, поэтому меняем
    // командой /plague voice set, а текущие числа спрашиваем у сервера
    // (/plague voice sync) и читаем из plaguecore рефлексией.

    /** Ручки уровня силы в том порядке, в каком их приятно крутить. */
    private static final String[] VOICE_ROWS =
        { "semitones", "muffle", "rasp", "breath", "tremor" };

    private void initVoice() {
        if (!VoiceAccess.available()) return;
        if (!VoiceAccess.synced()) run("plague voice sync");

        int колонка = (contentW - 6) / 2;
        int y = contentY + 12;
        for (String основа : VOICE_ROWS) {
            for (int уровень = 0; уровень < 2; уровень++) {
                String id = основа + (уровень + 1);
                if (VoiceAccess.level(id) < 0) continue;
                addRenderableWidget(new VoiceSlider(
                    contentX + уровень * (колонка + 6), y, колонка, 13, id,
                    VoiceAccess.label(id)));
            }
            y += ROW_H;
        }

        y += 2;
        addRenderableWidget(new VoiceSlider(contentX, y, contentW, 13,
            "tremorHz", VoiceAccess.label("tremorHz")));
        y += ROW_H;
        addRenderableWidget(new VoiceSlider(contentX, y, contentW, 13,
            "minStage", VoiceAccess.label("minStage")));

        addRenderableWidget(Button.builder(Component.literal("Спросить сервер"), b -> {
            run("plague voice sync");
            rebuildWidgets();
        }).bounds(contentX, contentY + contentH - 14, 104, 13).build());
    }

    private void renderVoice(GuiGraphics g) {
        if (!VoiceAccess.available()) {
            g.drawString(font, "Мод plaguecore не найден в паке.", contentX, contentY + 4, DIM, false);
            return;
        }
        int ст = VoiceAccess.minStage();
        int колонка = (contentW - 6) / 2;
        g.drawString(font, "стадия " + ст, contentX, contentY, DIM, false);
        g.drawString(font, "стадия " + (ст + 1) + "+", contentX + колонка + 6, contentY, DIM, false);
        g.drawString(font, VoiceAccess.synced()
                ? "правки уходят на сервер и слышны сразу · пишутся в конфиг"
                : "числа местные: сервер ещё не ответил",
            contentX, contentY + contentH - 26, DIM, false);
    }

    /**
     * Ползунок одной ручки голоса. Локального превью нет и быть не может:
     * голос портится на сервере, у клиента этих чисел просто не спросишь.
     * Поэтому значение уходит на отпускании мыши, одной командой.
     */
    private final class VoiceSlider extends AbstractSliderButton {
        private final String id;
        private final double lo, hi;
        private final boolean intKind;
        private final String label;

        VoiceSlider(int x, int y, int w, int h, String id, String label) {
            super(x, y, w, h, Component.empty(),
                VoiceAccess.max(id) > VoiceAccess.min(id)
                    ? (VoiceAccess.get(id) - VoiceAccess.min(id))
                      / (VoiceAccess.max(id) - VoiceAccess.min(id))
                    : 0.0);
            this.id = id;
            this.lo = VoiceAccess.min(id);
            this.hi = VoiceAccess.max(id);
            this.intKind = VoiceAccess.isInt(id);
            this.label = label;
            updateMessage();
        }

        private double raw() {
            double v = lo + this.value * (hi - lo);
            return intKind ? Math.round(v) : v;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ":  " + (intKind
                ? String.valueOf((long) raw())
                : String.format(Locale.ROOT, "%.2f", raw()))));
        }

        @Override
        protected void applyValue() {
            // Значение живёт только в ползунке до отпускания мыши.
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            run(String.format(Locale.ROOT, "plague voice set %s %.3f", id, raw()));
        }
    }

    // ── Опыты ─────────────────────────────────────────────────────────────

    private void initExperimental() {
        int y = contentY + 52, w = (contentW - 8) / 2;
        expBtn(contentX, y, w, "exp:freeze", "Заморозить мир", () -> run("tick freeze"));
        expBtn(contentX + w + 4, y, contentW - w - 4, "exp:unfreeze", "Разморозить", () -> run("tick unfreeze"));
        y += BTN_H + 4;
        expBtn(contentX, y, w, "exp:step1", "Шаг ×1", () -> run("tick step 1"));
        expBtn(contentX + w + 4, y, contentW - w - 4, "exp:step20", "Шаг ×20", () -> run("tick step 20"));
        y += BTN_H + 12;
        expBtn(contentX, y, w, "exp:pause", "Пауза сессии", () -> {
            run("tick freeze"); run("plague pause");
        });
        expBtn(contentX + w + 4, y, contentW - w - 4, "exp:resume", "Продолжить сессию", () -> {
            run("tick unfreeze"); run("plague resume");
        });
    }

    /** Кнопка опыта: любое нажатие требует подтверждения вторым кликом. */
    private void expBtn(int x, int y, int w, String id, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(armedLabel(id, label)),
            b -> { if (arm(id)) action.run(); }).bounds(x, y, w, BTN_H).build());
    }

    // ── Карта ─────────────────────────────────────────────────────────────

    private void initMap() {
        addRenderableWidget(Button.builder(Component.literal("К себе"),
            b -> { mapCentered = false; })
            .bounds(contentX, contentY + contentH - 14, 46, 13).build());
    }

    private int mapH() {
        return contentH - 16;
    }

    /** Экранная точка в мировые координаты (X, Z). */
    private double[] screenToWorld(double sx, double sy) {
        double midX = contentX + contentW / 2.0, midY = contentY + mapH() / 2.0;
        return new double[] { mapCX + (sx - midX) / mapScale, mapCZ + (sy - midY) / mapScale };
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
        boolean inMap = mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH;

        // метки — иконкой предмета
        for (GmNetwork.Mark m : GmMapData.marks()) {
            int sx = (int) Math.round(midX + (m.x() - mapCX) * mapScale);
            int sy = (int) Math.round(midY + (m.z() - mapCZ) * mapScale);
            if (sx < mapX - 10 || sx > mapX + mapW + 10 || sy < mapY - 10 || sy > mapY + mapH + 10) continue;
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.renderItem(MarkerIcons.stack(m.icon()), -8, -8);
            g.pose().popPose();
            if (inMap && mx >= sx - 6 && mx < sx + 6 && my >= sy - 6 && my < sy + 6) {
                hover = "метка: " + m.name();
            }
        }

        for (GmNetwork.Pos p : GmMapData.players()) {
            int sx = (int) Math.round(midX + (p.x() - mapCX) * mapScale);
            int sy = (int) Math.round(midY + (p.z() - mapCZ) * mapScale);
            if (sx < mapX - 10 || sx > mapX + mapW + 10 || sy < mapY - 10 || sy > mapY + mapH + 10) continue;
            boolean self = me != null && p.id().equals(me.getUUID());
            if (self) g.fill(sx - 6, sy - 6, sx + 6, sy + 6, ACCENT);
            drawHead(g, p.id(), sx - 4, sy - 4);
            if (inMap && mx >= sx - 5 && mx < sx + 5 && my >= sy - 5 && my < sy + 5) hover = p.name();
        }
        g.disableScissor();

        long age = GmMapData.ageMs();
        if (age == Long.MAX_VALUE) {
            g.drawCenteredString(font, "нет данных с сервера (нужны права оператора)",
                mapX + mapW / 2, mapY + mapH / 2 - 4, DIM);
        } else {
            g.drawString(font, "игроков: " + GmMapData.players().size()
                + "   меток: " + GmMapData.marks().size(), mapX + 3, mapY + 3, DIM, false);
        }
        g.drawString(font, "ПКМ — поставить метку   ·   ЛКМ по метке — убрать",
            mapX + 54, mapY + mapH + 4, DIM, false);

        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    private static final MapTerrainCache mapTerrain = new MapTerrainCache();

    private void renderMapTerrain(GuiGraphics g, int mapX, int mapY, int mapW, int mapH,
                                  double midX, double midY) {
        var level = minecraft != null ? minecraft.level : null;
        mapTerrain.draw(g, level, mapX, mapY, mapW, mapH, mapCX, mapCZ, mapScale);
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
            case PLAGUE -> { if (folder() == 1) renderVoice(g); else renderPlague(g); }
            case GRAPHICS -> renderGraphics(g);
            case EXPERIMENTAL -> renderExperimental(g);
            case LOG -> {
                g.drawString(font, "Журнал действий мастеров", contentX, contentY, DIM, false);
                drawWrapped(g, "Последние команды операторов (кик, бан, тп, выдача, чума, "
                    + "правила и т.п.). Выводится в чат.", contentX, contentY + 12, contentW, DIM);
            }
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

    private static final int TROW = 15;   // высота строки списка игроков

    private void renderPlayers(GuiGraphics g, int mx, int my) {
        if (selectedPlayer != null && !pickingTpTarget) {
            renderPlayerCard(g);
        } else {
            renderPlayerTable(g, mx, my);
        }
    }

    private void renderPlayerCard(GuiGraphics g) {
        int x = contentX, w = contentW;
        GmNetwork.Pos p = null;
        for (GmNetwork.Pos q : GmMapData.players()) {
            if (q.name().equals(selectedPlayer)) { p = q; break; }
        }
        PlayerInfo pi = onlinePlayers().stream()
            .filter(q -> nameOf(q).equals(selectedPlayer)).findFirst().orElse(null);

        g.drawString(font, selectedPlayer, x + 82, contentY + 1, TEXT, false);
        StringBuilder sub = new StringBuilder();
        if (pi != null) sub.append(gameModeRu(pi.getGameMode())).append("  ·  ").append(pi.getLatency()).append(" мс");
        if (p != null) sub.append("  ·  HP ").append(Math.round(p.health())).append("  ·  еда ").append(p.food());
        g.drawString(font, font.plainSubstrByWidth(sub.toString(), w - 84), x + 82, contentY + 11, DIM, false);
        g.fill(x, contentY + 18, x + w, contentY + 19, BORDER);

        g.drawString(font, "СОСТОЯНИЕ", x, hdrState, ACCENT, false);
        g.drawString(font, "МОДЕРАЦИЯ", x, hdrMod, ACCENT, false);
    }

    /** Спокойный список игроков: голова, ник, тонкая полоса HP, дистанция. */
    private void renderPlayerTable(GuiGraphics g, int mx, int my) {
        List<PlayerInfo> players = onlinePlayers();
        java.util.Map<UUID, GmNetwork.Pos> pos = new java.util.HashMap<>();
        for (GmNetwork.Pos p : GmMapData.players()) pos.put(p.id(), p);
        var me = mePlayer();

        int x = contentX, w = contentW, top = contentY;
        int rDist = x + w;
        int hpX = rDist - 84;

        if (pickingTpTarget) {
            g.fill(x, top - 13, x + w, top - 1, 0x33D8A24A);
            g.drawString(font, font.plainSubstrByWidth(
                "Куда телепортировать " + selectedPlayer + "?  Выберите игрока.", w - 6),
                x + 3, top - 11, WARN, false);
        } else {
            g.drawString(font, "Игроки — " + players.size(), x, top - 11, DIM, false);
        }
        g.fill(x, top - 1, x + w, top, BORDER);

        int listY = top + 1, listH = contentH - 1;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, players.size() * TROW - listH)));

        g.enableScissor(x, listY, x + w, listY + listH);
        int y = listY - listScroll;
        for (PlayerInfo pi : players) {
            GmNetwork.Pos p = pos.get(pi.getProfile().getId());
            boolean vis = y + TROW > listY && y < listY + listH;
            if (vis) {
                boolean hover = in(mx, my, x, y, w, TROW) && my >= listY && my < listY + listH;
                if (hover) {
                    g.fill(x, y, x + w, y + TROW, HOVER);
                    g.fill(x, y, x + 2, y + TROW, ACCENT);
                }
                drawHead(g, pi.getProfile().getId(), x + 4, y + (TROW - 10) / 2);
                g.drawString(font, font.plainSubstrByWidth(nameOf(pi), hpX - x - 20),
                    x + 18, y + (TROW - 8) / 2, TEXT, false);

                GameType gm = p != null ? GameType.byId(p.mode()) : pi.getGameMode();
                if (gm != null && gm != GameType.SURVIVAL) {
                    g.drawString(font, gameModeShort(gm), x + 20 + font.width(nameOf(pi)),
                        y + (TROW - 8) / 2, 0xFF7C97A6, false);
                }

                int cy = y + TROW / 2;
                if (p != null) {
                    int bw = 46, fw = Math.round(bw * Math.min(1f, p.health() / 20f));
                    g.fill(hpX, cy - 2, hpX + bw, cy + 2, 0x33000000);
                    g.fill(hpX, cy - 2, hpX + fw, cy + 2, hpColor(p.health()));
                    g.drawString(font, String.valueOf(Math.round(p.health())),
                        hpX + bw + 4, y + (TROW - 8) / 2, DIM, false);
                }
                String dist = "";
                if (p != null && me != null) {
                    double ddx = p.x() - me.getX(), ddz = p.z() - me.getZ();
                    dist = (int) Math.sqrt(ddx * ddx + ddz * ddz) + " м";
                }
                rightStr(g, dist, rDist, y + (TROW - 8) / 2, DIM);
            }
            y += TROW;
        }
        g.disableScissor();
    }

    private void rightStr(GuiGraphics g, String s, int rightX, int y, int color) {
        g.drawString(font, s, rightX - font.width(s), y, color, false);
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

    // ── Графика ───────────────────────────────────────────────────────────
    // Живой редактор цветокора мода lmpc_shade. Значения дёргаются
    // рефлексией через ShadeAccess — прямой зависимости между джарами нет.
    // Ползунки меняют конфиг в памяти сразу, «Сохранить» пишет в файл.

    private void initGraphics() {
        if (!ShadeAccess.available()) return;
        String grp = section.folders[folder()];
        int x = contentX, y = contentY + 2, w = contentW;
        for (String id : ShadeAccess.ids()) {
            if (grp.equals(ShadeAccess.group(id))) {
                y = addShadeControl(x, y, w, id);
            }
        }
        int by = contentY + contentH - 14;
        addRenderableWidget(Button.builder(Component.literal("Вернуть подобранные"), b -> {
            run("lmpcshade reset");
            ShadeAccess.resetAll();
            rebuildWidgets();
        }).bounds(x, by, 128, 13).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить локально"),
                b -> ShadeAccess.save())
            .bounds(x + w - 108, by, 108, 13).build());
    }

    /**
     * Отправить значение на сервер: он сохранит его в мире и разошлёт
     * всем игрокам. Право проверяет сервер (OP). Локальный превью уже
     * выставлен вызывающим — сервер потом пришлёт то же значение
     * авторитетно. В одиночке идёт через встроенный сервер.
     */
    private void pushShade(String id, String value) {
        run("lmpcshade set " + id + " " + value);
    }

    /** Кладёт виджет(ы) под один параметр, возвращает следующий y. */
    private int addShadeControl(int x, int y, int w, String id) {
        String kind = ShadeAccess.kind(id);
        String label = ShadeAccess.label(id) + (ShadeAccess.live(id) ? "" : " *");
        switch (kind) {
            case "BOOL" -> {
                addRenderableWidget(Button.builder(boolMsg(label, shadeBool(id)), b -> {
                    boolean nv = !shadeBool(id);
                    ShadeAccess.set(id, nv);
                    pushShade(id, String.valueOf(nv));
                    b.setMessage(boolMsg(label, nv));
                }).bounds(x, y, w, 13).build());
                return y + ROW_H;
            }
            case "HEX" -> {
                int[] rgb = parseHex(String.valueOf(ShadeAccess.get(id)));
                String base = ShadeAccess.label(id);
                for (int ch = 0; ch < 3; ch++) {
                    addRenderableWidget(new HexChannelSlider(x, y + ch * ROW_H, w, 13,
                        base + " " + "RGB".charAt(ch), id, ch, rgb[ch]));
                }
                return y + 3 * ROW_H;
            }
            default -> {
                addRenderableWidget(new ShadeSlider(x, y, w, 13, label, id,
                    ShadeAccess.min(id), ShadeAccess.max(id), "INT".equals(kind),
                    ((Number) ShadeAccess.get(id)).doubleValue()));
                return y + ROW_H;
            }
        }
    }

    private void renderGraphics(GuiGraphics g) {
        if (!ShadeAccess.available()) {
            g.drawString(font, "Мод lmpc_shade не найден в паке.", contentX, contentY + 4, DIM, false);
            return;
        }
        g.drawString(font, "на сервере правки применяются у всех · * — после перезахода в мир",
            contentX, contentY + contentH - 26, DIM, false);
    }

    private static boolean shadeBool(String id) {
        return Boolean.TRUE.equals(ShadeAccess.get(id));
    }

    private static Component boolMsg(String label, boolean v) {
        return Component.literal(label + ":  " + (v ? "вкл" : "выкл"));
    }

    private static int[] parseHex(String s) {
        try {
            int v = Integer.parseInt(s.replace("#", "").trim(), 16);
            return new int[] { (v >> 16) & 255, (v >> 8) & 255, v & 255 };
        } catch (RuntimeException e) {
            return new int[] { 128, 128, 128 };
        }
    }

    // Ползунки — нестатические: при отпускании мыши шлют значение на
    // сервер через pushShade. Во время перетаскивания — только локальный
    // превью (applyValue). Правки стрелками клавиатуры на сервер не
    // уходят до следующего перетаскивания — мелочь, GM тянет мышью.

    private final class ShadeSlider extends AbstractSliderButton {
        private final String id;
        private final double lo, hi;
        private final boolean intKind;
        private final String label;

        ShadeSlider(int x, int y, int w, int h, String label, String id,
                    double lo, double hi, boolean intKind, double cur) {
            super(x, y, w, h, Component.empty(), hi > lo ? (cur - lo) / (hi - lo) : 0.0);
            this.id = id;
            this.lo = lo;
            this.hi = hi;
            this.intKind = intKind;
            this.label = label;
            updateMessage();
        }

        private String current() {
            double v = lo + this.value * (hi - lo);
            return intKind ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.3f", v);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ":  " + (intKind
                ? current()
                : String.format(Locale.ROOT, "%.2f", lo + this.value * (hi - lo)))));
        }

        @Override
        protected void applyValue() {
            ShadeAccess.set(id, current());
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            pushShade(id, current());
        }
    }

    /** Один канал hex-цвета (0..255); при движении пересобирает весь цвет. */
    private final class HexChannelSlider extends AbstractSliderButton {
        private final String id;
        private final int channel;
        private final String label;

        HexChannelSlider(int x, int y, int w, int h, String label, String id, int channel, int v0) {
            super(x, y, w, h, Component.empty(), v0 / 255.0);
            this.id = id;
            this.channel = channel;
            this.label = label;
            updateMessage();
        }

        private String current() {
            int[] rgb = parseHex(String.valueOf(ShadeAccess.get(id)));
            rgb[channel] = (int) Math.round(this.value * 255);
            return String.format(Locale.ROOT, "%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ":  " + (int) Math.round(this.value * 255)));
        }

        @Override
        protected void applyValue() {
            ShadeAccess.set(id, current());
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            pushShade(id, current());
        }
    }

    private void renderExperimental(GuiGraphics g) {
        g.drawString(font, "⚠ Опасные функции — каждая по двойному клику", contentX, contentY, WARN, false);
        drawWrapped(g, "Стоп-кадр останавливает весь мир: мобов, рост, время, физику. "
            + "«Пауза сессии» — то же плюс пауза чумы, одной кнопкой. Не забудьте вернуть.",
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
            // карта: ПКМ — новая метка; ЛКМ по метке — убрать; иначе перетаскивание
            if (section == Section.MAP && in(mx, my, contentX, contentY, contentW, mapH())) {
                if (btn == 1) {
                    double[] w = screenToWorld(mx, my);
                    minecraft.setScreen(new MarkerDialogScreen(this, w[0], w[1]));
                    return true;
                }
                double midX = contentX + contentW / 2.0, midY = contentY + mapH() / 2.0;
                for (GmNetwork.Mark m : GmMapData.marks()) {
                    int sx = (int) Math.round(midX + (m.x() - mapCX) * mapScale);
                    int sy = (int) Math.round(midY + (m.z() - mapCZ) * mapScale);
                    if (mx >= sx - 6 && mx < sx + 6 && my >= sy - 6 && my < sy + 6) {
                        if (arm("unmark:" + m.name())) run("gmtools unmark " + m.name());
                        return true;
                    }
                }
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
            // таблица игроков: клик по строке — выбор или цель телепорта
            if (section == Section.PLAYERS && (selectedPlayer == null || pickingTpTarget)) {
                int listY = contentY + 1, listH = contentH - 1;
                if (in(mx, my, contentX, listY, contentW, listH)) {
                    List<PlayerInfo> players = onlinePlayers();
                    int idx = (int) ((my - listY + listScroll) / TROW);
                    if (idx >= 0 && idx < players.size()) {
                        String name = nameOf(players.get(idx));
                        if (pickingTpTarget) {
                            if (!name.equals(selectedPlayer) && arm("tp2:" + name)) {
                                run("tp " + selectedPlayer + " " + name);
                                pickingTpTarget = false;
                                rebuildWidgets();
                            }
                        } else {
                            selectedPlayer = name;
                            listScroll = 0;
                            rebuildWidgets();
                        }
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
