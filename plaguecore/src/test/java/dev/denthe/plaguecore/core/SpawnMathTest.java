package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевод шанса за ночь в шанс за тик — место, где легко ошибиться
 * на порядок и заметить это только на живом сервере, когда из каждого
 * мешка полезет толпа.
 */
class SpawnMathTest {

    @Test
    void шансЗаТикСкладываетсяОбратноВШансЗаНочь() {
        for (float заНочь : new float[] { 0.05f, 0.30f, 0.5f, 0.9f }) {
            float заТик = SpawnMath.шансЗаТик(заНочь);
            assertEquals(заНочь, SpawnMath.заНочь(заТик), 1e-4,
                "шанс за ночь " + заНочь + " не сошёлся обратно");
        }
    }

    @Test
    void тридцатьПроцентовЗаНочьЭтоОколоПятиЗаТик() {
        float заТик = SpawnMath.шансЗаТик(0.30f);
        assertTrue(заТик > 0.04f && заТик < 0.06f,
            "ожидалось около 5% за тик, вышло " + заТик);
    }

    @Test
    void краяНеЛомаются() {
        assertEquals(0f, SpawnMath.шансЗаТик(0f));
        assertEquals(0f, SpawnMath.шансЗаТик(-1f));
        assertEquals(1f, SpawnMath.шансЗаТик(1f));
        assertEquals(1f, SpawnMath.шансЗаТик(2f));
    }

    /** Блок получает за ночь около семи случайных тиков, а не один и не сто. */
    @Test
    void тиковЗаНочьОколоСеми() {
        assertTrue(SpawnMath.ТИКОВ_ЗА_НОЧЬ > 6.0 && SpawnMath.ТИКОВ_ЗА_НОЧЬ < 8.0,
            "тиков за ночь вышло " + SpawnMath.ТИКОВ_ЗА_НОЧЬ);
    }
}
