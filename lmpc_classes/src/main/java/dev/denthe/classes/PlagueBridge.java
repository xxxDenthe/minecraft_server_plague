package dev.denthe.classes;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Мост к `plaguecore`. Только рефлексия на публичные члены — Gradle-
 * зависимости между джарами нет и не будет. Любая ошибка отражения —
 * тихий отказ, не краш: без `plaguecore` (или при несовпавшей версии)
 * эти вызовы просто ничего не делают.
 *
 * С 0.4.0 — рефлексия на {@code dev.denthe.plaguecore.mc.PlagueApi},
 * официальную точку входа для подсистемы классов (спек ядра,
 * раздел 9.6). Раньше сюда лезли напрямую в {@code PlayerPlagueData}/
 * {@code PlayerInfection.задать} — тот способ тоже работал, но
 * `PlagueApi` появился именно для нас, и его контракт устойчивее:
 * `cure` сам читает текущую заражённость (не нужно тащить её через
 * отдельное поле), `grantImmunity` не укорачивает уже идущий иммунитет.
 *
 * С 0.6.0 добавлено чтение — {@code getStage}/{@code getInfection}
 * для обзора Летописца. Методы необязательные по отдельности: если
 * сосед их однажды переименует, отвар Клирика от этого не отвалится,
 * пропадёт только показ чисел.
 */
public final class PlagueBridge {
    private PlagueBridge() {}

    private static final String КЛАСС_API = "dev.denthe.plaguecore.mc.PlagueApi";

    /** Ответ, когда `plaguecore` не отвечает: «числа неизвестны», не «ноль». */
    public static final float НЕТ_ДАННЫХ = -1f;

    private static Method методCure;
    private static Method методGrantImmunity;
    private static Method методGetStage;
    private static Method методGetInfection;
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> api = Class.forName(КЛАСС_API);
            методCure = api.getMethod("cure", ServerPlayer.class, float.class);
            методGrantImmunity = api.getMethod("grantImmunity", ServerPlayer.class, int.class);
            доступен = true;
            // Чтение — отдельно и необязательно: без него живут все классы, кроме Летописца.
            методGetStage = метод(api, "getStage");
            методGetInfection = метод(api, "getInfection");
        } catch (ReflectiveOperationException e) {
            доступен = false;
        }
    }

    private static Method метод(Class<?> api, String имя) {
        try {
            return api.getMethod(имя, ServerPlayer.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Есть ли plaguecore на сервере. */
    public static boolean доступен() {
        инициализировать();
        return доступен;
    }

    /** Снять {@code amount} очков заражённости. */
    public static void cure(ServerPlayer игрок, float amount) {
        инициализировать();
        if (!доступен) return;
        try {
            методCure.invoke(null, игрок, amount);
        } catch (ReflectiveOperationException e) {
            // молчим — без plaguecore лечить нечего
        }
    }

    /** Дать иммунитет к набору заразы на {@code ticks} тиков вперёд. */
    public static void grantImmunity(ServerPlayer игрок, long ticks) {
        инициализировать();
        if (!доступен) return;
        try {
            методGrantImmunity.invoke(null, игрок, (int) Math.min(Integer.MAX_VALUE, ticks));
        } catch (ReflectiveOperationException e) {
            // молчим
        }
    }

    /** Стадия чумы игрока; {@code -1}, если `plaguecore` недоступен. */
    public static int стадия(ServerPlayer игрок) {
        float значение = число(методGetStage, игрок);
        return значение == НЕТ_ДАННЫХ ? -1 : (int) значение;
    }

    /** Заражённость игрока в очках; {@link #НЕТ_ДАННЫХ}, если недоступна. */
    public static float заражённость(ServerPlayer игрок) {
        return число(методGetInfection, игрок);
    }

    private static float число(Method метод, ServerPlayer игрок) {
        инициализировать();
        if (!доступен || метод == null) return НЕТ_ДАННЫХ;
        try {
            Object значение = метод.invoke(null, игрок);
            return значение instanceof Number число ? число.floatValue() : НЕТ_ДАННЫХ;
        } catch (ReflectiveOperationException e) {
            return НЕТ_ДАННЫХ;
        }
    }
}
