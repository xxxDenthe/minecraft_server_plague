package dev.denthe.classes.client;

import dev.denthe.classes.ClassLoreAccess;
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

import java.util.List;

/**
 * Гримуар. Спек — 2026-09-04-klassy-design.md, раздел 2.1 (мастерство)
 * и 4–7 (роли, лор). Фон — разворот книги, картинка владельца
 * (736×490, соотношение 3:2 сохранено при масштабировании). Левая
 * страница — список классов (просто текст, без ванильной серой
 * кнопки — это книга, не инвентарь), правая — роль, лор и, только
 * у своего класса, мастерство.
 *
 * Точные отступы внутри картинки подобраны на глаз по референсу,
 * не измерены пиксель в пиксель — поправить после первого взгляда
 * в игре, я сам вижу только сгенерированный кадр, не рендер.
 */
public class ClassCodexScreen extends Screen {
    private static final ResourceLocation ФОН_ТЕКСТУРА =
        ResourceLocation.fromNamespaceAndPath(LmpcClasses.MODID, "textures/gui/codex_background.png");
    private static final int ТЕКСТУРА_Ш = 736;
    private static final int ТЕКСТУРА_В = 490;

    private static final int ШИРИНА = 320;
    private static final int ВЫСОТА = Math.round(ШИРИНА * (float) ТЕКСТУРА_В / ТЕКСТУРА_Ш);

    // Отступы внутри картинки разворота — на глаз, доля от размера книги.
    private static final int ОТСТУП_КРАЙ = 16;
    private static final int ОТСТУП_КОРЕШОК = 10;
    private static final int ОТСТУП_ВЕРХ = 14;
    private static final int ОТСТУП_НИЗ = 14;

    private static final String[] НОМЕР = { "I", "II", "III", "IV" };

    private final PlayerClassData.Класс свойКласс;
    private PlayerClassData.Класс открытаяВкладка;

    /** Цветовой акцент класса — заголовок, печать, полоса мастерства. Держит книгу единой, не разноцветной. */
    private static int цветКласса(PlayerClassData.Класс класс) {
        return switch (класс) {
            case CLERIC -> 0xB8942F;      // тёплое золото
            case SMITH -> 0x8A4A1C;       // калёное железо
            case FARMER -> 0x4A6B2E;      // земля, урожай
            case CHRONICLER -> 0x4A3A7A;  // чернила, редкий лиловый акцент
            case NONE -> 0x4A3B25;
        };
    }

    private ClassCodexScreen(PlayerClassData.Класс свойКласс) {
        super(Component.literal("Гримуар"));
        this.свойКласс = свойКласс;
        this.открытаяВкладка = свойКласс == PlayerClassData.Класс.NONE
            ? PlayerClassData.Класс.CLERIC
            : свойКласс;
    }

    public static void открыть(PlayerClassData.Класс свойКласс) {
        Minecraft.getInstance().setScreen(new ClassCodexScreen(свойКласс));
    }

    private int левый() { return (width - ШИРИНА) / 2; }
    private int верхний() { return (height - ВЫСОТА) / 2; }
    private int корешок() { return левый() + ШИРИНА / 2; }

    @Override
    protected void init() {
        int леваяX = левый() + ОТСТУП_КРАЙ;
        int y = верхний() + ОТСТУП_ВЕРХ + 6;

        int i = 0;
        for (PlayerClassData.Класс класс : PlayerClassData.Класс.values()) {
            if (класс == PlayerClassData.Класс.NONE) continue;
            addRenderableWidget(new ВкладкаКнопка(леваяX, y, корешок() - ОТСТУП_КОРЕШОК - леваяX, класс, НОМЕР[i++]));
            y += 20;
        }

        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(левый() + ШИРИНА / 2 - 40, верхний() + ВЫСОТА + 6, 80, 18)
            .build());
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
        int акцент = цветКласса(открытаяВкладка);

        String заголовок = ClassLoreAccess.заголовок(открытаяВкладка);
        String роль = ClassLoreAccess.роль(открытаяВкладка);
        String лор = ClassLoreAccess.лор(открытаяВкладка);

        нарисоватьПечать(графика, правыйКрай - 18, верхний() + ОТСТУП_ВЕРХ - 2, акцент, заголовок);

        графика.drawString(font, Component.literal(заголовок).withStyle(ChatFormatting.BOLD),
            x, y, акцент, false);
        y += 14;

