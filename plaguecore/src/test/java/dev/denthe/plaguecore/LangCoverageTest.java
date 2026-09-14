package dev.denthe.plaguecore;

import dev.denthe.plaguecore.core.Wellbeing;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Каждый ключ, который умеет выдать Wellbeing, обязан быть в обоих
 * языковых файлах. Без этого проверки игрок увидит голый ключ вида
 * plaguecore.health.body.arms.3 — и это худшее, что может случиться
 * с интерфейсом, который весь состоит из текста.
 *
 * Файлы читаются как текст, а не разбираются JSON-библиотекой: тянуть
 * зависимость ради поиска подстроки незачем.
 */
class LangCoverageTest {

    private static final Path RU =
        Path.of("src/main/resources/assets/plaguecore/lang/ru_ru.json");
    private static final Path EN =
        Path.of("src/main/resources/assets/plaguecore/lang/en_us.json");

    @Test
    void всеКлючиЕстьВОбоихЯзыках() throws IOException {
        String ru = Files.readString(RU, StandardCharsets.UTF_8);
        String en = Files.readString(EN, StandardCharsets.UTF_8);

        List<String> пропущены = new ArrayList<>();
        for (String ключ : всеКлючи()) {
            if (!ru.contains('"' + ключ + '"')) пропущены.add("ru: " + ключ);
            if (!en.contains('"' + ключ + '"')) пропущены.add("en: " + ключ);
        }
        assertTrue(пропущены.isEmpty(), "нет перевода: " + пропущены);
    }

    private static List<String> всеКлючи() {
        List<String> ключи = new ArrayList<>();
        for (int с = 0; с < Wellbeing.СТУПЕНЕЙ; с++) {
            ключи.add(Wellbeing.общее(с));
            ключи.add(Wellbeing.общееКратко(с));
            ключи.add(Wellbeing.чужоеОбщее(с));
            for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
                ключи.add(Wellbeing.часть(часть, с));
                ключи.add(Wellbeing.чужаяЧасть(часть, с));
            }
        }
        for (int ч = 0; ч <= 20; ч++) {
            ключи.add(Wellbeing.голод(ч));
            ключи.add(Wellbeing.жажда(ч));
        }
        for (String идентификатор : Wellbeing.идентификаторыОщущений()) {
            ключи.add(Wellbeing.ощущение(идентификатор));
        }
        ключи.add("plaguecore.health.title");
        ключи.add("plaguecore.health.tab.state");
        ключи.add("plaguecore.health.tab.body");
        ключи.add("plaguecore.health.tab.feel");
        ключи.add("plaguecore.health.tab.memory");
        ключи.add("plaguecore.health.hint.pick");
        ключи.add("plaguecore.health.feel.none");
        ключи.add("plaguecore.health.memory.none");
        ключи.add("key.plaguecore.health");
        ключи.add("key.categories.plaguecore");
        return ключи;
    }
}
