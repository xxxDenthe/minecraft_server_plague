package dev.denthe.classes.client;

import dev.denthe.classes.ClassLore;
import dev.denthe.classes.ClassMastery;
import dev.denthe.classes.LmpcClasses;
import dev.denthe.classes.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Гримуар. Спек — 2026-09-04-klassy-design.md, раздел 2.1 (мастерство)
 * и 4–7 (роли, лор). Фон — разворот книги, картинка владельца
 * (736×490, соотношение сохраняется при масштабировании). Левая
 * страница — оглавление из четырёх классов, правая — заголовок,
 * роль, лор, способности и, у своего класса, мастерство.
 *
 * <p><b>Что изменилось в 0.6.1 — по замечаниям с живого экрана.</b>
 * <ul>
 * <li>Отступы больше не «на глаз». Границы бумаги внутри картинки
 *     ({@link #ЛИСТ_Л} и соседи) сняты замером самой текстуры, поля
 *     считаются из них. Старое {@code ОТСТУП_НИЗ = 16} уводило текст
 *     на пять пикселей под деревянную обложку: бумага кончается
 *     на 448-м текселе, а не на 469-м.</li>
 * <li>Разворот тянется под окно, а не заперт на 360 пикселях. На
 *     мелком масштабе интерфейса книга больше и колонка текста шире,
 *     на крупном кнопка «Закрыть» перестала свисать за нижний край
 *     экрана — она теперь внизу левой страницы, внутри книги.</li>
 * <li>Правая страница прокручивается колесом. Лор Клирика со всеми
 *     тремя способностями не влезал ни в какой разумный разворот,
 *     и старый код молча обрывал текст на полуслове ранним
 *     {@code return}. Теперь непоместившееся достаётся прокруткой,
 *     а на поле страницы видна закладка с положением.</li>
 * </ul>
 */
public class ClassCodexScreen extends Screen {
    private static final ResourceLocation ФОН_ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "textures/gui/codex_background.png");
    private static final int ТЕКСТУРА_Ш = 736;
    private static final int ТЕКСТУРА_В = 490;

    /**
     * Границы бумаги внутри картинки разворота, в текселях. Замерены
     * по самому файлу (край светлой заливки), а не подобраны на глаз:
     * корешок сидит не по центру (365 из 368), а снизу у книги толстая
     * тень, из-за которой симметричные отступы промахиваются.
     */
    private static final int ЛИСТ_Л = 26;
    private static final int ЛИСТ_П = 705;
    private static final int ЛИСТ_В = 21;
    private static final int ЛИСТ_Н = 448;
    private static final int КОРЕШОК_Л = 360;
    private static final int КОРЕШОК_П = 371;

    /** Поле от края бумаги до текста, в текселях (около шести экранных пикселей). */
    private static final int ПОЛЕ = 12;

    private static final int МИН_ШИРИНА = 300;
    private static final int МАКС_ШИРИНА = 460;

    /** Вкладки — только настоящие классы: «без класса» не глава книги. */
    private static final PlayerClassData.Класс[] ВКЛАДКИ = {
        PlayerClassData.Класс.CLERIC,
        PlayerClassData.Класс.SMITH,
        PlayerClassData.Класс.FARMER,
        PlayerClassData.Класс.CHRONICLER,
    };

    private PlayerClassData.Класс открытаяВкладка;

    /** Сдвиг правой страницы вверх, пиксели. Предел пересчитывается при отрисовке. */
    private int прокрутка;
    private int пределПрокрутки;

    private ClassCodexScreen() {
        super(Component.translatable("gui.lmpc_classes.codex.title"));
        PlayerClassData.Класс свой = свойКласс();
        this.открытаяВкладка = свой == PlayerClassData.Класс.NONE ? PlayerClassData.Класс.CLERIC : свой;
    }

    public static void открыть() {
        Minecraft.getInstance().setScreen(new ClassCodexScreen());
    }

    /** Читается каждый раз заново: класс могли сменить, пока книга открыта. */
    private static PlayerClassData.Класс свойКласс() {
        PlayerClassData д = ClassStyle.свои();
        return д == null ? PlayerClassData.Класс.NONE : д.класс;
    }

    /**
     * Разворот занимает столько, сколько даёт окно, но не больше
     * {@link #МАКС_ШИРИНА}: на крупном масштабе интерфейса книга
     * упиралась в края экрана, на мелком — зря оставалась крошечной.
     */
    private int ширина() {
        int поВысоте = (height - 16) * ТЕКСТУРА_Ш / ТЕКСТУРА_В;
        return Math.max(МИН_ШИРИНА, Math.min(Math.min(width - 16, поВысоте), МАКС_ШИРИНА));
    }

    private int высота() { return ширина() * ТЕКСТУРА_В / ТЕКСТУРА_Ш; }

    private int левый() { return (width - ширина()) / 2; }
    private int верхний() { return (height - высота()) / 2; }

    /** Тексель картинки в экранный X. Вся раскладка книги считается только так. */
    private int пX(int тексель) { return левый() + тексель * ширина() / ТЕКСТУРА_Ш; }

    private int пY(int тексель) { return верхний() + тексель * высота() / ТЕКСТУРА_В; }

    private int верхТекста() { return пY(ЛИСТ_В + ПОЛЕ); }
    private int дноТекста() { return пY(ЛИСТ_Н - ПОЛЕ); }

    @Override
    protected void init() {
        int x = пX(ЛИСТ_Л + ПОЛЕ);
        int ширинаЛевой = пX(КОРЕШОК_Л - ПОЛЕ) - x;
        int y = верхТекста() + 8;

        for (int i = 0; i < ВКЛАДКИ.length; i++) {
            addRenderableWidget(new ВкладкаКнопка(x, y, ширинаЛевой, ВКЛАДКИ[i], ClassStyle.римская(i + 1)));
            y += 22;
        }

        // Кнопка живёт внизу левой страницы, внутри книги: снаружи она
        // свисала за край экрана на крупном масштабе интерфейса.
        addRenderableWidget(Button.builder(
                Component.translatable("gui.lmpc_classes.close"), b -> onClose())
            .bounds(x + ширинаЛевой / 2 - 40, дноТекста() - 18, 80, 18)
            .build());
    }

    /** Стрелки листают главы — книга, а не список настроек. */
    @Override
    public boolean keyPressed(int клавиша, int сканкод, int модификаторы) {
        int шаг = switch (клавиша) {
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            default -> 0;
        };
        if (шаг == 0) return super.keyPressed(клавиша, сканкод, модификаторы);

        int текущая = 0;
        for (int i = 0; i < ВКЛАДКИ.length; i++) {
            if (ВКЛАДКИ[i] == открытаяВкладка) текущая = i;
        }
        листать(ВКЛАДКИ[Math.floorMod(текущая + шаг, ВКЛАДКИ.length)]);
        return true;
    }

    /** Новая глава открывается с начала: чужая прокрутка на ней бессмысленна. */
    private void листать(PlayerClassData.Класс вкладка) {
        открытаяВкладка = вкладка;
        прокрутка = 0;
    }

    @Override
    public boolean mouseScrolled(double мышьX, double мышьY, double сдвигX, double сдвигY) {
        if (пределПрокрутки > 0) {
            прокрутка = Math.max(0, Math.min(пределПрокрутки, прокрутка - (int) (сдвигY * 12)));
            return true;
        }
        return super.mouseScrolled(мышьX, мышьY, сдвигX, сдвигY);
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
        renderBackground(графика, мышьX, мышьY, partialTick);

        графика.pose().pushPose();
        графика.pose().translate(левый(), верхний(), 0);
        графика.pose().scale((float) ширина() / ТЕКСТУРА_Ш, (float) высота() / ТЕКСТУРА_В, 1f);
        графика.blit(ФОН_ТЕКСТУРА, 0, 0, 0, 0f, 0f, ТЕКСТУРА_Ш, ТЕКСТУРА_В, ТЕКСТУРА_Ш, ТЕКСТУРА_В);
        графика.pose().popPose();

        super.render(графика, мышьX, мышьY, partialTick);
        рисоватьПравуюСтраницу(графика);
    }

    /** Готовая к отрисовке строка. {@code текст == null} — пустая строка-отбивка. */
    private record Строка(FormattedCharSequence текст, int цвет, int сдвиг, boolean маркер) {}

    private static final Строка ОТБИВКА = new Строка(null, 0, 0, false);

    /**
     * Вся правая страница раскладывается в плоский список строк до
     * отрисовки. Так у страницы есть измеренная высота — без неё
     * прокрутку не к чему прижимать, и прежний код вместо прокрутки
     * просто обрывал текст первым {@code return} за нижним краем.
     */
    private List<Строка> собратьСтраницу(int ширина) {
        List<Строка> строки = new ArrayList<>();
        int акцент = ClassStyle.цвет(открытаяВкладка);

        for (FormattedCharSequence с : font.split(ClassLore.роль(открытаяВкладка), ширина - 20)) {
            строки.add(new Строка(с, ClassStyle.ЧЕРНИЛА_БЛЕДНЫЕ, 0, false));
        }
        строки.add(ОТБИВКА);

        for (FormattedCharSequence с
                : font.split(сАкцентомНаПервомСлове(ClassLore.лор(открытаяВкладка), акцент), ширина)) {
            строки.add(new Строка(с, ClassStyle.ЧЕРНИЛА, 0, false));
        }

        List<Component> способности = ClassLore.способности(открытаяВкладка);
        if (!способности.isEmpty()) {
            строки.add(ОТБИВКА);
            строки.add(new Строка(
                Component.translatable("gui.lmpc_classes.codex.abilities")
                    .withStyle(ChatFormatting.BOLD).getVisualOrderText(), акцент, 0, false));
            for (Component способность : способности) {
                boolean первая = true;
                for (FormattedCharSequence с : font.split(способность, ширина - 8)) {
                    строки.add(new Строка(с, ClassStyle.ЧЕРНИЛА, 7, первая));
                    первая = false;
                }
            }
        }

        строки.add(ОТБИВКА);
        for (FormattedCharSequence с : font.split(ClassLore.ростМастерства(открытаяВкладка), ширина)) {
            строки.add(new Строка(с, ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ, 0, false));
        }
        return строки;
    }

    private void рисоватьПравуюСтраницу(GuiGraphics графика) {
        int x = пX(КОРЕШОК_П + ПОЛЕ);
        int правыйКрай = пX(ЛИСТ_П - ПОЛЕ);
        int ширинаТекста = правыйКрай - x;
        int акцент = ClassStyle.цвет(открытаяВкладка);
        boolean свой = открытаяВкладка == свойКласс();

        Component заголовок = ClassLore.заголовок(открытаяВкладка);
        нарисоватьПечать(графика, правыйКрай - 16, верхТекста() - 2, акцент, заголовок.getString());
        графика.drawString(font, заголовок.copy().withStyle(ChatFormatting.BOLD),
            x, верхТекста(), акцент, false);

        // Полоса мастерства прибита к низу страницы: она не должна
        // уезжать вместе с прокруткой лора.
        int окноВерх = верхТекста() + font.lineHeight + 6;
        int окноНиз = свой ? дноТекста() - 30 : дноТекста();

        List<Строка> строки = собратьСтраницу(ширинаТекста);
        int шаг = font.lineHeight + 1;
        пределПрокрутки = Math.max(0, строки.size() * шаг - (окноНиз - окноВерх));
        прокрутка = Math.max(0, Math.min(прокрутка, пределПрокрутки));

        графика.enableScissor(x, окноВерх, правыйКрай, окноНиз);
        int y = окноВерх - прокрутка;
        for (Строка строка : строки) {
            if (строка.текст() != null && y + шаг > окноВерх && y < окноНиз) {
                if (строка.маркер()) {
                    графика.fill(x + 1, y + 2, x + 3, y + 4, 0xFF000000 | (акцент & 0xFFFFFF));
                }
                графика.drawString(font, строка.текст(), x + строка.сдвиг(), y, строка.цвет(), false);
            }
            y += шаг;
        }
        графика.disableScissor();

        if (пределПрокрутки > 0) {
            полосаПрокрутки(графика, правыйКрай + 2, окноВерх, окноНиз, строки.size() * шаг);
        }

        if (свой) {
            рисоватьМастерство(графика, x, дноТекста() - 26, ширинаТекста, акцент);
        }
    }

    /** Тонкая закладка на поле страницы: единственный знак, что текст ниже продолжается. */
    private void полосаПрокрутки(GuiGraphics графика, int x, int верх, int низ, int всего) {
        int высотаОкна = низ - верх;
        int ползунок = Math.max(8, высотаОкна * высотаОкна / всего);
        int сдвиг = (высотаОкна - ползунок) * прокрутка / пределПрокрутки;
        графика.fill(x, верх, x + 2, низ, 0x30000000 | ClassStyle.ЧЕРНИЛА);
        графика.fill(x, верх + сдвиг, x + 2, верх + сдвиг + ползунок, 0xA0000000 | ClassStyle.ЧЕРНИЛА);
    }

    /** Первое слово лора выделено жирным и цветом класса — кивок в сторону заглавных буквиц. */
    private static FormattedText сАкцентомНаПервомСлове(Component текст, int цвет) {
        String строка = текст.getString();
        int пробел = строка.indexOf(' ');
        if (пробел < 0) return текст.copy().withStyle(ChatFormatting.BOLD).withColor(цвет);
        return Component.literal(строка.substring(0, пробел)).withStyle(ChatFormatting.BOLD).withColor(цвет)
            .append(Component.literal(строка.substring(пробел)));
    }

    /** Маленькая восковая печать в углу страницы — просто заливка и буква, без новых текстур. */
    private void нарисоватьПечать(GuiGraphics графика, int x, int y, int цвет, String заголовок) {
        int размер = 16;
        графика.fill(x, y, x + размер, y + размер, 0xFF000000 | (цвет & 0xFFFFFF));
        графика.renderOutline(x, y, размер, размер, 0xFF1A1208);
        String буква = заголовок.isEmpty() ? "?" : заголовок.substring(0, 1);
        графика.drawCenteredString(font, буква, x + размер / 2, y + 4, ClassStyle.ПЕРГАМЕНТ);
    }

    /**
     * Мастерство своего класса: число, тир римской цифрой, полоса
     * с засечками на порогах и остаток до следующего тира.
     */
    private void рисоватьМастерство(GuiGraphics графика, int x, int y, int ширина, int цвет) {
        PlayerClassData д = ClassStyle.свои();
        if (д == null) return;

        Component подпись = Component.translatable(
            "gui.lmpc_classes.codex.mastery", д.мастерство, ClassMastery.МАКСИМУМ);
        графика.drawString(font, подпись, x, y, ClassStyle.ЧЕРНИЛА, false);

        Component тир = Component.translatable("gui.lmpc_classes.codex.tier", ClassStyle.римская(д.тир()));
        графика.drawString(font, тир.copy().withStyle(ChatFormatting.BOLD),
            x + ширина - font.width(тир), y, цвет, false);

        ClassStyle.полосаМастерства(графика, x, y + 12, ширина, д.мастерство, цвет);

        int доТира = д.доСледующегоТира();
        Component остаток = доТира < 0
            ? Component.translatable("gui.lmpc_classes.codex.tier_max")
            : Component.translatable("gui.lmpc_classes.codex.tier_next", доТира);
        графика.drawString(font, остаток, x, y + 20, ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Пункт оглавления на левой странице — просто текст, без ванильной кнопки. */
    private class ВкладкаКнопка extends Button {
        private final PlayerClassData.Класс класс;
        private final String номер;

        ВкладкаКнопка(int x, int y, int ширина, PlayerClassData.Класс класс, String номер) {
            super(x, y, ширина, 18, ClassLore.заголовок(класс),
                b -> листать(класс), DEFAULT_NARRATION);
            this.класс = класс;
            this.номер = номер;
        }

        @Override
        protected void renderWidget(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
            boolean активна = класс == открытаяВкладка;
            boolean свой = класс == свойКласс();
            int цвет = активна
                ? ClassStyle.цвет(класс)
                : (isHovered() ? ClassStyle.ЧЕРНИЛА_БЛЕДНЫЕ : ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ);

            графика.drawString(font,
                Component.literal(номер + ".  ").append(getMessage())
                    .withStyle(активна ? ChatFormatting.BOLD : ChatFormatting.ITALIC),
                getX(), getY(), цвет, false);

            // Свой класс помечен точкой цвета класса — видно и на чужой вкладке.
            if (свой) {
                графика.fill(getX() + getWidth() - 5, getY() + 2,
                    getX() + getWidth() - 1, getY() + 6, ClassStyle.заливка(класс));
            }
            if (активна) {
                графика.fill(getX(), getY() + 12, getX() + getWidth(), getY() + 13, 0x804A3B25);
            }
        }
    }
}
