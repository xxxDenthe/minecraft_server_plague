package dev.denthe.plaguecore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import dev.denthe.plaguecore.core.Wellbeing;

import java.util.EnumMap;
import java.util.Map;

/**
 * Экран «Состояние здоровья»: человек осматривает сам себя.
 * Спек интерфейса, раздел 7.
 *
 * Экран ничего не решает и ничего не спрашивает у сервера — он рисует
 * то, что собрал {@link HealthSense}. Ни одного числа болезни наружу
 * не выходит: ни заражения, ни стадии, ни порогов.
 */
public class HealthScreen extends Screen {

    /** Размер панели. Сундук — 176 × 166; здесь шире, текст длинный. */
    protected static final int ШИРИНА = 248, ВЫСОТА = 166;

    protected static final int ФОН = 0xFF101410;
    protected static final int РАМКА = 0xFF4A4A4A;
    protected static final int ТЕКСТ = 0xFFE0E0E0;
    protected static final int ТУСКЛЫЙ = 0xFF909090;

    protected int левый, верхний;

    protected HealthScreen() {
        super(Component.translatable("plaguecore.health.title"));
    }

    /** Открыть осмотр самого себя. */
    public static void открытьСвой() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen());
    }

    /**
     * Игру не останавливаем. Сервер сессионный, пауза там всё равно
     * не работает, а стоять с открытым экраном посреди гнили должно
     * быть опасно.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        левый = (width - ШИРИНА) / 2;
        верхний = (height - ВЫСОТА) / 2;

        геометрия = new EnumMap<>(Wellbeing.Часть.class);
        for (Wellbeing.Часть часть : ЧАСТИ) {
            геометрия.put(часть, построить(часть));
        }
    }

    /** Окно модели внутри панели. */
    protected static final int МОДЕЛЬ_X = 12, МОДЕЛЬ_Y = 22;
    protected static final int МОДЕЛЬ_Ш = 64, МОДЕЛЬ_В = 132;

    /** Масштаб фигуры. Подбирался глазом: 44 — фигура занимает окно почти целиком. */
    protected static final int МАСШТАБ = 44;

    /** Сдвиг фигуры вверх, тот же, что у ванильного инвентаря. */
    protected static final float СДВИГ = 0.0625f;

    /**
     * Границы частей тела по высоте фигуры, в блоках от подошв.
     * Модель игрока — 32 пикселя, сжатых рендером на 0.9375, то есть
     * 1.875 блока: ноги 12 пикселей, туловище 12, голова 8.
     */
    protected static final float НОГИ_ВЕРХ = 0.703f;
    protected static final float ТУЛОВИЩЕ_ВЕРХ = 1.406f;
    protected static final float ГОЛОВА_ВЕРХ = 1.875f;

    /** Полуширина туловища и всей фигуры с руками, в блоках. */
    protected static final float ПОЛУШИРИНА_ТЕЛА = 0.234f;
    protected static final float ПОЛУШИРИНА_РУК = 0.469f;

    /** Все части тела. Считано один раз, а не на каждый кадр через values(). */
    private static final Wellbeing.Часть[] ЧАСТИ = Wellbeing.Часть.values();

    /**
     * Прямоугольники всех частей тела, посчитанные один раз в {@link #init()}.
     * Геометрия зависит только от {@code левый}/{@code верхний}, которые
     * на кадр не меняются — пересчитывать её в {@code render} незачем.
     */
    private Map<Wellbeing.Часть, int[][]> геометрия;

    /** Выбранная часть тела. {@code null} — ещё ничего не выбрано. */
    protected Wellbeing.Часть выбрана;

    /** Кого осматриваем. В своём экране — сам игрок; чужого даёт задача 12. */
    protected LivingEntity цель() {
        return Minecraft.getInstance().player;
    }

    protected void нарисоватьМодель(GuiGraphics графика) {
        LivingEntity кто = цель();
        if (кто == null) return;
        InventoryScreen.renderEntityInInventoryFollowsAngle(
            графика,
            левый + МОДЕЛЬ_X, верхний + МОДЕЛЬ_Y,
            левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш, верхний + МОДЕЛЬ_Y + МОДЕЛЬ_В,
            МАСШТАБ, СДВИГ, 0f, 0f, кто);
    }

    /** Середина окна модели по горизонтали. */
    private int центрX() {
        return левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш / 2;
    }

    /** Экранный X для точки фигуры, в блоках от середины. Ось перевёрнута рендером. */
    private int экранX(float блоки) {
        return центрX() - Math.round(МАСШТАБ * блоки);
    }

    /** Экранный Y для точки фигуры, в блоках от подошв. */
    private int экранY(float блоки) {
        LivingEntity кто = цель();
        float половинаРоста = кто == null ? 0.9f : кто.getBbHeight() / 2f;
        int центрY = верхний + МОДЕЛЬ_Y + МОДЕЛЬ_В / 2;
        return центрY + Math.round(МАСШТАБ * (половинаРоста + СДВИГ - блоки));
    }

    /**
     * Прямоугольники части тела на экране: {x1, y1, x2, y2}. Готовое
     * значение из {@link #геометрия}, посчитанное один раз в {@link #init()}.
     *
     * У рук и ног их по два — это и есть та самая пара, которую надо
     * подсвечивать целиком.
     */
    protected int[][] прямоугольники(Wellbeing.Часть часть) {
        return геометрия.get(часть);
    }

    /**
     * Строит прямоугольники части тела. Зовётся только из {@link #init()},
     * не из кадра. Числа прикидочные, подкручиваются глазом при первой
     * живой проверке.
     */
    private int[][] построить(Wellbeing.Часть часть) {
        return switch (часть) {
            case HEAD -> new int[][] {{
                экранX(ПОЛУШИРИНА_ТЕЛА), экранY(ГОЛОВА_ВЕРХ),
                экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ)}};
            case TORSO -> new int[][] {{
                экранX(ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ),
                экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ)}};
            case ARMS -> new int[][] {
                {экранX(ПОЛУШИРИНА_РУК), экранY(ТУЛОВИЩЕ_ВЕРХ),
                 экранX(ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ)},
                {экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(ТУЛОВИЩЕ_ВЕРХ),
                 экранX(-ПОЛУШИРИНА_РУК), экранY(НОГИ_ВЕРХ)}};
            case LEGS -> new int[][] {
                {экранX(ПОЛУШИРИНА_ТЕЛА), экранY(НОГИ_ВЕРХ),
                 экранX(0f), экранY(0f)},
                {экранX(0f), экранY(НОГИ_ВЕРХ),
                 экранX(-ПОЛУШИРИНА_ТЕЛА), экранY(0f)}};
        };
    }

    /** Какая часть тела под курсором. {@code null} — ни одна. */
    protected Wellbeing.Часть частьПод(int мышьX, int мышьY) {
        for (Wellbeing.Часть часть : ЧАСТИ) {
            for (int[] п : прямоугольники(часть)) {
                if (мышьX >= п[0] && мышьX < п[2] && мышьY >= п[1] && мышьY < п[3]) {
                    return часть;
                }
            }
        }
        return null;
    }

    /** Длина уголка, пикселей. Четыре — читается и не спорит с фигурой. */
    private static final int УГОЛОК = 4;

    /**
     * Уголки вокруг части тела, а не заливка: подсветить кусок 3D-модели
     * поверх скина нечем, а уголки читаются и картинку не портят.
     */
    protected void уголки(GuiGraphics графика, Wellbeing.Часть часть, int цвет) {
        for (int[] п : прямоугольники(часть)) {
            рамкаУголками(графика, п[0], п[1], п[2], п[3], цвет);
        }
    }

    private void рамкаУголками(GuiGraphics г, int x1, int y1, int x2, int y2, int цвет) {
        int д = Math.min(УГОЛОК, Math.max(1, Math.min(x2 - x1, y2 - y1) / 2));
        // верхний левый
        г.fill(x1, y1, x1 + д, y1 + 1, цвет);
        г.fill(x1, y1, x1 + 1, y1 + д, цвет);
        // верхний правый
        г.fill(x2 - д, y1, x2, y1 + 1, цвет);
        г.fill(x2 - 1, y1, x2, y1 + д, цвет);
        // нижний левый
        г.fill(x1, y2 - 1, x1 + д, y2, цвет);
        г.fill(x1, y2 - д, x1 + 1, y2, цвет);
        // нижний правый
        г.fill(x2 - д, y2 - 1, x2, y2, цвет);
        г.fill(x2 - 1, y2 - д, x2, y2, цвет);
    }

    /** Цвет уголков под курсором и у выбранной части. */
    private static final int НАВЕДЕНИЕ = 0xFF9A9A8A;
    private static final int ВЫБРАНО = 0xFFE0E0E0;

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        renderBackground(графика, мышьX, мышьY, кадр);
        панель(графика);
        графика.drawString(font, title, левый + 8, верхний + 8, ТЕКСТ, false);

        нарисоватьМодель(графика);

        Wellbeing.Часть под = частьПод(мышьX, мышьY);
        if (выбрана != null) уголки(графика, выбрана, ВЫБРАНО);
        if (под != null && под != выбрана) уголки(графика, под, НАВЕДЕНИЕ);

        super.render(графика, мышьX, мышьY, кадр);
    }

    @Override
    public boolean mouseClicked(double мышьX, double мышьY, int кнопка) {
        Wellbeing.Часть под = частьПод((int) мышьX, (int) мышьY);
        if (под != null) {
            выбрана = под;
            выбрана(под);
            return true;
        }
        return super.mouseClicked(мышьX, мышьY, кнопка);
    }

    /**
     * Часть тела выбрана. Здесь только тихий щелчок; вкладку переключает
     * задача 9, переопределяя этот метод.
     */
    protected void выбрана(Wellbeing.Часть часть) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.18f, 1.4f);
        }
    }

    /** Тёмная панель с тонкой рамкой. Палитра — от админского экрана карты. */
    protected void панель(GuiGraphics графика) {
        int x = левый, y = верхний;
        графика.fill(x - 1, y - 1, x + ШИРИНА + 1, y + ВЫСОТА + 1, РАМКА);
        графика.fill(x, y, x + ШИРИНА, y + ВЫСОТА, ФОН);
    }
}
