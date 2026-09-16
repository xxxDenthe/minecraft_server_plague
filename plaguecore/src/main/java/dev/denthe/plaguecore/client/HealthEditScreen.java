package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.core.Marks;
import dev.denthe.plaguecore.core.Wellbeing;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран правки состояния: тот же планшет, что видит игрок, но с
 * крестиками, карандашами и кнопкой «Вернуть как было».
 * Спек «Пометки Мастера игры», раздел 10.
 *
 * Наследуется от {@link HealthScreen} не ради экономии строк, а чтобы
 * ГМ видел ровно то же, что игрок: геометрия планшета, фигура, части
 * тела и вкладки уже выверены там, и второй их экземпляр разъехался бы
 * с оригиналом на первой же правке.
 *
 * Открывается только у ГМ и только по его команде. Игрок не получает
 * ни звука, ни сообщения: он узнает о пометке, открыв свой экран.
 *
 * Сам экран ничего не решает — печатает те же команды `/plague health`,
 * что ГМ мог бы набрать руками, а право на них проверяет сервер.
 */
public class HealthEditScreen extends HealthScreen {

    /** Кого правим. Держим номер сущности, а не список: список живёт в клиентском хранилище. */
    private final int сущность;
    private final String ник;

    /** Полоска кнопок у строки: крестик и карандаш. */
    private static final int ЗНАЧОК = 9;

    /** Ширина колонки под кнопки, отрезанная от правой врезки. */
    private static final int ПОЛЕ_КНОПОК = 22;

    private static final int КНОПКА = 0xFF2A2318, КНОПКА_КРАЙ = 0xFF6E5A33;
    private static final int КРЕСТИК = 0xFFC86A5E, КАРАНДАШ = 0xFFD8A24A;

    /** Действия кнопок у строки. */
    private static final int СНЯТЬ = 0, ПРАВИТЬ = 1;

    /** Кнопки этого кадра: {x1, y1, x2, y2, id пометки, действие}. */
    private final List<int[]> кнопкиСтрок = new ArrayList<>();

    private int[] кнопкаДобавить = new int[4];
    private int[] кнопкаСброс = new int[4];

    /** «Вернуть как было» просит второй клик — как опасные кнопки в панели ГМ. */
    private long взведено;

    private HealthEditScreen(Player цель, int ступень) {
        super(цель, ступень);
        this.сущность = цель.getId();
        this.ник = цель.getGameProfile().getName();
    }

    /**
     * Пришёл список для редактора. Экран либо открывается, либо просто
     * обновляется: сами пометки лежат в {@link HealthMarksClient}, и
     * открытый экран читает их оттуда каждый кадр.
     */
    public static void принять(PlagueNetwork.MarkList пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Экран уже открыт — или поверх него открыт выбор заготовки.
        // Во втором случае лезть со своим setScreen нельзя: ГМ как раз
        // печатает строку, и она пропадёт.
        if (mc.screen instanceof HealthEditScreen || mc.screen instanceof MarkPickerScreen) return;

        if (mc.level.getEntity(пакет.сущность()) instanceof Player кто) {
            mc.setScreen(new HealthEditScreen(кто, пакет.ступень()));
        }
    }

