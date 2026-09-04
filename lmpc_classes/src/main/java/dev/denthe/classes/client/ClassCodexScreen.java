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

import java.util.List;

/**
 * Гримуар. Спек — 2026-09-04-klassy-design.md, раздел 2.1 (мастерство)
 * и 4–7 (роли, лор). Фон — разворот книги, картинка владельца
 * (736×490, соотношение сохраняется при масштабировании). Левая
 * страница — оглавление из четырёх классов, правая — заголовок,
 * роль, лор, способности и, у своего класса, мастерство.
 *
 * **Что изменилось в 0.6.0.**
 * <ul>
 * <li>Мастерство наконец настоящее. Раньше экран читал вложение
 *     на клиенте, куда оно не синкалось, и честно показывал ноль
 *     любому игроку любого класса; заодно гримуар всегда открывался
 *     на Клирике, потому что свой класс читался оттуда же.</li>
 * <li>Появился тир (I–III), засечки порогов на полосе и строка
 *     «сколько до следующего» — без них число 73 ничего не значило.</li>
 * <li>Появился раздел способностей: до этого книга про классы
 *     не говорила, что классы, собственно, делают.</li>
 * <li>Стрелки ← → листают вкладки, как страницы.</li>
 * </ul>
 *
 * Отступы внутри картинки подобраны на глаз по референсу, не измерены
 * пиксель в пиксель — поправить после первого взгляда в игре.
 */
