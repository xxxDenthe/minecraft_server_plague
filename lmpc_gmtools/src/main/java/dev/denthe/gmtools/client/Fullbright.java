package dev.denthe.gmtools.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * «Ночное зрение» без зелья: поднимаем клиентскую яркость. Зелье не
 * годится — частицы эффекта выдали бы мастера.
 *
 * ponytail: ванильный сеттер gamma режет значение до 1.0, поэтому
 * пишем прямо в приватное поле OptionInstance через рефлексию. Если
 * поле однажды переименуют — тихо откатываемся на set(1.0), это всё
 * ещё заметно светлее обычного.
 */
public final class Fullbright {
    private Fullbright() {}

    private static final double BRIGHT = 15.0;
    private static boolean on;
    private static double saved;

    public static boolean isOn() {
        return on;
    }

    public static void toggle() {
        OptionInstance<Double> gamma = Minecraft.getInstance().options.gamma();
        if (on) {
            apply(gamma, saved);
            on = false;
        } else {
            saved = gamma.get();
            apply(gamma, BRIGHT);
            on = true;
        }
    }

    private static void apply(OptionInstance<Double> gamma, double value) {
        try {
            var f = OptionInstance.class.getDeclaredField("value");
            f.setAccessible(true);
            f.set(gamma, value);
        } catch (Exception e) {
            // поле переименовали или модули не пускают рефлексию — хотя бы «ярко»
            gamma.set(Math.min(1.0, value));
        }
    }
}
