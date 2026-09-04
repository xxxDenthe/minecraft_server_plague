package dev.denthe.classes.client;

import dev.denthe.classes.ClassLoreAccess;
import dev.denthe.classes.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Гримуар. Спек — 2026-09-04-klassy-design.md, раздел 2.1 (мастерство)
 * и 4–7 (роли, лор). Не ванильная книга — своя пергаментная вёрстка:
 * четыре вкладки классов сверху, текущий класс открыт по умолчанию,
 * чужие вкладки нарочно без мастерства — только роль и лор, чтобы
 * не отвлекать от своего.
 */
public class ClassCodexScreen extends Screen {
    private static final int ШИРИНА = 260;
    private static final int ВЫСОТА = 190;
    private static final int ФОН = 0xEE2B2118;
    private static final int РАМКА = 0xFF6B5B3A;

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

    @Override
    protected void init() {
        int левый = (width - ШИРИНА) / 2;
        int верхний = (height - ВЫСОТА) / 2;

        PlayerClassData.Класс[] классы = PlayerClassData.Класс.values();
        int вкладок = классы.length - 1; // без NONE
        int ширинаВкладки = ШИРИНА / вкладок;
        int i = 0;
        for (PlayerClassData.Класс класс : классы) {
            if (класс == PlayerClassData.Класс.NONE) continue;
            int индекс = i++;
            addRenderableWidget(Button.builder(
                    Component.literal(ClassLoreAccess.заголовок(класс)),
                    b -> открытаяВкладка = класс)
                .bounds(левый + индекс * ширинаВкладки, верхний, ширинаВкладки, 18)
                .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(левый + ШИРИНА / 2 - 40, верхний + ВЫСОТА + 6, 80, 18)
            .build());
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
        renderBackground(графика, мышьX, мышьY, partialTick);

        int левый = (width - ШИРИНА) / 2;
        int верхний = (height - ВЫСОТА) / 2;

        графика.fill(левый, верхний, левый + ШИРИНА, верхний + ВЫСОТА, ФОН);
        графика.renderOutline(левый, верхний, ШИРИНА, ВЫСОТА, РАМКА);

        super.render(графика, мышьX, мышьY, partialTick);

        String заголовок = ClassLoreAccess.заголовок(открытаяВкладка);
        String роль = ClassLoreAccess.роль(открытаяВкладка);
        String лор = ClassLoreAccess.лор(открытаяВкладка);

        int текстЛевый = левый + 12;
        int y = верхний + 26;

        графика.drawString(font, Component.literal(заголовок).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
            текстЛевый, y, 0xFFFFFF, false);
        y += 14;

        if (!роль.isEmpty()) {
            графика.drawString(font, Component.literal(роль).withStyle(ChatFormatting.GRAY),
                текстЛевый, y, 0xFFFFFF, false);
            y += 14;
        }

        y += 4;
        for (FormattedCharSequence строка : разбить(лор, ШИРИНА - 24)) {
            графика.drawString(font, строка, текстЛевый, y, 0xE0D8C0, false);
            y += font.lineHeight + 2;
        }

        if (открытаяВкладка == свойКласс && свойКласс != PlayerClassData.Класс.NONE) {
            нарисоватьМастерство(графика, текстЛевый, верхний + ВЫСОТА - 34, ШИРИНА - 24);
        }
    }

    private List<FormattedCharSequence> разбить(String текст, int ширина) {
        return font.split(FormattedText.of(текст), ширина);
    }

    /**
     * Тиров мастерства пока не существует нигде в коде (ничего его
     * не растит, спек 2.1 только объявляет идею) — полоса рисуется
     * от 0 до 100 просто как шкала, без деления на тиры, чтобы
     * не выдумывать пороги, которых ещё нет.
     */
    private void нарисоватьМастерство(GuiGraphics графика, int x, int y, int ширина) {
        int мастерство = ClassLoreAccess.мастерство();

        графика.drawString(font, Component.literal("Мастерство: " + мастерство)
            .withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFF, false);

        int полосаY = y + 12;
        int доляЗаполнено = Math.max(0, Math.min(ширина, ширина * Math.min(100, мастерство) / 100));
        графика.fill(x, полосаY, x + ширина, полосаY + 6, 0xFF3A2E1C);
        графика.fill(x, полосаY, x + доляЗаполнено, полосаY + 6, 0xFFB88A3D);
        графика.renderOutline(x, полосаY, ширина, 6, РАМКА);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