public class ClassCodexScreen extends Screen {
    private static final ResourceLocation ФОН_ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "textures/gui/codex_background.png");
    private static final int ТЕКСТУРА_Ш = 736;
    private static final int ТЕКСТУРА_В = 490;

    private static final int ШИРИНА = 360;
    private static final int ВЫСОТА = Math.round(ШИРИНА * (float) ТЕКСТУРА_В / ТЕКСТУРА_Ш);

    // Отступы внутри картинки разворота — на глаз, доля от размера книги.
    private static final int ОТСТУП_КРАЙ = 18;
    private static final int ОТСТУП_КОРЕШОК = 12;
    private static final int ОТСТУП_ВЕРХ = 16;
    private static final int ОТСТУП_НИЗ = 16;

    /** Вкладки — только настоящие классы: «без класса» не глава книги. */
    private static final PlayerClassData.Класс[] ВКЛАДКИ = {
        PlayerClassData.Класс.CLERIC,
        PlayerClassData.Класс.SMITH,
        PlayerClassData.Класс.FARMER,
        PlayerClassData.Класс.CHRONICLER,
    };

    private PlayerClassData.Класс открытаяВкладка;

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

    private int левый() { return (width - ШИРИНА) / 2; }
    private int верхний() { return (height - ВЫСОТА) / 2; }
    private int корешок() { return левый() + ШИРИНА / 2; }

    @Override
    protected void init() {
        int леваяX = левый() + ОТСТУП_КРАЙ;
        int y = верхний() + ОТСТУП_ВЕРХ + 8;

        for (int i = 0; i < ВКЛАДКИ.length; i++) {
            addRenderableWidget(new ВкладкаКнопка(
                леваяX, y, корешок() - ОТСТУП_КОРЕШОК - леваяX, ВКЛАДКИ[i], ClassStyle.римская(i + 1)));
            y += 22;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.lmpc_classes.close"), b -> onClose())
            .bounds(левый() + ШИРИНА / 2 - 40, верхний() + ВЫСОТА + 6, 80, 18)
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
        открытаяВкладка = ВКЛАДКИ[Math.floorMod(текущая + шаг, ВКЛАДКИ.length)];
        return true;
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
        renderBackground(графика, мышьX, мышьY, partialTick);

        графика.pose().pushPose();
        графика.pose().translate(левый(), верхний(), 0);
        графика.pose().scale((float) ШИРИНА / ТЕКСТУРА_Ш, (float) ВЫСОТА / ТЕКСТУРА_В, 1f);
        графика.blit(ФОН_ТЕКСТУРА, 0, 0, 0, 0f, 0f, ТЕКСТУРА_Ш, ТЕКСТУРА_В, ТЕКСТУРА_Ш, ТЕКСТУРА_В);
        графика.pose().popPose();

        super.render(графика, мышьX, мышьY, partialTick);
        рисоватьПравуюСтраницу(графика);
    }

    private void рисоватьПравуюСтраницу(GuiGraphics графика) {
        int x = корешок() + ОТСТУП_КОРЕШОК;
        int правыйКрай = левый() + ШИРИНА - ОТСТУП_КРАЙ;
        int ширинаТекста = правыйКрай - x;
        int y = верхний() + ОТСТУП_ВЕРХ;
        int дно = верхний() + ВЫСОТА - ОТСТУП_НИЗ;
        int акцент = ClassStyle.цвет(открытаяВкладка);
        boolean свой = открытаяВкладка == свойКласс();

        // Место под мастерство резервируем заранее: полоса внизу страницы
        // не должна оказаться под лором, если лор длинный.
        int дноТекста = свой ? дно - 30 : дно;

        Component заголовок = ClassLore.заголовок(открытаяВкладка);
        нарисоватьПечать(графика, правыйКрай - 18, y - 2, акцент, заголовок.getString());

        графика.drawString(font, заголовок.copy().withStyle(ChatFormatting.BOLD), x, y, акцент, false);
        y += 14;

        for (FormattedCharSequence строка : font.split(ClassLore.роль(открытаяВкладка), ширинаТекста - 22)) {
            if (y > дноТекста) return;
            графика.drawString(font, строка, x, y, ClassStyle.ЧЕРНИЛА_БЛЕДНЫЕ, false);
            y += font.lineHeight + 1;
        }
        y += 4;

        for (FormattedCharSequence строка
                : font.split(сАкцентомНаПервомСлове(ClassLore.лор(открытаяВкладка), акцент), ширинаТекста)) {
            if (y > дноТекста) return;
            графика.drawString(font, строка, x, y, ClassStyle.ЧЕРНИЛА, false);
            y += font.lineHeight + 2;
        }

        y = рисоватьСпособности(графика, x, y + 6, ширинаТекста, дноТекста, акцент);

        if (свой) {
            рисоватьМастерство(графика, x, дно - 26, ширинаТекста, акцент);
        }
    }

    /**
     * Список того, что класс реально умеет, плюс строка «чем растёт
     * мастерство». Ни того, ни другого в книге не было — она
     * описывала роли, но не отвечала, что нажимать.
     */
    private int рисоватьСпособности(
            GuiGraphics графика, int x, int y, int ширина, int дно, int акцент) {
        List<Component> способности = ClassLore.способности(открытаяВкладка);
        if (способности.isEmpty() || y > дно) return y;

        графика.drawString(font, Component.translatable("gui.lmpc_classes.codex.abilities")
            .copy().withStyle(ChatFormatting.BOLD), x, y, акцент, false);
        y += font.lineHeight + 2;

        for (Component способность : способности) {
            // Маркер ставим до переноса: после цикла y уже уехал на несколько строк.
            графика.fill(x + 1, y + 2, x + 3, y + 4, 0xFF000000 | (акцент & 0xFFFFFF));
            for (FormattedCharSequence строка : font.split(способность, ширина - 6)) {
                if (y > дно) return y;
                графика.drawString(font, строка, x + 6, y, ClassStyle.ЧЕРНИЛА, false);
                y += font.lineHeight;
            }
            y += 2;
        }

        if (y <= дно) {
            for (FormattedCharSequence строка
                    : font.split(ClassLore.ростМастерства(открытаяВкладка), ширина)) {
                if (y > дно) break;
                графика.drawString(font, строка, x, y, ClassStyle.ЧЕРНИЛА_ПОГАШЕННЫЕ, false);
                y += font.lineHeight;
            }
        }
        return y;
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
                b -> открытаяВкладка = класс, DEFAULT_NARRATION);
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
