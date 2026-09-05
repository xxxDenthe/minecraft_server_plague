package dev.denthe.gmtools.client;

import java.lang.reflect.Method;

/**
 * Мост к голосу больного из мода {@code plaguecore} через рефлексию его
 * класса {@code dev.denthe.plaguecore.client.PlagueVoiceApi}. Зависимости
 * между джарами нет: нет мода — {@link #available()} вернёт false,
 * папка «Голос» покажет заглушку.
 *
 * Читать можно только текущие значения. Меняются они командой
 * {@code /plague voice set}: голос считается на сервере, и клиентские
 * числа ему не указ.
 */
final class VoiceAccess {
    private VoiceAccess() {}

    private static boolean tried;
    private static Method mIds, mLabel, mLevel, mMinStage, mMin, mMax, mIsInt, mGet, mSynced;

    private static void load() {
        if (tried) return;
        tried = true;
        try {
            Class<?> api = Class.forName("dev.denthe.plaguecore.client.PlagueVoiceApi");
            mIds      = api.getMethod("ids");
            mLabel    = api.getMethod("label", String.class);
            mLevel    = api.getMethod("level", String.class);
            mMinStage = api.getMethod("minStage");
            mMin      = api.getMethod("min", String.class);
            mMax      = api.getMethod("max", String.class);
            mIsInt    = api.getMethod("isInt", String.class);
            mGet      = api.getMethod("get", String.class);
            mSynced   = api.getMethod("synced");
        } catch (ReflectiveOperationException e) {
            mIds = null;
        }
    }

    static boolean available() {
        load();
        return mIds != null;
    }

    static String[] ids()          { Object r = call(mIds); return r == null ? new String[0] : (String[]) r; }
    static String label(String id) { Object r = call(mLabel, id); return r == null ? id : (String) r; }
    static int level(String id)    { Object r = call(mLevel, id); return r == null ? -1 : (int) r; }
    static int minStage()          { Object r = call(mMinStage); return r == null ? 3 : (int) r; }
    static double min(String id)   { Object r = call(mMin, id); return r == null ? 0 : (double) r; }
    static double max(String id)   { Object r = call(mMax, id); return r == null ? 1 : (double) r; }
    static boolean isInt(String id) { return Boolean.TRUE.equals(call(mIsInt, id)); }
    static double get(String id)   { Object r = call(mGet, id); return r == null ? 0 : (double) r; }
    static boolean synced()        { return Boolean.TRUE.equals(call(mSynced)); }

    private static Object call(Method m, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