        if (!роль.isEmpty()) {
            for (FormattedCharSequence строка : разбить(роль, ширинаТекста - 22)) {
                графика.drawString(font, строка, x, y, 0x4A3B25, false);
                y += font.lineHeight + 1;
            }
            y += 4;
        }

        for (FormattedCharSequence строка : разбить(сАкцентомНаПервомСлове(лор, акцент), ширинаТекста)) {
            графика.drawString(font, строка, x, y, 0x1A1208, false);
            y += font.lineHeight + 2;
        }

        if (открытаяВкладка == свойКласс && свойКласс != PlayerClassData.Класс.NONE) {
            нарисоватьМастерство(графика, x, верхний() + ВЫСОТА - ОТСТУП_НИЗ - 20, ширинаТекста, акцент);
        }
    }

    /** Первое слово лора выделено жирным и цветом класса — маленький кивок в сторону заглавных буквиц. */
    private static FormattedText сАкцентомНаПервомСлове(String текст, int цвет) {
        int пробел = текст.indexOf(' ');
        if (пробел < 0) return Component.literal(текст).withStyle(ChatFormatting.BOLD).withColor(цвет);
        return Component.literal(текст.substring(0, пробел)).withStyle(ChatFormatting.BOLD).withColor(цвет)
            .append(Component.literal(текст.substring(пробел)));
    }

    /** Маленькая восковая печать в углу страницы — просто заливка и буква, без новых текстур. */
    private void нарисоватьПечать(GuiGraphics графика, int x, int y, int цвет, String заголовок) {
        int размер = 16;
        графика.fill(x, y, x + размер, y + размер, 0xFF000000 | (цвет & 0xFFFFFF));
        графика.renderOutline(x, y, размер, размер, 0xFF1A1208);
        String буква = заголовок.isEmpty() ? "?" : заголовок.substring(0, 1);
        графика.drawCenteredString(font, буква, x + размер / 2, y + 4, 0xFFF0E6D2);
    }

    private List<FormattedCharSequence> разбить(String текст, int ширина) {
        return разбить(FormattedText.of(текст), ширина);
    }

    private List<FormattedCharSequence> разбить(FormattedText текст, int ширина) {
        return font.split(текст, ширина);
    }

    /**
     * Тиров мастерства пока не существует нигде в коде (ничего его
     * не растит, спек 2.1 только объявляет идею) — полоса рисуется
     * от 0 до 100 просто как шкала, без деления на тиры.
     */
    private void нарисоватьМастерство(GuiGraphics графика, int x, int y, int ширина, int цвет) {
        int мастерство = ClassLoreAccess.мастерство();

        графика.drawString(font, Component.literal("Мастерство: " + мастерство),
            x, y, 0x1A1208, false);

        int полосаY = y + 12;
        int доляЗаполнено = Math.max(0, Math.min(ширина, ширина * Math.min(100, мастерство) / 100));
        графика.fill(x, полосаY, x + ширина, полосаY + 5, 0xFF6B5B3A);
        графика.fill(x, полосаY, x + доляЗаполнено, полосаY + 5, 0xFF000000 | (цвет & 0xFFFFFF));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Пункт списка классов на левой странице — просто текст, без ванильной кнопки. */
    private class ВкладкаКнопка extends Button {
        private final PlayerClassData.Класс класс;
        private final String номер;

        ВкладкаКнопка(int x, int y, int ширина, PlayerClassData.Класс класс, String номер) {
            super(x, y, ширина, 16, Component.literal(ClassLoreAccess.заголовок(класс)),
                b -> открытаяВкладка = класс, DEFAULT_NARRATION);
            this.класс = класс;
            this.номер = номер;
        }

        @Override
        protected void renderWidget(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
            boolean активна = класс == открытаяВкладка;
            boolean наведена = isHovered();
            int цвет = активна ? цветКласса(класс) : (наведена ? 0x4A3B25 : 0x8A7A5A);
            var стиль = активна ? ChatFormatting.BOLD : ChatFormatting.ITALIC;
            графика.drawString(font, Component.literal(номер + ".  ").append(getMessage()).withStyle(стиль),
                getX(), getY(), цвет, false);
            if (активна) {
                графика.fill(getX(), getY() + 12, getX() + getWidth(), getY() + 13, 0x804A3B25);
            }
        }
    }
}
