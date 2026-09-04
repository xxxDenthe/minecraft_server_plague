package dev.denthe.shade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShadeColorTest {

    @Test
    void hexСРешёткойИБезОдинаково() {
        float[] fb = { 9f, 9f, 9f };
        float[] expected = { 0f, 128f / 255f, 1f };
        assertArrayEquals(expected, ShadeColor.hexToRgb("#0080FF", fb), 1e-6f);
        assertArrayEquals(expected, ShadeColor.hexToRgb("0080ff", fb), 1e-6f);
        assertArrayEquals(expected, ShadeColor.hexToRgb("  0080FF  ", fb), 1e-6f);
    }

    @Test
    void мусорВозвращаетТотЖеЗапас() {
        float[] fb = { 0.1f, 0.2f, 0.3f };
        assertSame(fb, ShadeColor.hexToRgb(null, fb));
        assertSame(fb, ShadeColor.hexToRgb("нет", fb));
        assertSame(fb, ShadeColor.hexToRgb("12345", fb));   // не 6 символов
        assertSame(fb, ShadeColor.hexToRgb("gggggg", fb));  // не hex
    }
}
