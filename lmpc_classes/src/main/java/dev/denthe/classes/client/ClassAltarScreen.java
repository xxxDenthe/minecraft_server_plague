package dev.denthe.classes.client;

import dev.denthe.classes.ClassLore;
import dev.denthe.classes.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Экран алтаря призвания. Спек — 2026-09-04-klassy-design.md, раздел 2.
 *
 * Без меню-контейнера: выбор шлёт команду {@code /lmpcclasses choose
 * <класс>} тем же приёмом, что панель `lmpc_gmtools` шлёт ванильные
 * команды. Сервер сам проверяет кулдаун и режет мастерство — экран
 * ничего не решает, только показывает и предупреждает.
 *
 * **Переписан в 0.6.0.** Раньше это была стопка из шести серых
 * ванильных кнопок посреди пустого экрана: одна страница книги
 * (гримуар) выглядела рукописью, вторая — окном настроек. Теперь
 * обе на одном пергаменте, той же палитрой ({@link ClassStyle}),
 * и алтарь наконец отвечает на три вопроса, которые до этого
 * приходилось выяснять методом тыка: какой у меня сейчас класс,
 * можно ли вообще сменить прямо сейчас и чем эти четверо
 * отличаются.
 *
 * Кулдаун виден заранее, а не вылезает ошибкой после клика — это
 * стало возможно только с синком вложения (0.6.0): до него клиент
 * не знал ни своего класса, ни тика последней смены.
 */
public class ClassAltarScreen extends Screen {

    private static final int ШИРИНА = 320;
    private static final int ВЫСОТА_СТРОКИ = 26;
    private static final int ОТСТУП = 10;
    private static final int ВЫСОТА_ШАПКИ = 44;

    /** Порядок строк: сперва четыре класса, «без класса» — последним, как отказ. */
    private static final PlayerClassData.Класс[] ПОРЯДОК = {
        PlayerClassData.Класс.CLERIC,
        PlayerClassData.Класс.SMITH,
        PlayerClassData.Класс.FARMER,
        PlayerClassData.Класс.CHRONICLER,
        PlayerClassData.Класс.NONE,
    };

    /** Подсказка под списком: почему клик не сработал. Живёт до следующего клика. */
    private Component замечание;

    public ClassAltarScreen() {
        super(Component.translatable("gui.lmpc_classes.altar.title"));
    }

    /** Открыть экран. Единственная точка входа из {@code ClassAltarBlock}. */
    public static void открыть() {
        Minecraft.getInstance().setScreen(new ClassAltarScreen());
    }

    private int левый() { return (width - ШИРИНА) / 2; }

    private int высотаПанели() {
        return ВЫСОТА_ШАПКИ + ПОРЯДОК.length * ВЫСОТА_СТРОКИ + 34;
    }

    private int верхний() { return (height - высотаПанели()) / 2; }

    private PlayerClassData.Класс свойКласс() {
        PlayerClassData д = ClassStyle.свои();
        return д == null ? PlayerClassData.Класс.NONE : д.класс;
    }

    @Override
    protected void init() {
        int x = левый() + ОТСТУП;
        int ширина = ШИРИНА - ОТСТУП * 2;
        int y = верхний() + ВЫСОТА_ШАПКИ;

        for (PlayerClassData.Класс класс : ПОРЯДОК) {
            addRenderableWidget(new СтрокаКласса(x, y, ширина, класс));
            y += ВЫСОТА_СТРОКИ;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.lmpc_classes.close"), b -> onClose())
            .bounds(левый() + ШИРИНА / 2 - 45, y + 8, 90, 18)
            .build());
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
        renderBackground(графика, мышьX, мышьY, partialTick);
        пергамент(графика);
        шапка(графика);
        super.render(графика, мышьX, мышьY, partialTick);

        if (замечание != null) {
            графика.drawCenteredString(font, замечание,
                левый() + ШИРИНА / 2, верхний() + высотаПанели() - 30, 0xC08A3A2A);
        }
    }

    /** Лист пергамента под всем экраном: тень, тёмная рамка, тёплая заливка. */
    private void пергамент(GuiGraphics графика) {
        int x = левый(), y = верхний(), в = высотаПанели();
        графика.fill(x + 3, y + 3, x + ШИРИНА + 3, y + в + 3, 0x60000000);
        графика.fill(x, y, x + ШИРИНА, y + в, 0xFF2A2118);
        графика.fill(x + 2, y + 2, x + ШИРИНА - 2, y + в - 2, 0xFFE8DCC0);
        графика.fill(x + 2, y + 2, x + ШИРИНА - 2, y + 3, 0xFFF6EEDC);
    }

    /** Заголовок, текущий класс и состояние кулдауна — три вопроса, ради которых сюда идут. */
    private void шапка(GuiGraphics графика) {
        int x = левый() + ОТСТУП;
        int y = верхний() + 8;
        PlayerClassData.Класс свой = свойКласс();
        int акцент = ClassStyle.цвет(свой);

        графика.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, акцент, false);

        PlayerClassData д = ClassStyle.свои();
        Component текущий = свой == PlayerClassData.Класс.NONE
            ? Component.translatable("gui.lmpc_classes.altar.current_none")
            : Component.translatable("gui.lmpc_classes.altar.current",
                ClassLore.заголовок(свой), ClassStyle.римская(д == null ? 1 : д.тир()));
        графика.drawString(font, текущий, x, y + 13, ClassStyle.ЧЕРНИЛА_БЛЕДНЫЕ, false);

