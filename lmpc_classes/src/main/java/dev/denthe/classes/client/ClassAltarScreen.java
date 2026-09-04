package dev.denthe.classes.client;

import dev.denthe.classes.PlayerClassData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Экран алтаря призвания. Спек — 2026-09-04-klassy-design.md, раздел 2.
 *
 * Без меню-контейнера: кнопки шлют команду {@code /lmpcclasses choose
 * <класс>} тем же приёмом, что панель `lmpc_gmtools` шлёт ванильные
 * команды (`minecraft.getConnection().sendCommand`). Сервер сам
 * проверяет кулдаун и режет мастерство — экран ничего не решает.
 */
public class ClassAltarScreen extends Screen {
    private static final String[] НАЗВАНИЯ = {
        "Без класса", "Клирик", "Кузнец", "Фермер", "Летописец"
    };

    public ClassAltarScreen() {
        super(Component.literal("Алтарь призвания"));
    }

    /** Открыть экран. Единственная точка входа из {@link dev.denthe.classes.ClassAltarBlock}. */
    public static void открыть() {
        Minecraft.getInstance().setScreen(new ClassAltarScreen());
    }

    @Override
    protected void init() {
        PlayerClassData.Класс[] классы = PlayerClassData.Класс.values();
        int высотаКнопки = 20, отступ = 4;
        int всего = классы.length * (высотаКнопки + отступ) - отступ;
        int y = (height - всего) / 2;

        for (int i = 0; i < классы.length; i++) {
            PlayerClassData.Класс класс = классы[i];
            addRenderableWidget(Button.builder(Component.literal(НАЗВАНИЯ[i]), b -> выбрать(класс))
                .bounds(width / 2 - 100, y, 200, высотаКнопки)
                .build());
            y += высотаКнопки + отступ;
        }

        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(width / 2 - 100, y + отступ, 200, высотаКнопки)
            .build());
    }

    private void выбрать(PlayerClassData.Класс класс) {
        Minecraft.getInstance().getConnection().sendCommand(
            "lmpcclasses choose " + класс.name().toLowerCase(Locale.ROOT));
        onClose();
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float partialTick) {
        renderBackground(графика, мышьX, мышьY, partialTick);
        super.render(графика, мышьX, мышьY, partialTick);
        графика.drawCenteredString(font, title, width / 2, (height - (НАЗВАНИЯ.length + 1) * 24) / 2 - 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
