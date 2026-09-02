package dev.denthe.plaguecore.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CorePurityTest {

    @Test
    void coreНеЗависитОтMinecraft() throws IOException {
        Path coreDir = Paths.get("src/main/java/dev/denthe/plaguecore/core");
        assertTrue(Files.isDirectory(coreDir), "пакет core должен существовать: " + coreDir.toAbsolutePath());

        List<String> нарушения = new ArrayList<>();
        try (Stream<Path> files = Files.walk(coreDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        String t = line.trim();
                        if (t.startsWith("import net.minecraft") || t.startsWith("import net.neoforged")) {
                            нарушения.add(p.getFileName() + ": " + t);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(нарушения.isEmpty(),
            "в пакете core запрещены импорты Minecraft/NeoForge:\n" + String.join("\n", нарушения));
    }
}
