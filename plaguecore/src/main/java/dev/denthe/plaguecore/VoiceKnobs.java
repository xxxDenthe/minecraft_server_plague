package dev.denthe.plaguecore;

/**
 * Ручки голоса больного одним списком: id, подпись, границы.
 *
 * Список один на всех — его читают конфиг, команда `/plague voice`,
 * пакет синхронизации и панель мастера игры. Добавил ручку сюда —
 * она сама появилась везде.
 *
 * Чистая Java: ни Minecraft, ни NeoForge. Значения живут
 * в {@link PlagueConstants}, файл на диске ведёт {@code PlagueConfig}.
 */
public final class VoiceKnobs {
    private VoiceKnobs() {}

    /**
     * @param id        имя для команды и конфига
     * @param подпись   что это по-русски, для панели GM
     * @param уровень   −1 для общих, иначе индекс уровня силы
     * @param минимум   границы обязаны совпадать с PlagueConfig
     */
    public record Ручка(String id, String подпись, int уровень,
                        double минимум, double максимум, boolean целое) {}

    public static final Ручка[] ВСЕ = собрать();

    private static Ручка[] собрать() {
        int у = PlagueConstants.VOICE_LEVELS;
        Ручка[] всё = new Ручка[2 + 5 * у];
        всё[0] = new Ручка("minStage", "С какой стадии портить", -1, 1, 4, true);
        всё[1] = new Ручка("tremorHz", "Частота дрожи, Гц", -1, 0.5, 15.0, false);
        for (int у2 = 0; у2 < у; у2++) {
            int б = 2 + у2 * 5;
            int н = у2 + 1;
            всё[б]     = new Ручка("semitones" + н, "Ниже на, полутонов", у2, 0.0, 8.0, false);
            всё[б + 1] = new Ручка("muffle" + н,    "Глухота (меньше — глуше)", у2, 0.05, 1.0, false);
            всё[б + 2] = new Ручка("rasp" + н,      "Хрип", у2, 1.0, 8.0, false);
            всё[б + 3] = new Ручка("breath" + н,    "Дыхание", у2, 0.0, 1.5, false);
            всё[б + 4] = new Ручка("tremor" + н,    "Дрожь", у2, 0.0, 0.8, false);
        }
        return всё;
    }

    public static Ручка найти(String id) {
        for (Ручка р : ВСЕ) if (р.id().equals(id)) return р;
        return null;
    }

    /** Текущее значение ручки из {@link PlagueConstants}. */
    public static double прочитать(String id) {
        Ручка р = найти(id);
        if (р == null) return 0;
        int у = р.уровень();
        return switch (базовое(id)) {
            case "minStage"  -> PlagueConstants.VOICE_MIN_STAGE;
            case "tremorHz"  -> PlagueConstants.VOICE_TREMOR_HZ;
            case "semitones" -> PlagueConstants.VOICE_SEMITONES[у];
            case "muffle"    -> PlagueConstants.VOICE_MUFFLE[у];
            case "rasp"      -> PlagueConstants.VOICE_RASP[у];
            case "breath"    -> PlagueConstants.VOICE_BREATH[у];
            case "tremor"    -> PlagueConstants.VOICE_TREMOR[у];
            default -> 0;
        };
    }

    /**
     * Записать значение в {@link PlagueConstants}, зажав в границы.
     *
     * Файл этим не трогается: за файл отвечает PlagueConfig. Нужно,
     * чтобы правка звучала в ту же секунду, не дожидаясь перечитывания.
     */
    public static void записать(String id, double значение) {
        Ручка р = найти(id);
        if (р == null) return;
        double v = Math.max(р.минимум(), Math.min(р.максимум(), значение));
        int у = р.уровень();
        switch (базовое(id)) {
            case "minStage"  -> PlagueConstants.VOICE_MIN_STAGE = (int) Math.round(v);
            case "tremorHz"  -> PlagueConstants.VOICE_TREMOR_HZ = (float) v;
            case "semitones" -> PlagueConstants.VOICE_SEMITONES[у] = (float) v;
            case "muffle"    -> PlagueConstants.VOICE_MUFFLE[у] = (float) v;
            case "rasp"      -> PlagueConstants.VOICE_RASP[у] = (float) v;
            case "breath"    -> PlagueConstants.VOICE_BREATH[у] = (float) v;
            case "tremor"    -> PlagueConstants.VOICE_TREMOR[у] = (float) v;
            default -> { }
        }
    }

    /** Снимок всех ручек в порядке {@link #ВСЕ} — им ходит пакет синхронизации. */
    public static float[] снимок() {
        float[] с = new float[ВСЕ.length];
        for (int i = 0; i < ВСЕ.length; i++) с[i] = (float) прочитать(ВСЕ[i].id());
        return с;
    }

    /** «semitones2» → «semitones». Номер уровня уже разобран в ручке. */
    private static String базовое(String id) {
        int i = id.length();
        while (i > 0 && Character.isDigit(id.charAt(i - 1))) i--;
        return id.substring(0, i);
    }
}
