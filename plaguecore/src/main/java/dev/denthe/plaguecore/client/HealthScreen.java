package dev.denthe.plaguecore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import dev.denthe.plaguecore.core.Wellbeing;

import java.util.EnumMap;
import java.util.List;
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

    private static final ResourceLocation ЗНАЧКИ =
        ResourceLocation.fromNamespaceAndPath("plaguecore", "textures/gui/health_icons.png");

    /** Лист значков: 64 × 16, ячейки вкладок 12 × 12 с шагом 12. */
    private static final int ЛИСТ_Ш = 64, ЛИСТ_В = 16, ЯЧЕЙКА = 12;

    /** Капля жажды в листе. */
    private static final int КАПЛЯ_X = 48, КАПЛЯ_Ш = 7, КАПЛЯ_В = 9;

    private static final ResourceLocation СЕРДЦЕ_ПУСТО =
        ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation СЕРДЦЕ_ПОЛНОЕ =
        ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation СЕРДЦЕ_ПОЛОВИНА =
        ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation ЕДА =
        ResourceLocation.withDefaultNamespace("hud/food_full");

    public enum Вкладка { STATE, BODY, FEEL, MEMORY }

    /**
     * Подписи вкладок. Готовые ключи заведены в языковых файлах и
     * покрыты {@code LangCoverageTest} с самого начала — до этой правки
     * нигде не читались. Константы, а не аллокация на кадр.
     */
    private static final Component ПОДПИСЬ_STATE = Component.translatable("plaguecore.health.tab.state");
    private static final Component ПОДПИСЬ_BODY = Component.translatable("plaguecore.health.tab.body");
    private static final Component ПОДПИСЬ_FEEL = Component.translatable("plaguecore.health.tab.feel");
    private static final Component ПОДПИСЬ_MEMORY = Component.translatable("plaguecore.health.tab.memory");

    private static Component подпись(Вкладка в) {
        return switch (в) {
            case STATE -> ПОДПИСЬ_STATE;
            case BODY -> ПОДПИСЬ_BODY;
            case FEEL -> ПОДПИСЬ_FEEL;
            case MEMORY -> ПОДПИСЬ_MEMORY;
        };
    }

    /** Готовые тексты правой панели, не зависящие от входов — не пересобираются на кадре. */
    private static final Component ПОДСКАЗКА_ВЫБОР = Component.translatable("plaguecore.health.hint.pick");
    private static final Component ОЩУЩЕНИЙ_НЕТ = Component.translatable("plaguecore.health.feel.none");
    private static final Component ПАМЯТЬ_ПУСТА = Component.translatable("plaguecore.health.memory.none");

    protected Вкладка вкладка = Вкладка.STATE;

    protected int левый, верхний;

    protected HealthScreen() {
        super(Component.translatable("plaguecore.health.title"));
    }

    /** Осматриваем соседа, а не себя. */
    protected boolean чужой;

    /** Кого осматриваем и какое о нём впечатление. */
    protected Player ктоЧужой;
    protected int чужаяСтупень;

    /**
     * Тексты соседа, собранные один раз в конструкторе, а не в render:
     * ступень чужого не меняется, пока экран открыт, так что пересобирать
     * компонент и строку ключа каждый кадр незачем — раздел 14 спека.
     */
    private Component чужоеОбщееКэш;
    private Map<Wellbeing.Часть, Component> чужаяЧастьКэш;

    protected HealthScreen(Player кто, int ступень) {
        this();
        this.чужой = true;
        this.ктоЧужой = кто;
        this.чужаяСтупень = ступень;
        this.чужоеОбщееКэш = Component.translatable(Wellbeing.чужоеОбщее(ступень));
        this.чужаяЧастьКэш = new EnumMap<>(Wellbeing.Часть.class);
        for (Wellbeing.Часть часть : ЧАСТИ) {
            чужаяЧастьКэш.put(часть, Component.translatable(Wellbeing.чужаяЧасть(часть, ступень)));
        }
    }

    /** Открыть осмотр самого себя. */
    public static void открытьСвой() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen());
    }

    /** Открыть осмотр соседа. */
    public static void открытьЧужой(Player кто, int ступень) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.35f, 0.8f);
        mc.setScreen(new HealthScreen(кто, ступень));
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

        подписьВысота = Math.max(1, Math.round(font.lineHeight * ПОДПИСЬ_МАСШТАБ));
        Вкладка[] вкладки = вкладки();
        вкладкаЛевый = new int[вкладки.length];
        вкладкаШирина = new int[вкладки.length];
        int x = левый + ШИРИНА - 8;
        for (int i = вкладки.length - 1; i >= 0; i--) {
            int подписьШирина = Math.round(font.width(подпись(вкладки[i])) * ПОДПИСЬ_МАСШТАБ);
            int ширина = Math.max(ЯЧЕЙКА, подписьШирина);
            x -= ширина;
            вкладкаЛевый[i] = x;
            вкладкаШирина[i] = ширина;
            x -= ЗАЗОР_ВКЛАДОК;
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

    /** Все вкладки. Считано один раз, а не на каждый кадр через values(). */
    private static final Вкладка[] ВКЛАДКИ = Вкладка.values();

    /**
     * Вкладки соседа: только «Самочувствие» и «Тело» — чужих ощущений
     * и чужой памяти знать неоткуда. Готовый массив, не новый на кадр.
     */
    private static final Вкладка[] ВКЛАДКИ_ЧУЖОГО = { Вкладка.STATE, Вкладка.BODY };

    /** Какие вкладки доступны сейчас. Ссылка на готовый массив — без аллокаций в кадре. */
    protected Вкладка[] вкладки() {
        return чужой ? ВКЛАДКИ_ЧУЖОГО : ВКЛАДКИ;
    }

    /**
     * Прямоугольники всех частей тела, посчитанные один раз в {@link #init()}.
     * Геометрия зависит только от {@code левый}/{@code верхний}, которые
     * на кадр не меняются — пересчитывать её в {@code render} незачем.
     */
    private Map<Wellbeing.Часть, int[][]> геометрия;

    /**
     * Колонки вкладок: у каждой своя ширина, под её собственную подпись,
     * а не общий фиксированный шаг. Так шире вкладке достаётся больше
     * места, а не приходится обрезать слово или наезжать на соседей.
     * Считано один раз в {@link #init()} по живому шрифту, индексы —
     * как в {@link #вкладки()}.
     */
    private int[] вкладкаЛевый;
    private int[] вкладкаШирина;

    /** Масштаб текста подписи под значком: полный размер шрифта сюда не влезает. */
    private static final float ПОДПИСЬ_МАСШТАБ = 0.6f;

    /** Отступ подписи от нижнего края значка и зазор между колонками вкладок. */
    private static final int ПОДПИСЬ_ОТСТУП = 2, ЗАЗОР_ВКЛАДОК = 3;

    /** Высота строки подписи в пикселях экрана, посчитана в {@link #init()} по шрифту. */
    private int подписьВысота;

    /** Выбранная часть тела. {@code null} — ещё ничего не выбрано. */
    protected Wellbeing.Часть выбрана;

    /** Кого осматриваем: сам игрок или сосед из чужого осмотра. */
    protected LivingEntity цель() {
        return чужой ? ктоЧужой : Minecraft.getInstance().player;
    }

    /** Ступень, по которой подбирается текст: своя или чужая. */
    protected int ступеньДляТекста() {
        return чужой ? чужаяСтупень : HealthSense.ступень();
    }

    protected void нарисоватьМодель(GuiGraphics графика) {
        LivingEntity кто = цель();
        if (кто == null) return;

        // Модель дёргается при одержимости: окно фигуры рывком уезжает
        // в сторону, а прямоугольники частей тела (геометрия) — нет,
        // иначе попасть по телу мышью на четвёртой ступени станет
        // невозможно. Раздел 14 спека.
        int рывокX = 0, рывокY = 0;
        if (PossessionClient.ведут() && тряска.nextInt(5) == 0) {
            рывокX = тряска.nextInt(5) - 2;
            рывокY = тряска.nextInt(3) - 1;
        }

        InventoryScreen.renderEntityInInventoryFollowsAngle(
            графика,
            левый + МОДЕЛЬ_X + рывокX, верхний + МОДЕЛЬ_Y + рывокY,
            левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш + рывокX, верхний + МОДЕЛЬ_Y + МОДЕЛЬ_В + рывокY,
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

    /** Где начинается правая колонка и сколько ей ширины. */
    protected int правыйX() { return левый + МОДЕЛЬ_X + МОДЕЛЬ_Ш + 12; }
    protected int праваяШирина() { return левый + ШИРИНА - 10 - правыйX(); }

    private int вкладкаY() { return верхний + 6; }

    /** Нижняя граница ряда вкладок вместе с подписью — отсюда начинается правая панель. */
    private int вкладкиНиз() { return вкладкаY() + ЯЧЕЙКА + ПОДПИСЬ_ОТСТУП + подписьВысота; }

    private void нарисоватьВкладки(GuiGraphics графика, int мышьX, int мышьY) {
        Вкладка[] вкладки = вкладки();
        int y = вкладкаY();
        int низ = вкладкиНиз();
        // Подсветка наведения и клик обязаны смотреть на одну и ту же
        // зону — иначе кайма в пиксель подсвечена, но не нажимается.
        // Единственный источник границы — вкладкаПод().
        Вкладка подКурсором = вкладкаПод(мышьX, мышьY);
        for (int i = 0; i < вкладки.length; i++) {
            int колонкаX = вкладкаЛевый[i], колонкаШирина = вкладкаШирина[i];
            int x = колонкаX + (колонкаШирина - ЯЧЕЙКА) / 2;
            boolean своя = вкладка == вкладки[i];
            boolean под = подКурсором == вкладки[i];

            графика.fill(колонкаX - 1, y - 1, колонкаX + колонкаШирина + 1, низ + 1,
                своя ? 0xFF2A322A : (под ? 0xFF1E241E : 0xFF161C16));
            графика.blit(ЗНАЧКИ, x, y, ЯЧЕЙКА, ЯЧЕЙКА,
                i * ЯЧЕЙКА, 0, ЯЧЕЙКА, ЯЧЕЙКА, ЛИСТ_Ш, ЛИСТ_В);
            подписьВкладки(графика, подпись(вкладки[i]),
                колонкаX + колонкаШирина / 2, y + ЯЧЕЙКА + ПОДПИСЬ_ОТСТУП,
                своя ? ТЕКСТ : ТУСКЛЫЙ);
        }
    }

    /** Подпись вкладки уменьшенным шрифтом, центрированная под значком. Не аллокация — только матрица позы. */
    private void подписьВкладки(GuiGraphics графика, Component текст, int центрX, int y, int цвет) {
        int ширина = Math.round(font.width(текст) * ПОДПИСЬ_МАСШТАБ);
        float x = центрX - ширина / 2f;
        графика.pose().pushPose();
        графика.pose().translate(x, y, 0f);
        графика.pose().scale(ПОДПИСЬ_МАСШТАБ, ПОДПИСЬ_МАСШТАБ, 1f);
        графика.drawString(font, текст, 0, 0, цвет, false);
        графика.pose().popPose();
    }

    private Вкладка вкладкаПод(int мышьX, int мышьY) {
        Вкладка[] вкладки = вкладки();
        int y = вкладкаY();
        int низ = вкладкиНиз();
        for (int i = 0; i < вкладки.length; i++) {
            int колонкаX = вкладкаЛевый[i], колонкаШирина = вкладкаШирина[i];
            if (мышьX >= колонкаX && мышьX < колонкаX + колонкаШирина
                    && мышьY >= y && мышьY < низ) {
                return вкладки[i];
            }
        }
        return null;
    }

    /** Печатает текст с переносами по ширине колонки. Возвращает новый y. */
    protected int протянуть(GuiGraphics графика, Component текст, int y, int цвет) {
        List<FormattedCharSequence> строки = font.split(текст, праваяШирина());
        for (FormattedCharSequence строка : строки) {
            графика.drawString(font, строка, правыйX(), y, цвет, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    protected void правая(GuiGraphics графика) {
        // Начало ниже ряда вкладок с подписями — иначе первая строка
        // текста наедет на подписи под значками.
        int y = вкладкиНиз() + 4;
        switch (вкладка) {
            case STATE -> {
                Component строка = чужой ? чужоеОбщееКэш : HealthSense.общее();
                y = протянуть(графика, строка, y, ТЕКСТ) + 6;
                if (чужой && HealthSense.клирик()) {
                    y = протянуть(графика, клирикОбщее(чужаяСтупень), y, ТУСКЛЫЙ) + 4;
                }
                y = сердца(графика, y) + 4;
                if (!чужой) {
                    y = строкаСоЗначком(графика, HealthSense.голод(), y, true);
                    if (HealthSense.жажда() != null) {
                        строкаСоЗначком(графика, HealthSense.жажда(), y, false);
                    }
                }
            }
            case BODY -> {
                if (выбрана == null) {
                    протянуть(графика, ПОДСКАЗКА_ВЫБОР, y, ТУСКЛЫЙ);
                } else {
                    Component строка = чужой ? чужаяЧастьКэш.get(выбрана) : HealthSense.часть(выбрана);
                    y = протянуть(графика, строка, y, ТЕКСТ);
                    if (HealthSense.клирик()) {
                        протянуть(графика, клирикЧасть(выбрана), y + 4, ТУСКЛЫЙ);
                    }
                }
            }
            case FEEL -> {
                List<Component> что = HealthSense.ощущения();
                if (что.isEmpty()) {
                    протянуть(графика, ОЩУЩЕНИЙ_НЕТ, y, ТУСКЛЫЙ);
                } else {
                    for (Component строка : что) y = протянуть(графика, строка, y, ТЕКСТ) + 2;
                }
            }
            case MEMORY -> {
                List<Component> что = HealthMemory.записи();
                if (что.isEmpty()) {
                    протянуть(графика, ПАМЯТЬ_ПУСТА, y, ТУСКЛЫЙ);
                } else {
                    for (int i = что.size() - 1; i >= 0; i--) {
                        y = протянуть(графика, что.get(i), y, ТУСКЛЫЙ) + 1;
                        if (y > верхний + ВЫСОТА - 14) break;
                    }
                }
            }
        }
    }

    /**
     * Кэш второй строки Клирика к выбранной части тела — пересобирается
     * только когда меняется часть, ступень или режим осмотра, а не
     * каждый кадр (раздел 14 спека).
     */
    private Wellbeing.Часть клирикЧастьКэшЗа;
    private int клирикСтупеньКэшЗа = Integer.MIN_VALUE;
    private boolean клирикРежимКэшЗа;
    private Component клирикЧастьТекст;

    private Component клирикЧасть(Wellbeing.Часть часть) {
        int ступень = ступеньДляТекста();
        if (клирикЧастьТекст == null || клирикЧастьКэшЗа != часть
                || клирикСтупеньКэшЗа != ступень || клирикРежимКэшЗа != чужой) {
            клирикЧастьКэшЗа = часть;
            клирикСтупеньКэшЗа = ступень;
            клирикРежимКэшЗа = чужой;
            клирикЧастьТекст = Component.translatable(Wellbeing.клирикЧасть(часть, ступень, чужой));
        }
        return клирикЧастьТекст;
    }

    /** Кэш второй строки Клирика о соседе целиком — только для чужого осмотра. */
    private int клирикОбщееКэшЗа = Integer.MIN_VALUE;
    private Component клирикОбщееТекст;

    private Component клирикОбщее(int ступень) {
        if (клирикОбщееТекст == null || клирикОбщееКэшЗа != ступень) {
            клирикОбщееКэшЗа = ступень;
            клирикОбщееТекст = Component.translatable(Wellbeing.клирикОбщее(ступень));
        }
        return клирикОбщееТекст;
    }

    /** Кэш числа «здоровье / максимум» — пересобирается только когда числа меняются. */
    private int здоровьеКэш = Integer.MIN_VALUE, максимумКэш = Integer.MIN_VALUE;
    private Component числоHPКэш = Component.empty();

    /**
     * Сердца ванильными спрайтами плюс число. Максимум учитывается как
     * есть: постоянная потеря за смерти и временный штраф стадии оба
     * уже сидят в getMaxHealth, считать отдельно нечего.
     *
     * Число берётся не из {@link HealthSense} — оно пересчитывается раз
     * в десять тиков и отстанет от живых сердец на полсекунды. Строка
     * своя, но собирается заново только когда здоровье или максимум
     * меняются, а не каждый кадр.
     *
     * При осмотре соседа число не печатается вовсе: максимум несёт
     * в себе временный штраф стадии, и точное «14 / 20» читалось бы
     * как стадия числом — против §9 спека и главного правила мода.
     * Сердца всё равно рисуются: их видно и на себе, штраф не выдают.
     */
    private int сердца(GuiGraphics графика, int y) {
        LivingEntity кто = цель();
        if (кто == null) return y;

        int здоровье = Mth.ceil(кто.getHealth());
        int максимум = Mth.ceil(кто.getMaxHealth());
        int всего = Math.max(1, Mth.ceil(максимум / 2f));

        if (!чужой && (здоровье != здоровьеКэш || максимум != максимумКэш)) {
            здоровьеКэш = здоровье;
            максимумКэш = максимум;
            числоHPКэш = Component.translatable("plaguecore.health.hp", здоровье, максимум);
        }

        int вРяду = 10;
        int x = правыйX();
        for (int i = 0; i < всего; i++) {
            int сx = x + (i % вРяду) * 8;
            int сy = y + (i / вРяду) * 10;
            графика.blitSprite(СЕРДЦЕ_ПУСТО, сx, сy, 9, 9);
            int вЭтом = здоровье - i * 2;
            if (вЭтом >= 2) графика.blitSprite(СЕРДЦЕ_ПОЛНОЕ, сx, сy, 9, 9);
            else if (вЭтом == 1) графика.blitSprite(СЕРДЦЕ_ПОЛОВИНА, сx, сy, 9, 9);
        }
        int рядов = (всего + вРяду - 1) / вРяду;
        int числоY = y + (рядов - 1) * 10;
        if (!чужой) {
            графика.drawString(font, числоHPКэш,
                x + Math.min(всего, вРяду) * 8 + 6, числоY + 1, ТУСКЛЫЙ, false);
        }
        return y + рядов * 10;
    }

    /** Строка «значок + фраза». Значок голода — ванильный, капля — своя. */
    private int строкаСоЗначком(GuiGraphics графика, Component текст, int y, boolean еда) {
        if (еда) {
            графика.blitSprite(ЕДА, правыйX(), y, 9, 9);
        } else {
            графика.blit(ЗНАЧКИ, правыйX() + 1, y, КАПЛЯ_Ш, КАПЛЯ_В,
                КАПЛЯ_X, 0, КАПЛЯ_Ш, КАПЛЯ_В, ЛИСТ_Ш, ЛИСТ_В);
        }
        List<FormattedCharSequence> строки = font.split(текст, праваяШирина() - 14);
        int сy = y;
        for (FormattedCharSequence строка : строки) {
            графика.drawString(font, строка, правыйX() + 14, сy, ТЕКСТ, false);
            сy += font.lineHeight + 1;
        }
        return Math.max(сy, y + 11);
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
        // Сосед вышел из мира, пока экран был открыт: держать его
        // замороженную модель и сердца — врать про живого человека.
        // Закрываем, а не рисуем труп чужих данных.
        if (чужой && (ктоЧужой == null || ктоЧужой.isRemoved())) {
            onClose();
            return;
        }

        // Фон рисует сам Screen.render: он начинается с renderBackground,
        // а тот размывает мир за экраном. Звать renderBackground отдельно
        // нельзя — размытие ляжет второй раз, уже поверх нашей панели,
        // и намылит её вместе с текстом.
        super.render(графика, мышьX, мышьY, кадр);

        // Дрожь двигает позу GPU, а не координаты: фон (мир за экраном)
        // уже нарисован строкой выше и не трясётся — трясётся только то,
        // что рисуем дальше: панель, модель, текст. Раздел 14 спека.
        посчитатьДрожь();
        графика.pose().pushPose();
        графика.pose().translate(дрожьX, дрожьY, 0f);

        панель(графика);
        гниль(графика);
        графика.drawString(font, title, левый + 8, верхний + 8, ТЕКСТ, false);

        нарисоватьМодель(графика);

        Wellbeing.Часть под = частьПод(мышьX, мышьY);
        if (выбрана != null) уголки(графика, выбрана, ВЫБРАНО);
        if (под != null && под != выбрана) уголки(графика, под, НАВЕДЕНИЕ);

        нарисоватьВкладки(графика, мышьX, мышьY);
        правая(графика);

        вторжение(графика);
        графика.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double мышьX, double мышьY, int кнопка) {
        Вкладка нажата = вкладкаПод((int) мышьX, (int) мышьY);
        if (нажата != null) {
            вкладка = нажата;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.1f);
            }
            return true;
        }

        Wellbeing.Часть под = частьПод((int) мышьX, (int) мышьY);
        if (под != null) {
            выбрана = под;
            выбрана(под);
            return true;
        }
        return super.mouseClicked(мышьX, мышьY, кнопка);
    }

    /** Часть тела выбрана: переключаемся на вкладку «Тело» и тихо щёлкаем. */
    protected void выбрана(Wellbeing.Часть часть) {
        вкладка = Вкладка.BODY;
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

    // ── искажение при одержимости: раздел 14 спека ──────────────────────
    //
    // В render ничего не создаётся — ни Random, ни Component, ни списки.
    // Всё, что зависит только от ступени, считается один раз при её смене
    // и лежит готовым в поле; на кадре остаётся чтение кэша и отрисовка.

    /**
     * Кэш пятен гнили: {x, y, размер} на пятно, координаты — от левого
     * верхнего угла панели (панель не меняет размер при ресайзе окна,
     * меняется только её позиция, так что кэш переживает init() и
     * инвалидируется только сменой ступени).
     */
    private int гнильКэшСтупень = Integer.MIN_VALUE;
    private int[][] гнильПятна;
    private int гнильТон;

    private void посчитатьГниль(int ступень) {
        if (ступень == гнильКэшСтупень) return;
        гнильКэшСтупень = ступень;
        if (ступень < 2) {
            гнильПятна = null;
            return;
        }

        java.util.Random зерно = new java.util.Random(0x9A17L + ступень);
        int пятен = ступень * 14;
        гнильТон = new int[] {0, 0, 0xFF1A1F16, 0xFF20220F, 0xFF241C10}[ступень];
        гнильПятна = new int[пятен][3];

        for (int i = 0; i < пятен; i++) {
            boolean поГоризонтали = зерно.nextBoolean();
            int размер = 1 + зерно.nextInt(ступень);
            int x, y;
            if (поГоризонтали) {
                x = зерно.nextInt(ШИРИНА - размер);
                y = зерно.nextBoolean() ? зерно.nextInt(6)
                                        : ВЫСОТА - 6 + зерно.nextInt(6);
            } else {
                x = зерно.nextBoolean() ? зерно.nextInt(6)
                                        : ШИРИНА - 6 + зерно.nextInt(6);
                y = зерно.nextInt(ВЫСОТА - размер);
            }
            гнильПятна[i] = new int[] {x, y, размер};
        }
    }

    /**
     * Гниль по краям панели. Растёт со ступенью, пятна не должны плясать
     * каждый кадр — зерно постоянное, картинка от этого не меняется.
     */
    protected void гниль(GuiGraphics графика) {
        посчитатьГниль(ступеньДляТекста());
        if (гнильПятна == null) return;
        for (int[] пятно : гнильПятна) {
            int x = левый + пятно[0], y = верхний + пятно[1], размер = пятно[2];
            графика.fill(x, y, x + размер, y + размер, гнильТон);
        }
    }

    /** Насколько панель уезжает от рывка, пикселей. */
    private static final int РЫВОК = 3;

    /** По вертикали трясёт слабее: иначе текст становится нечитаемым. */
    private static final int РЫВОК_ВЕРТИКАЛЬ = 2;

    /** Общий источник случайности для тряски, рывка модели и вторжений. */
    private final java.util.Random тряска = new java.util.Random();

    /** Сдвиг всего экрана в этом кадре. Ноль — телом никто не правит. */
    protected int дрожьX, дрожьY;

    private void посчитатьДрожь() {
        if (!PossessionClient.ведут()) {
            дрожьX = 0;
            дрожьY = 0;
            return;
        }
        // Рывками, а не ровным синусом: ровное дрожание читается как
        // анимация, а рывок — как то, что кто-то трогает тебя изнутри.
        if (тряска.nextInt(6) == 0) {
            дрожьX = тряска.nextInt(РЫВОК * 2 + 1) - РЫВОК;
            дрожьY = тряска.nextInt(РЫВОК_ВЕРТИКАЛЬ * 2 + 1) - РЫВОК_ВЕРТИКАЛЬ;
        }
    }

    /** Сколько разных чужих фраз заведено в языковом файле. */
    private static final int ВТОРЖЕНИЙ = 6;

    /** Цвет чужой речи: не цвет интерфейса, и это намеренно. */
    private static final int ЧУЖОЕ = 0xFFB03030;

    private int чужаяФраза = -1;
    private int чужаяДо;
    private int чужаяX, чужаяY;

    /**
     * Кэш компонента текущей чужой фразы — пересобирается только когда
     * меняется сама фраза, а не каждый кадр, пока она держится на экране.
     */
    private int чужаяФразаКэш = -1;
    private Component чужаяСтрокаКэш;

    /**
     * Чужая речь поверх интерфейса. Появляется рывком, не там, где
     * обычный текст, и держится недолго: это вторжение, а не подпись.
     */
    protected void вторжение(GuiGraphics графика) {
        if (!PossessionClient.ведут()) {
            чужаяФраза = -1;
            return;
        }

        int такт = (int) (Minecraft.getInstance().level == null
            ? 0 : Minecraft.getInstance().level.getGameTime());

        if (такт > чужаяДо) {
            if (тряска.nextInt(40) == 0) {
                чужаяФраза = тряска.nextInt(ВТОРЖЕНИЙ);
                чужаяДо = такт + 25 + тряска.nextInt(20);
                чужаяX = левый + 10 + тряска.nextInt(Math.max(1, ШИРИНА - 140));
                чужаяY = верхний + 30 + тряска.nextInt(Math.max(1, ВЫСОТА - 70));
            } else {
                чужаяФраза = -1;
            }
        }

        if (чужаяФраза >= 0) {
            if (чужаяФраза != чужаяФразаКэш) {
                чужаяФразаКэш = чужаяФраза;
                чужаяСтрокаКэш = Component.translatable(
                    "plaguecore.health.intrusion." + чужаяФраза);
            }
            графика.fill(чужаяX - 2, чужаяY - 2,
                чужаяX + font.width(чужаяСтрокаКэш) + 2, чужаяY + font.lineHeight + 1, 0xC0000000);
            графика.drawString(font, чужаяСтрокаКэш, чужаяX, чужаяY, ЧУЖОЕ, false);
        }
    }
}
