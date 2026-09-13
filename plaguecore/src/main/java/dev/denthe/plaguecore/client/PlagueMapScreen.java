package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Админский экран: карта заражения плюс кнопки.
 *
 * Экран ничего не решает сам — он рисует последний снимок с сервера и
 * шлёт номера действий. Любое действие возвращается новым снимком,
 * поэтому картинка не может разойтись с сервером.
 */
public class PlagueMapScreen extends Screen {

    /** Цвета уровней: от чистой земли до логова. */
    private static final int[] ЦВЕТА = {
        0xFF1B241B, // 0 чисто
        0xFF5C6B2E, // 1
        0xFF8A7A22, // 2
        0xFFB05A18, // 3
        0xFF8E2020, // 4
        0xFFC030C0  // 5 логово
    };

    private static final int ФОН_КАРТЫ = 0xFF101410;
    private static final int РАМКА = 0xFF4A4A4A;
    private static final int ОСЬ = 0x40FFFFFF;
    private static final int ТЕКСТ = 0xFFE0E0E0;
    private static final int ТУСКЛЫЙ = 0xFF909090;

    /** Отступ карты сверху: под заголовок и три строки состояния. */
    private static final int картаСверху = 52;

    private PlagueNetwork.Snapshot данные;

    private int клетка;
    private int картаX;
    private int картаY;
    private int картаПиксели;

    /**
     * Сколько чанков сетки ложится в одну клетку карты.
     *
     * Квадрат чумы задаётся командой и бывает в сотни чанков — на экран
     * он клетка-в-чанк не влезает никак. Тогда карта показывает не каждый
     * чанк, а наибольший уровень в квадратике шаг×шаг: пятна видно, а
     * точные числа всё равно смотрят подсказкой под курсором.
     */
    private int шаг = 1;

    /** Сторона карты в клетках после прореживания. */
    private int ячеек;

    /** Уровни, уже прореженные до клеток карты. Считаются при init. */
    private byte[] картинка = new byte[0];

    public PlagueMapScreen(PlagueNetwork.Snapshot снимок) {
        super(Component.literal("Ядро чумы"));
        this.данные = снимок;
    }

