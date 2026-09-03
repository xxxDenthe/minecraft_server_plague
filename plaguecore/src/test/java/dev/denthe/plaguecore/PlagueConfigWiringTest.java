package dev.denthe.plaguecore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Сторож проводки конфига. Сам конфиг обычным JUnit не проверить — он
 * тянет NeoForge, — поэтому проверяем то, что ломается на деле: завели
 * новую ручку в PlagueConstants и забыли вывести её в файл.
 *
 * Договор простой: нефинальное поле PlagueConstants — это ручка, и она
 * обязана переписываться из PlagueConfig. Финальное поле — устройство
 * мода, его в конфиге быть не должно.
 */
class PlagueConfigWiringTest {

    private static final Path ИСХОДНИК =
        Paths.get("src/main/java/dev/denthe/plaguecore/PlagueConfig.java");

    @Test
    void каждаяРучкаПереписываетсяИзКонфига() throws IOException {
        String конфиг = Files.readString(ИСХОДНИК, StandardCharsets.UTF_8);

        List<String> забытые = new ArrayList<>();
        for (Field поле : PlagueConstants.class.getDeclaredFields()) {
            int мод = поле.getModifiers();
            if (!Modifier.isStatic(мод) || Modifier.isFinal(мод)) continue;
            if (!конфиг.contains("PlagueConstants." + поле.getName() + " =")) {
                забытые.add(поле.getName());
            }
        }

        assertTrue(забытые.isEmpty(),
            "ручки заведены в PlagueConstants, но не читаются из конфига: " + забытые
            + "\nлибо допиши их в PlagueConfig, либо сделай поле final");
    }

    @Test
    void ручекДолжноБытьНеМеньшеДесятка() {
        long ручек = java.util.Arrays.stream(PlagueConstants.class.getDeclaredFields())
            .filter(п -> Modifier.isStatic(п.getModifiers()))
            .filter(п -> !Modifier.isFinal(п.getModifiers()))
            .count();
        assertTrue(ручек >= 10, "ручек всего " + ручек + " — конфиг явно недособран");
    }
}