        long минут = ClassStyle.минутДоСмены();
        Component статус = минут > 0
            ? Component.translatable("gui.lmpc_classes.altar.locked", минут)
            : Component.translatable("gui.lmpc_classes.altar.ready");
        графика.drawString(font, статус, x, y + 25, минут > 0 ? 0x8A3A2A : 0x4A6B2E, false);

        графика.fill(x, верхний() + ВЫСОТА_ШАПКИ - 5,
            левый() + ШИРИНА - ОТСТУП, верхний() + ВЫСОТА_ШАПКИ - 4, 0x40000000 | ClassStyle.ЧЕРНИЛА);
    }

    /**
     * Клик по строке. Экран не подменяет собой сервер — он только
     * отсекает два случая, где сервер всё равно откажет, чтобы
     * не гонять команду ради ошибки в чате.
     */
    private void выбрать(PlayerClassData.Класс класс) {
        if (класс == свойКласс()) {
            замечание = Component.translatable("gui.lmpc_classes.altar.same");
            return;
        }
        long минут = ClassStyle.минутДоСмены();
        if (минут > 0) {
            замечание = Component.translatable("gui.lmpc_classes.altar.locked", минут);
            return;
        }
        var связь = Minecraft.getInstance().getConnection();
        if (связь != null) связь.sendCommand("lmpcclasses choose " + класс.name().toLowerCase(Locale.ROOT));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Роль класса; у «без класса» своя строка, лора у него нет. */
    private static Component роль(PlayerClassData.Класс класс) {
        return класс == PlayerClassData.Класс.NONE
            ? Component.translatable("class.lmpc_classes.none.role")
            : ClassLore.роль(класс);
    }

    /**
     * Строка списка: цветная засечка класса, название, роль мелким
     * шрифтом. Ванильной серой кнопки нет — переопределён только
     * {@code renderWidget}, клики и наведение остались от {@link Button}.
     *
     * <p>В 0.6.1 по замечаниям с живого экрана: пометка «твой» больше
     * не наезжает на длинное название (место под неё резервируется,
     * и обрезается имя, а не соседняя надпись), а обрезанная роль
     * доступна целиком во всплывающей подсказке — вместе со списком
     * способностей, ради которого раньше приходилось лезть в гримуар.
     */
    private class СтрокаКласса extends Button {
        private final PlayerClassData.Класс класс;

        СтрокаКласса(int x, int y, int ширина, PlayerClassData.Класс класс) {
            super(x, y, ширина, ВЫСОТА_СТРОКИ - 2, ClassLore.заголовок(класс),
                b -> выбрать(класс), DEFAULT_NARRATION);
            this.класс = класс;
            setTooltip(Tooltip.create(подсказка(класс)));
        }

        /** Полная роль плюс «что умеет» — то, что не влезло в строку. */
        private Component подсказка(PlayerClassData.Класс класс) {
            MutableComponent текст = роль(класс).copy();
            for (Component способность : ClassLore.способности(класс)) {
                текст.append(Component.literal("\n· ")).append(способность);
            }
            return текст;
        }

        @Override
        protected void renderWidget(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
            boolean свой = класс == свойКласс();
            boolean заперто = ClassStyle.минутДоСмены() > 0;
            int акцент = ClassStyle.цвет(класс);

            if (isHovered()) графика.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22000000);
            if (свой) графика.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x18000000 | (акцент & 0xFFFFFF));

            // Засечка слева: единственное место, где цвет класса виден крупно.
            графика.fill(getX(), getY(), getX() + 3, getY() + getHeight(), ClassStyle.заливка(класс));

            int x = getX() + 9;
            int правый = getX() + getWidth() - 4;

            // Место под пометку отводится до отрисовки имени. Раньше обе
            // надписи считали ширину порознь и на длинных названиях
            // налезали друг на друга.
            Component пометка = Component.translatable("gui.lmpc_classes.altar.yours");
            int подПометку = свой ? font.width(пометка) + 6 : 0;

            int цветИмени = заперто && !свой ? ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ : акцент;
            Component имя = getMessage().copy().withStyle(свой ? ChatFormatting.BOLD : ChatFormatting.RESET);
            графика.drawString(font, обрезать(имя, правый - x - подПометку), x, getY() + 3, цветИмени, false);

            if (свой) {
                графика.drawString(font, пометка, правый - font.width(пометка), getY() + 3, акцент, false);
            }

            графика.drawString(font, обрезать(роль(класс), правый - x),
                x, getY() + 13, ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ, false);
        }

        /**
         * Длинный текст укорачиваем многоточием — перенос сломал бы
         * сетку списка. Целиком он есть в подсказке при наведении,
         * поэтому обрезка ничего не прячет насовсем.
         */
        private Component обрезать(Component текст, int ширина) {
            String строка = текст.getString();
            if (font.width(строка) <= ширина) return текст;
            return Component.literal(font.plainSubstrByWidth(строка, ширина - font.width("…")) + "…")
                .setStyle(текст.getStyle());
        }
    }
}
