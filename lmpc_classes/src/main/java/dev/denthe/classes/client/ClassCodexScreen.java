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

    private final PlayerClassData.Класс свойКласс;
    private PlayerClassData.Класс открытаяВкладка;

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

        for (PlayerClassData.Класс класс : PlayerClassData.Класс.values()) {
            if (класс == PlayerClassData.Класс.NONE) continue;
            addRenderableWidget(new ВкладкаКнопка(леваяX, y, корешок() - ОТСТУП_КОРЕШОК - леваяX, класс));
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

        String заголовок = ClassLoreAccess.заголовок(открытаяВкладка);
        String роль = ClassLoreAccess.роль(открытаяВкладка);
        String лор = ClassLoreAccess.лор(открытаяВкладка);

        графика.drawString(font, Component.literal(заголовок).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED),
            x, y, 0x000000, false);
        y += 14;

        if (!роль.isEmpty()) {
            for (FormattedCharSequence строка : разбить(роль, ширинаТекста)) {
                графика.drawString(font, строка, x, y, 0x4A3B25, false);
                y += font.lineHeight + 1;
            }
            y += 4;
        }

        for (FormattedCharSequence строка : разбить(лор, ширинаТекста)) {
            графика.drawString(font, строка, x, y, 0x1A1208, false);
            y += font.lineHeight + 2;
        }

        if (открытаяВкладка == свойКласс && свойКласс != PlayerClassData.Класс.NONE) {
            нарисоватьМастерство(графика, x, верхний() + ВЫСОТА - ОТСТУП_НИЗ - 20, ширинаТекста);
        }
    }

    private List<FormattedCharSequence> разбить(String текст, int ширина) {
        return font.split(FormattedText.of(текст), ширина);
    }

    /**
     * Тиров мастерства пока не существует нигде в коде (ничего его
     * не растит, спек 2.1 только объявляет идею) — полоса рисуется
     * от 0 до 100 просто как шкала, без деления на тиры.
     */
    private void нарисоватьМастерство(GuiGraphics графика, int x, int y, int ширина) {
        int мастерство = ClassLoreAccess.мастерство();

        графика.drawString(font, Component.literal("Мастерство: " + мастерство)
            .withStyle(ChatFormatting.DARK_GREEN), x, y, 0x1A1208, false);

        int полосаY = y + 12;
        int доляЗаполнено = Math.max(0, Math.min(ширина, ширина * Math.min(100, мастерство) / 100));
        графика.fill(x, полосаY, x + ширина, полосаY + 5, 0xFF6B5B3A);
        графика.fill(x, полосаY, x + доляЗаполнено, полосаY + 5, 0xFF4A6B2E);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Пункт списка классов на левой странице — просто текст, без ванильной кнопки. */
    private class ВкладкаКнопка extends Button {
        private final PlayerClassData.Класс класс;

        ВкладкаКнопка(int x, int y, int ширина, PlayerClassData.Класс класс) {
            super(x, y, ширина, 16, Component.literal(ClassLoreAccess.заголовок(класс)),
                b -> открытаяВкладка = класс, DEFAULT_NARRATION);
            this.класс = класс;
        }

        @Override
        protected void renderWidget(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
            boolean активна = класс == открытаяВкладка;
            boolean наведена = isHovered();
            int цвет = активна ? 0x000000 : (наведена ? 0x4A3B25 : 0x6B5B3A);
            var стиль = активна ? ChatFormatting.BOLD : ChatFormatting.ITALIC;
            графика.drawString(font, getMessage().copy().withStyle(стиль), getX(), getY(), цвет, false);
            if (активна) {
                графика.fill(getX(), getY() + 12, getX() + getWidth(), getY() + 13, 0x804A3B25);
            }
        }
    }
}
