package dev.denthe.shade;

/**
 * Разбор hex-цвета из конфига в нормализованный RGB для юниформов шейдера.
 * Отдельно и без Minecraft — чтобы тестировалось обычным JUnit.
 */
public final class ShadeColor {
    private ShadeColor() {}

    /**
     * "26252A" или "#26252A" → {0.149f, 0.145f, 0.165f}.
     * На мусор (null, не 6 hex-символов) возвращает переданный запас.
     */
    public static float[] hexToRgb(String hex, float[] fallback) {
        if (hex == null) return fallback;
        String s = hex.strip();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return fallback;
        try {
            int v = Integer.parseInt(s, 16);
            return new float[] {
                ((v >> 16) & 0xFF) / 255f,
                ((v >> 8) & 0xFF) / 255f,
                (v & 0xFF) / 255f
            };
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