    public void обновить(PlagueNetwork.Snapshot снимок) {
        this.данные = снимок;
        rebuildWidgets(); // подписи кнопок зависят от состояния — паузы, например
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int сторона = данные.size();

        // Карта занимает всё, что осталось между шапкой и подвалом.
        // Подвал: подсказка, два ряда кнопок и легенда — 84 пикселя.
        int доступно = Math.max(16, Math.min(height - (картаСверху + 84), width - 40));
        шаг = (сторона + доступно - 1) / доступно;   // округление вверх
        ячеек = (сторона + шаг - 1) / шаг;
        клетка = размерКлетки(доступно, ячеек);
        картаПиксели = клетка * ячеек;
        прорядить();
        картаX = (width - картаПиксели) / 2;
        картаY = картаСверху;

        int низ = картаY + картаПиксели + 16;
        int ш = 76, в = 20, зазор = 4;

        // ── первый ряд: течение времени ────────────────────────────────
        int x = (width - (ш * 4 + зазор * 3)) / 2;
        addRenderableWidget(Button.builder(Component.literal("Ночь"),
            b -> действие(PlagueNetwork.ACTION_NIGHT, 0, 0)).bounds(x, низ, ш, в).build());
        addRenderableWidget(Button.builder(Component.literal("+5 ночей"),
            b -> действие(PlagueNetwork.ACTION_FASTFORWARD, 5, 0)).bounds(x + (ш + зазор), низ, ш, в).build());
        addRenderableWidget(Button.builder(Component.literal("+30 ночей"),
            b -> действие(PlagueNetwork.ACTION_FASTFORWARD, 30, 0)).bounds(x + (ш + зазор) * 2, низ, ш, в).build());
        addRenderableWidget(Button.builder(
            Component.literal(данные.paused() ? "Продолжить" : "Пауза"),
            b -> действие(данные.paused() ? PlagueNetwork.ACTION_RESUME : PlagueNetwork.ACTION_PAUSE, 0, 0))
            .bounds(x + (ш + зазор) * 3, низ, ш, в).build());

        // ── второй ряд: подготовка расклада ────────────────────────────
        int низ2 = низ + в + зазор;
        int x2 = (width - (ш * 3 + зазор * 2)) / 2;
        addRenderableWidget(Button.builder(Component.literal("Засеять 10%"),
            b -> действие(PlagueNetwork.ACTION_GENERATE, 10, 0)).bounds(x2, низ2, ш, в).build());
        addRenderableWidget(Button.builder(Component.literal("Обновить"),
            b -> действие(PlagueNetwork.ACTION_REFRESH, 0, 0)).bounds(x2 + (ш + зазор), низ2, ш, в).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),
            b -> onClose()).bounds(x2 + (ш + зазор) * 2, низ2, ш, в).build());
    }

    /**
     * Клетка не крупнее 8 пикселей и не мельче одного. Три пикселя —
     * желаемый минимум, чтобы по клетке можно было попасть мышью, но на
     * большом квадрате его приходится отдать: лучше мелкая карта целиком,
     * чем кусок карты в хороших клетках.
     */
    private static int размерКлетки(int доступно, int ячеек) {
        int по_месту = доступно / Math.max(1, ячеек);
        return по_месту >= 3 ? Math.min(8, по_месту) : Math.max(1, по_месту);
    }

    /**
     * Сжать уровни до клеток карты: в клетке остаётся наибольший уровень
     * своего квадратика. Считается один раз на снимок, а не каждый кадр —
     * на сетке в полтысячи чанков это четверть миллиона ячеек.
     */
    private void прорядить() {
        int сторона = данные.size();
        byte[] уровни = данные.levels();
        картинка = new byte[ячеек * ячеек];
        for (int dz = 0; dz < сторона; dz++) {
            int строка = (dz / шаг) * ячеек;
            for (int dx = 0; dx < сторона; dx++) {
                int к = строка + dx / шаг;
                byte у = уровни[dz * сторона + dx];
                if (у > картинка[к]) картинка[к] = у;
            }
        }
    }

    private void действие(int код, int a, int b) {
        PacketDistributor.sendToServer(new PlagueNetwork.Action(код, a, b));
    }

    @Override
    public void render(GuiGraphics g, int мышьX, int мышьY, float partial) {
        super.render(g, мышьX, мышьY, partial); // фон и кнопки

        нарисоватьШапку(g);
        нарисоватьКарту(g);
        нарисоватьЛегенду(g);
        нарисоватьПодсказку(g, мышьX, мышьY);
    }

    private void нарисоватьШапку(GuiGraphics g) {
        int заражено = данные.countInfected();
        int всего = данные.size() * данные.size();
        float доля = всего == 0 ? 0f : 100f * заражено / всего;

        String строка1 = String.format("Ночь %d  ·  фаза %d%s",
            данные.night(), данные.phase(), данные.paused() ? "  ·  ПАУЗА" : "");
        String строка2 = String.format("Заражено %.1f%%  (%d из %d чанков)  ·  очагов %d  ·  местность: %s",
            доля, заражено, всего, данные.epicenterCount(), данные.terrainReady() ? "да" : "нет");

        // Третья строка — где сейчас стоит квадрат. Без неё /plague center
        // выглядел бы так, будто ничего не сделал: карта всегда рисуется
        // от своего угла, и по одной картинке переезд не виден.
        int центрX = данные.originX() + данные.size() / 2;
        int центрZ = данные.originZ() + данные.size() / 2;
        String строка3 = String.format(
            "Центр: чанк %d, %d (блок %d, %d)  ·  сетка %d×%d, чанки %d,%d..%d,%d",
            центрX, центрZ, центрX * 16 + 8, центрZ * 16 + 8,
            данные.size(), данные.size(),
            данные.originX(), данные.originZ(),
            данные.originX() + данные.size() - 1, данные.originZ() + данные.size() - 1);

        g.drawCenteredString(font, "Ядро чумы", width / 2, 10, ТЕКСТ);
        g.drawCenteredString(font, строка1, width / 2, 22, данные.paused() ? 0xFFFFC050 : ТЕКСТ);
        g.drawCenteredString(font, строка2, width / 2, 32, ТУСКЛЫЙ);
        g.drawCenteredString(font, строка3, width / 2, 42, ТУСКЛЫЙ);
        g.drawCenteredString(font, "Shift+ЛКМ по карте — посадить очаг, ПКМ — убрать",
            width / 2, картаY + картаПиксели + 4, ТУСКЛЫЙ);
    }

    private void нарисоватьКарту(GuiGraphics g) {
        g.fill(картаX - 1, картаY - 1, картаX + картаПиксели + 1, картаY + картаПиксели + 1, РАМКА);
        g.fill(картаX, картаY, картаX + картаПиксели, картаY + картаПиксели, ФОН_КАРТЫ);

        for (int dz = 0; dz < ячеек; dz++) {
            for (int dx = 0; dx < ячеек; dx++) {
                int уровень = картинка[dz * ячеек + dx];
                if (уровень <= 0) continue;
                int цвет = ЦВЕТА[Math.min(уровень, ЦВЕТА.length - 1)];
                int x = картаX + dx * клетка;
                int y = картаY + dz * клетка;
                g.fill(x, y, x + клетка, y + клетка, цвет);
            }
        }

        // Оси через центральный чанк сетки — тот, что объявлен центром мира
        // командой /plague center. Он же точка возрождения, и на карте он
        // всегда ровно посередине; куда он уехал, написано в шапке.
        int осьX = картаX + (ячеек / 2) * клетка;
        int осьZ = картаY + (ячеек / 2) * клетка;
        g.fill(осьX, картаY, осьX + 1, картаY + картаПиксели, ОСЬ);
        g.fill(картаX, осьZ, картаX + картаПиксели, осьZ + 1, ОСЬ);
    }

    private void нарисоватьЛегенду(GuiGraphics g) {
        int y = картаY + картаПиксели + 68; // под двумя рядами кнопок
        if (y > height - 10) return; // не влезает — не рисуем

        String[] подписи = { "чисто", "1", "2", "3", "4", "логово" };
        int ширина = подписи.length * 46;
        int x = (width - ширина) / 2;
        for (int i = 0; i < подписи.length; i++) {
            int сx = x + i * 46;
            g.fill(сx, y, сx + 8, y + 8, ЦВЕТА[i]);
            g.drawString(font, подписи[i], сx + 11, y, ТУСКЛЫЙ);
        }
    }

    private void нарисоватьПодсказку(GuiGraphics g, int мышьX, int мышьY) {
        int[] чанк = чанкПод(мышьX, мышьY);
        if (чанк == null) return;

        int уровень = данные.levelAt(чанк[0], чанк[1]);
        g.renderTooltip(font, Component.literal(String.format(
            "чанк %d, %d  ·  уровень %d  ·  блоки %d..%d, %d..%d%s",
            чанк[0], чанк[1], уровень,
            чанк[0] * 16, (чанк[0] + шаг) * 16 - 1,
            чанк[1] * 16, (чанк[1] + шаг) * 16 - 1,
            шаг > 1 ? "  ·  клетка = " + шаг + "×" + шаг + " чанков" : "")),
            мышьX, мышьY);
    }

    /** Координаты чанка под курсором или null, если курсор вне карты. */
    private int[] чанкПод(double мышьX, double мышьY) {
        if (мышьX < картаX || мышьX >= картаX + картаПиксели
            || мышьY < картаY || мышьY >= картаY + картаПиксели) {
            return null;
        }
        int dx = (int) ((мышьX - картаX) / клетка);
        int dz = (int) ((мышьY - картаY) / клетка);
        return new int[] { данные.originX() + dx * шаг, данные.originZ() + dz * шаг };
    }

    @Override
    public boolean mouseClicked(double мышьX, double мышьY, int кнопка) {
        int[] чанк = чанкПод(мышьX, мышьY);
        if (чанк != null) {
            // Посадка очага требует Shift намеренно: карта занимает пол-экрана,
            // и случайный клик не должен менять мир. Правая кнопка убирает очаг.
            if (hasShiftDown()) {
                действие(PlagueNetwork.ACTION_SEED, чанк[0], чанк[1]);
            } else if (кнопка == 1) {
                действие(PlagueNetwork.ACTION_REMOVE, чанк[0], чанк[1]);
            }
            return true;
        }
        return super.mouseClicked(мышьX, мышьY, кнопка);
    }
}