    /** Кого правим — того и читаем. Сам ГМ тоже может оказаться целью. */
    @Override
    protected List<Marks.Пометка> пометки() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == сущность
            ? HealthMarksClient.свои()
            : HealthMarksClient.чужие(сущность);
    }

    /** ГМ смотрит на экран глазами игрока: текст от первого лица. */
    @Override
    protected boolean соСтороны() { return false; }

    /** И видит всё, что увидит любой осматривающий, включая подробности Клирика. */
    @Override
    protected boolean клирикСтрока() { return true; }

    /** Правая врезка уже обычной: с краю живут крестики и карандаши. */
    @Override
    protected int праваяШирина() { return super.праваяШирина() - ПОЛЕ_КНОПОК; }

    /** В редакторе открыты все четыре раздела: ГМ правит любой. */
    @Override
    protected Вкладка[] вкладки() { return Вкладка.values(); }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        кнопкиСтрок.clear();
        super.render(графика, мышьX, мышьY, кадр);

        // Имя игрока под заголовком: планшет чужой, и забывать об этом
        // не должно быть можно ни на секунду.
        графика.drawString(font, Component.literal(ник), левый + 9, верхний + 21, ТУСКЛЫЙ, false);

        подвалРедактора(графика, мышьX, мышьY);
    }

    /** Крестик и карандаш у строки пометки. У автотекста снимать нечего. */
    @Override
    protected void приСтроке(GuiGraphics графика, int номер, int сверху, int снизу) {
        int id = идСтроки(номер);
        if (id < 0) return;

        int x = правыйX() + праваяШирина() + 3;
        значок(графика, x, сверху, КАРАНДАШ, false);
        значок(графика, x + ЗНАЧОК + 2, сверху, КРЕСТИК, true);

        кнопкиСтрок.add(new int[] { x, сверху, x + ЗНАЧОК, сверху + ЗНАЧОК, id, ПРАВИТЬ });
        кнопкиСтрок.add(new int[] { x + ЗНАЧОК + 2, сверху, x + ЗНАЧОК * 2 + 2, сверху + ЗНАЧОК,
                                    id, СНЯТЬ });
    }

    /** Маленькая кнопка: тёмное поле, латунная кайма, знак внутри. */
    private void значок(GuiGraphics графика, int x, int y, int цвет, boolean крест) {
        графика.fill(x, y, x + ЗНАЧОК, y + ЗНАЧОК, КНОПКА);
        рамка(графика, x, y, x + ЗНАЧОК, y + ЗНАЧОК, КНОПКА_КРАЙ);
        if (крест) {
            for (int i = 2; i < ЗНАЧОК - 2; i++) {
                графика.fill(x + i, y + i, x + i + 1, y + i + 1, цвет);
                графика.fill(x + ЗНАЧОК - 1 - i, y + i, x + ЗНАЧОК - i, y + i + 1, цвет);
            }
        } else {
            // Карандаш: наклонная черта с утолщением у острия.
            for (int i = 2; i < ЗНАЧОК - 2; i++) {
                графика.fill(x + i, y + ЗНАЧОК - 1 - i, x + i + 1, y + ЗНАЧОК - i, цвет);
            }
            графика.fill(x + 2, y + ЗНАЧОК - 3, x + 4, y + ЗНАЧОК - 2, цвет);
        }
    }

    private void рамка(GuiGraphics г, int x1, int y1, int x2, int y2, int цвет) {
        г.fill(x1, y1, x2, y1 + 1, цвет);
        г.fill(x1, y2 - 1, x2, y2, цвет);
        г.fill(x1, y1 + 1, x1 + 1, y2 - 1, цвет);
        г.fill(x2 - 1, y1 + 1, x2, y2 - 1, цвет);
    }

    /** Две кнопки внизу врезки: добавить и вернуть как было. */
    private void подвалРедактора(GuiGraphics графика, int мышьX, int мышьY) {
        int y = верхний + ВЫСОТА - 18;
        int x = правыйX();
        int ширинаДобавить = 58;

        кнопкаДобавить = new int[] { x, y, x + ширинаДобавить, y + 12 };
        кнопка(графика, кнопкаДобавить, ДОБАВИТЬ, мышьX, мышьY, false);

        int сбросX = x + ширинаДобавить + 4;
        кнопкаСброс = new int[] { сбросX, y, правыйX() + super.праваяШирина(), y + 12 };
        boolean ждёт = взведено > 0 && Util.getMillis() - взведено < 3000L;
        кнопка(графика, кнопкаСброс, ждёт ? СБРОС_ТОЧНО : СБРОС, мышьX, мышьY, ждёт);
    }

    private static final Component ДОБАВИТЬ = Component.translatable("plaguecore.health.edit.add");
    private static final Component СБРОС = Component.translatable("plaguecore.health.edit.reset");
    private static final Component СБРОС_ТОЧНО =
        Component.translatable("plaguecore.health.edit.reset.confirm");

    private void кнопка(GuiGraphics графика, int[] п, Component подпись,
                        int мышьX, int мышьY, boolean тревога) {
        boolean под = в(п, мышьX, мышьY);
        графика.fill(п[0], п[1], п[2], п[3], под ? 0xFF3A2F1E : КНОПКА);
        рамка(графика, п[0], п[1], п[2], п[3], тревога ? КРЕСТИК : КНОПКА_КРАЙ);
        int ширина = font.width(подпись);
        графика.drawString(font, подпись,
            (п[0] + п[2]) / 2 - ширина / 2, п[1] + 2,
            тревога ? КРЕСТИК : ТЕКСТ, false);
    }

    private static boolean в(int[] п, double x, double y) {
        return x >= п[0] && x < п[2] && y >= п[1] && y < п[3];
    }

    @Override
    public boolean mouseClicked(double мышьX, double мышьY, int кнопка) {
        for (int[] п : кнопкиСтрок) {
            if (!в(п, мышьX, мышьY)) continue;
            щелчок();
            if (п[5] == СНЯТЬ) {
                команда("plague health remove " + ник + " " + п[4]);
            } else {
                Minecraft.getInstance().setScreen(
                    new MarkPickerScreen(this, ник, местоВкладки(), п[4]));
            }
            return true;
        }

        if (в(кнопкаДобавить, мышьX, мышьY)) {
            щелчок();
            Minecraft.getInstance().setScreen(
                new MarkPickerScreen(this, ник, местоВкладки(), -1));
            return true;
        }

        if (в(кнопкаСброс, мышьX, мышьY)) {
            щелчок();
            // Первый клик взводит, второй в течение трёх секунд снимает всё:
            // «вернуть как было» стирает работу целого вечера, и промах
            // мышью не должен этого делать.
            if (взведено > 0 && Util.getMillis() - взведено < 3000L) {
                взведено = 0;
                команда("plague health clear " + ник);
            } else {
                взведено = Util.getMillis();
            }
            return true;
        }

        return super.mouseClicked(мышьX, мышьY, кнопка);
    }

    /** Куда ляжет новая пометка: по открытой вкладке и выбранной части тела. */
    private Marks.Место местоВкладки() {
        return switch (вкладка) {
            case STATE -> Marks.Место.OVERALL;
            case BODY -> выбрана == null ? Marks.Место.HEAD : место(выбрана);
            case FEEL -> Marks.Место.FEEL;
            case MEMORY -> Marks.Место.MEMORY;
        };
    }

    /** Часть тела выбрана: у редактора это ещё и выбор места для новой пометки. */
    @Override
    protected void выбрана(Wellbeing.Часть часть) {
        super.выбрана(часть);
        взведено = 0;
    }

    private void щелчок() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.18f, 1.2f);
        }
    }

    void команда(String строка) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(строка);
    }
}
