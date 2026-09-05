package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.VoiceKnobs;

/**
 * Мост наружу для панели мастера игры (`lmpc_gmtools`): она читает эти
 * методы рефлексией, зависимости между джарами нет. Отсюда — плоские
 * латинские имена и примитивы: рефлексия по записям с русскими
 * методами читалась бы вдвое хуже.
 *
 * Значения берутся из снимка, присланного сервером после
 * `/plague voice sync`. Пока снимка нет — отдаём то, что стоит
 * в конфиге самого клиента: лучше показать что-то близкое, чем нули.
 *
 * **Имена методов не менять**: их ищет чужой мод по строке.
 */
public final class PlagueVoiceApi {
    private PlagueVoiceApi() {}

    public static String[] ids() {
        String[] с = new String[VoiceKnobs.ВСЕ.length];
        for (int i = 0; i < с.length; i++) с[i] = VoiceKnobs.ВСЕ[i].id();
        return с;
    }

    public static String label(String id) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        return р == null ? id : р.подпись();
    }

    /** −1 — общая ручка, иначе индекс уровня силы (0 — первая испорченная стадия). */
    public static int level(String id) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        return р == null ? -1 : р.уровень();
    }

    /** С какой стадии голос портится: панели надо подписать уровни. */
    public static int minStage() {
        return (int) Math.round(get("minStage"));
    }

    public static double min(String id) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        return р == null ? 0 : р.минимум();
    }

    public static double max(String id) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        return р == null ? 1 : р.максимум();
    }

    public static boolean isInt(String id) {
        VoiceKnobs.Ручка р = VoiceKnobs.найти(id);
        return р != null && р.целое();
    }

    /** Значение с сервера, если он его прислал; иначе местное. */
    public static double get(String id) {
        float[] снимок = PlagueClientAccess.голос();
        for (int i = 0; i < VoiceKnobs.ВСЕ.length; i++) {
            if (VoiceKnobs.ВСЕ[i].id().equals(id)) {
                return i < снимок.length ? снимок[i] : VoiceKnobs.прочитать(id);
            }
        }
        return 0;
    }

    /** Пришёл ли снимок с сервера. Панель этим подписывает, чьи числа показывает. */
    public static boolean synced() {
        return PlagueClientAccess.голос().length == VoiceKnobs.ВСЕ.length;
    }
}
