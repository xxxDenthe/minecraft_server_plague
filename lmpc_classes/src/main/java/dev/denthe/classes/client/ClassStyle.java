package dev.denthe.classes.client;

import dev.denthe.classes.ClassMastery;
import dev.denthe.classes.ClassSwitch;
import dev.denthe.classes.ClassesConfig;
import dev.denthe.classes.PlayerClassData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Общий язык экранов мода: палитра, римские цифры и пара мелких
 * рисовалок. Заведён, потому что алтарь и гримуар — две страницы
 * одной книги, а раскрашивались порознь: цвета классов жили копией
 * в каждом экране, и правка в одном тихо расходилась с другим.
 *
 * Палитра держится проектного правила: чума тёмная и серая, лиловый —
 * редким акцентом (заметка 2026-09-03-palitra-chumy). Лиловый здесь
 * ровно один — Летописец.
 */
public final class ClassStyle {
    private ClassStyle() {}

    /** Основной текст на пергаменте — почти чёрные чернила, но не чёрные. */
    public static final int ЧЕРНИЛА = 0x1A1208;
    /** Второстепенный текст: роль, подписи. */
    public static final int ЧЕРНИЛА_БЛЕДНЫЕ = 0x4A3B25;
    /** Погашенный текст: неактивная вкладка, недоступный выбор. */
    public static final int ЧЕРНИЛА_ПОГАШЕННЫЕ = 0x8A7A5A;
    /** Цвет письма поверх тёмной заливки — печать, значок тира. */
    public static final int ПЕРГАМЕНТ = 0xF0E6D2;

    /** Цветовой акцент класса. Держит книгу единой, а не разноцветной. */
    public static int цвет(PlayerClassData.Класс класс) {
        return switch (класс) {
            case CLERIC -> 0xB8942F;      // тёплое золото
            case SMITH -> 0x8A4A1C;       // калёное железо
            case FARMER -> 0x4A6B2E;      // земля, урожай
            case CHRONICLER -> 0x4A3A7A;  // чернила, единственный лиловый в моде
            case NONE -> ЧЕРНИЛА_БЛЕДНЫЕ;
        };
    }

    /** Тот же цвет, но непрозрачным ARGB — для заливок. */
    public static int заливка(PlayerClassData.Класс класс) {
        return 0xFF000000 | (цвет(класс) & 0xFFFFFF);
    }

    private static final String[] РИМСКИЕ = { "I", "II", "III", "IV" };

    /** Римская цифра 1..4; вне диапазона — само число. */
    public static String римская(int номер) {
        return номер >= 1 && номер <= РИМСКИЕ.length ? РИМСКИЕ[номер - 1] : Integer.toString(номер);
    }

    /** Данные локального игрока; {@code null}, если игрока ещё нет. */
    public static PlayerClassData свои() {
        Player игрок = Minecraft.getInstance().player;
        return игрок == null ? null : PlayerClassData.данные(игрок);
    }

    /**
     * Сколько минут до возможности сменить класс; 0 — можно сейчас.
     * Считается на клиенте: вложение синкается с 0.6.0, значит и тик
     * последней смены, и мировое время у клиента уже есть.
     */
    public static long минутДоСмены() {
        PlayerClassData д = свои();
        Minecraft mc = Minecraft.getInstance();
        if (д == null || mc.level == null) return 0;
        return ClassSwitch.минутОсталось(ClassSwitch.осталосьТиков(
            д.последняяСменаТик, mc.level.getGameTime(), ClassesConfig.кулдаунСменыТики()));
    }

    /**
     * Полоса мастерства с засечками на порогах тиров. Засечки —
     * не украшение: без них «73 из 100» ничего не говорит о том,
     * далеко ли до следующего тира.
     */
    public static void полосаМастерства(
            GuiGraphics графика, int x, int y, int ширина, int мастерство, int цветАкцента) {
        int высота = 5;
        int заполнено = Math.max(0, Math.min(ширина,
            ширина * Math.min(ClassMastery.МАКСИМУМ, мастерство) / ClassMastery.МАКСИМУМ));

        графика.fill(x, y, x + ширина, y + высота, 0xFF6B5B3A);
        графика.fill(x, y, x + заполнено, y + высота, 0xFF000000 | (цветАкцента & 0xFFFFFF));

        засечка(графика, x, y, ширина, высота, ClassesConfig.порогТира2());
        засечка(графика, x, y, ширина, высота, ClassesConfig.порогТира3());
    }

    private static void засечка(GuiGraphics графика, int x, int y, int ширина, int высота, int порог) {
        if (порог <= 0 || порог >= ClassMastery.МАКСИМУМ) return;
        int сдвиг = ширина * порог / ClassMastery.МАКСИМУМ;
        графика.fill(x + сдвиг, y - 1, x + сдвиг + 1, y + высота + 1, 0xFF3A2E1C);
    }
}
