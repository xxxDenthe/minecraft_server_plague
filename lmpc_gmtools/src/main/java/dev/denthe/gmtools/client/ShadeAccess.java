package dev.denthe.gmtools.client;

import java.lang.reflect.Method;

/**
 * Мост к цветокору мода {@code lmpc_shade} через рефлексию его класса
 * {@code dev.denthe.shade.ShadeApi}. Зависимости между джарами нет: если
 * мода в паке нет — {@link #available()} вернёт false, раздел «Графика»
 * покажет заглушку.
 */
final class ShadeAccess {
    private ShadeAccess() {}

    private static boolean tried;
    private static Method mIds, mGroup, mLabel, mKind, mMin, mMax, mLive, mGet, mSet, mSave, mReset;

    private static void load() {
        if (tried) return;
        tried = true;
        try {
            Class<?> api = Class.forName("dev.denthe.shade.ShadeApi");
            mIds   = api.getMethod("ids");
            mGroup = api.getMethod("group", String.class);
            mLabel = api.getMethod("label", String.class);
            mKind  = api.getMethod("kind", String.class);
            mMin   = api.getMethod("min", String.class);
            mMax   = api.getMethod("max", String.class);
            mLive  = api.getMethod("live", String.class);
            mGet   = api.getMethod("get", String.class);
            mSet   = api.getMethod("set", String.class, Object.class);
            mSave  = api.getMethod("save");
            mReset = api.getMethod("resetAll");
        } catch (ReflectiveOperationException e) {
            mIds = null;
        }
    }

    static boolean available() {
        load();
        return mIds != null;
    }

    static String[] ids()              { Object r = call(mIds); return r == null ? new String[0] : (String[]) r; }
    static String group(String id)     { return (String) call(mGroup, id); }
    static String label(String id)     { return (String) call(mLabel, id); }
    static String kind(String id)      { return (String) call(mKind, id); }
    static double min(String id)       { Object r = call(mMin, id); return r == null ? 0 : (double) r; }
    static double max(String id)       { Object r = call(mMax, id); return r == null ? 1 : (double) r; }
    static boolean live(String id)     { return Boolean.TRUE.equals(call(mLive, id)); }
    static Object get(String id)       { return call(mGet, id); }
    static void set(String id, Object v) { call(mSet, id, v); }
    static void save()                 { call(mSave); }
    static void resetAll()             { call(mReset); }

    private static Object call(Method m, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
