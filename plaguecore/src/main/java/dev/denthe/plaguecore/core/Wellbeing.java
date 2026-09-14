package dev.denthe.plaguecore.core;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Что игрок чувствует и какими словами он об этом думает.
 * Спек интерфейса «Состояние здоровья», раздел 6.
 *
 * Класс не знает ни о Minecraft, ни о текстах: наружу уходят только
 * ключи локализации, а сами слова лежат в языковых файлах. Поэтому
 * вся система текстов проверяется обычным JUnit, без запуска игры,
 * и живёт под {@code CorePurityTest}.
 *
 * Главное правило спека: ни одно число болезни наружу не выходит.
 * Ступень самочувствия совпадает со стадией один в один намеренно —
 * своя шкала поверх серверной была бы второй механикой, а вторую
 * механику болезни заводить запрещено.
 */
public final class Wellbeing {
    private Wellbeing() {}

    /** Ступеней самочувствия: здоров, слегка нехорошо, нехорошо, плохо, край. */
    public static final int СТУПЕНЕЙ = 5;

    /** Общий корень всех ключей. Держит их в одном месте языкового файла. */
    private static final String КОРЕНЬ = "plaguecore.health.";

    /**
     * Части тела, у которых свой текст. Рук и ног по две, но говорят
     * они одно и то же: болезнь стороны не различает, а притворяться,
     * что различает, значит врать игроку.
     */
    public enum Часть { HEAD, TORSO, ARMS, LEGS }

    /** Ступень самочувствия по стадии болезни. Мусор прижимается к краям. */
    public static int ступень(int стадия) {
        if (стадия < 0) return 0;
        return Math.min(стадия, СТУПЕНЕЙ - 1);
    }

    /** Общее самочувствие на экране — одна-две фразы от первого лица. */
    public static String общее(int ступень) {
        return КОРЕНЬ + "overall." + ступень(ступень);
    }

    /** То же короче, для HUD: там длинная фраза не читается. */
    public static String общееКратко(int ступень) {
        return КОРЕНЬ + "hud." + ступень(ступень);
    }

    /** Что человек чувствует в этой части тела. */
    public static String часть(Часть часть, int ступень) {
        return КОРЕНЬ + "body." + имя(часть) + "." + ступень(ступень);
    }

    /** Как сосед выглядит со стороны. Это наблюдение, а не ощущение. */
    public static String чужоеОбщее(int ступень) {
        return КОРЕНЬ + "other.overall." + ступень(ступень);
    }

    /** Как со стороны выглядит часть тела соседа. */
    public static String чужаяЧасть(Часть часть, int ступень) {
        return КОРЕНЬ + "other.body." + имя(часть) + "." + ступень(ступень);
    }

    /**
     * Что Клирик замечает сверх обычного взгляда. Это вторая строка
     * к описанию, а не замена ему.
     *
     * Никаких чисел: Клирик не диагност с прибором, он просто лучше
     * видит симптом. Стадию и заражённость ему не отдают так же, как
     * и всем остальным.
     */
    public static String клирикЧасть(Часть часть, int ступень, boolean чужой) {
        return КОРЕНЬ + "cleric." + (чужой ? "other." : "") + "body."
            + имя(часть) + "." + ступень(ступень);
    }

    /** Что Клирик замечает в человеке целиком. Только при чужом осмотре. */
    public static String клирикОбщее(int ступень) {
        return КОРЕНЬ + "cleric.other.overall." + ступень(ступень);
    }

    private static String имя(Часть часть) {
        return часть.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Голод — четыре ступени по ванильной сытости 0..20.
     *
     * Коэффициенты болезни сюда не попадают принципиально: игрок
     * чувствует, что есть хочется чаще, а не что стадия добавляет
     * 0.04 истощения в секунду.
     */
    public static String голод(int сытость) {
        return КОРЕНЬ + "hunger." + четверть(сытость);
    }

    /** Жажда — та же шкала 0..20, что у мода жажды. */
    public static String жажда(int уровень) {
        return КОРЕНЬ + "thirst." + четверть(уровень);
    }

    /** 0..20 → ступень 0..3. Мусор прижимается к краям. */
    private static int четверть(int значение) {
        if (значение <= 3) return 0;
        if (значение <= 10) return 1;
        if (значение <= 17) return 2;
        return 3;
    }

    /**
     * Действующий эффект человеческими словами.
     *
     * Незнакомый эффект молчит, а не показывает свой идентификатор:
     * лучше промолчать, чем вывалить игроку «somemod:quantum_flux».
     * Ни названия, ни уровня, ни длительности — всё это техническое.
     *
     * @return ключ локализации или {@code null}, если эффект незнаком
     */
    public static String ощущение(String идентификатор) {
        if (идентификатор == null) return null;
        String хвост = ОЩУЩЕНИЯ.get(идентификатор);
        return хвост == null ? null : КОРЕНЬ + "feel." + хвост;
    }

    /** Все идентификаторы эффектов, которым есть что сказать телом. */
    public static Set<String> идентификаторыОщущений() {
        return ОЩУЩЕНИЯ.keySet();
    }

    /**
     * Эффекты, которым есть что сказать телом. Список нарочно неполный:
     * сюда входит то, что действительно встречается в паке.
     */
    private static final Map<String, String> ОЩУЩЕНИЯ = Map.ofEntries(
        Map.entry("minecraft:weakness", "weakness"),
        Map.entry("minecraft:mining_fatigue", "mining_fatigue"),
        Map.entry("minecraft:slowness", "slowness"),
        Map.entry("minecraft:nausea", "nausea"),
        Map.entry("minecraft:hunger", "hunger"),
        Map.entry("minecraft:poison", "poison"),
        Map.entry("minecraft:wither", "wither"),
        Map.entry("minecraft:blindness", "blindness"),
        Map.entry("minecraft:darkness", "darkness"),
        Map.entry("minecraft:regeneration", "regeneration"),
        Map.entry("minecraft:speed", "speed"),
        Map.entry("minecraft:haste", "haste"),
        Map.entry("minecraft:strength", "strength"),
        Map.entry("minecraft:resistance", "resistance"),
        Map.entry("minecraft:fire_resistance", "fire_resistance"),
        Map.entry("minecraft:water_breathing", "water_breathing"),
        Map.entry("minecraft:night_vision", "night_vision"),
        Map.entry("minecraft:invisibility", "invisibility"),
        Map.entry("minecraft:jump_boost", "jump_boost"),
        Map.entry("minecraft:slow_falling", "slow_falling"),
        Map.entry("minecraft:absorption", "absorption"),
        Map.entry("minecraft:health_boost", "health_boost"),
        Map.entry("minecraft:saturation", "saturation"),
        Map.entry("minecraft:levitation", "levitation"),
        Map.entry("minecraft:glowing", "glowing"));
}
