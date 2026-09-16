package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.core.Marks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Выбор того, что поставить: заготовка из каталога или своя строка.
 * Спек «Пометки Мастера игры», раздел 10.
 *
 * Оформлен тёмной панелью, а не кожаным планшетом: это служебное окно
 * ГМ, и оно нарочно не притворяется частью того, что видит игрок.
 *
 * Своих правок не делает — печатает команды `/plague health` и уходит
 * обратно в редактор. Отдельной команды «изменить» в моде нет, поэтому
 * правка строки — это снять старую и поставить новую.
 */
public class MarkPickerScreen extends Screen {

    private final HealthEditScreen родитель;
    private final String ник;

    /** Номер правимой пометки или −1, если ставим новую. */
    private final int правим;

    private Marks.Место место;
    private boolean заменяет;

    private EditBox поле;
    private Button кнопкаМеста, кнопкаРежима;

    private static final int ШИРИНА = 236, ВЫСОТА = 196;
    private static final int ФОН = 0xF0161A19, КРАЙ = 0xFF3A4245, ТЕКСТ = 0xFFE2DDCE;

    private int левый, верхний;

    public MarkPickerScreen(HealthEditScreen родитель, String ник, Marks.Место место, int правим) {
        super(Component.translatable("plaguecore.health.edit.title"));
        this.родитель = родитель;
        this.ник = ник;
        this.место = место;
        this.правим = правим;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        левый = (width - ШИРИНА) / 2;
        верхний = (height - ВЫСОТА) / 2;

        int x = левый + 10;
        int y = верхний + 26;

        кнопкаМеста = addRenderableWidget(Button.builder(подписьМеста(), к -> следующееМесто())
            .bounds(x, y, 104, 16).build());
        кнопкаРежима = addRenderableWidget(Button.builder(подписьРежима(), к -> {
            заменяет = !заменяет;
            кнопкаРежима.setMessage(подписьРежима());
        }).bounds(x + 108, y, 104, 16).build());

        // Заготовки двумя столбцами: десяти штукам в один столбец не хватит
        // высоты, а полосу прокрутки ради десяти строк заводить незачем.
        y += 24;
        Marks.Заготовка[] все = Marks.Заготовка.values();
        for (int i = 0; i < все.length; i++) {
            Marks.Заготовка з = все[i];
            int кx = x + (i % 2) * 108;
            int кy = y + (i / 2) * 18;
            addRenderableWidget(Button.builder(Component.translatable(Marks.имя(з)),
                к -> поставить(з.идентификатор(), null)).bounds(кx, кy, 104, 16).build());
        }

        y += ((все.length + 1) / 2) * 18 + 10;
        поле = new EditBox(font, x, y, 212, 16, Component.translatable("plaguecore.health.edit.own"));
        поле.setMaxLength(Marks.ДЛИНА_СТРОКИ);
        поле.setHint(Component.translatable("plaguecore.health.edit.own"));
        addRenderableWidget(поле);

        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("plaguecore.health.edit.own"),
            к -> своя()).bounds(x, y, 104, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
            к -> onClose()).bounds(x + 108, y, 104, 16).build());
    }

    private Component подписьМеста() {
        return Component.translatable("plaguecore.health.edit.place")
            .append(": ")
            .append(Component.translatable(
                "plaguecore.health.place." + место.name().toLowerCase(Locale.ROOT)));
    }

    private Component подписьРежима() {
        return Component.translatable(заменяет
            ? "plaguecore.health.edit.replace" : "plaguecore.health.edit.append");
    }

    private void следующееМесто() {
        Marks.Место[] все = Marks.Место.values();
        место = все[(место.ordinal() + 1) % все.length];
        кнопкаМеста.setMessage(подписьМеста());
    }

    private void своя() {
        String строка = поле.getValue().trim();
        if (строка.isEmpty()) return;      // пустую строку сервер всё равно отвергнет
        поставить(null, строка);
    }

    /**
     * Снять старую (если правим) и поставить новую. Обе команды идут
     * на сервер, и он же проверяет право, длину и предел: экран здесь
     * только печатает то, что ГМ мог бы набрать руками.
     */
    private void поставить(String заготовка, String текст) {
        if (правим >= 0) родитель.команда("plague health remove " + ник + " " + правим);

        String режим = заменяет ? "replace" : "append";
        String местоИмя = место.name().toLowerCase(Locale.ROOT);
        родитель.команда(заготовка != null
            ? "plague health add " + ник + " " + местоИмя + " " + режим + " " + заготовка
            : "plague health text " + ник + " " + местоИмя + " " + режим + " " + текст);

        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(родитель);
    }

    /**
     * Панель рисуется здесь, а не в render: Screen сначала зовёт фон,
     * а уже потом свои виджеты. Нарисуй мы панель в render — она легла бы
     * поверх кнопок и закрыла их.
     */
    @Override
    public void renderBackground(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        super.renderBackground(графика, мышьX, мышьY, кадр);

        графика.fill(левый, верхний, левый + ШИРИНА, верхний + ВЫСОТА, ФОН);
        графика.fill(левый, верхний, левый + ШИРИНА, верхний + 1, КРАЙ);
        графика.fill(левый, верхний + ВЫСОТА - 1, левый + ШИРИНА, верхний + ВЫСОТА, КРАЙ);
        графика.fill(левый, верхний, левый + 1, верхний + ВЫСОТА, КРАЙ);
        графика.fill(левый + ШИРИНА - 1, верхний, левый + ШИРИНА, верхний + ВЫСОТА, КРАЙ);
    }

    @Override
    public void render(GuiGraphics графика, int мышьX, int мышьY, float кадр) {
        super.render(графика, мышьX, мышьY, кадр);

        графика.drawString(font, title, левый + 10, верхний + 10, ТЕКСТ, false);
        графика.drawString(font, Component.literal(ник),
            левый + ШИРИНА - 10 - font.width(ник), верхний + 10, 0xFF8A9490, false);
    }
}
