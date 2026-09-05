package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
    private static Method методGetChunkLevel;
    private static Method методGetNight;
    private static Method методCleanseChunk;
    private static Method методRecordSnapshot;
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
            // Чанки (0.7.0): грядка Фермера, Очиститель Кузнеца, снимок Летописца.
            методGetChunkLevel = необязательный(api, "getChunkLevel",
                ServerLevel.class, int.class, int.class);
            методGetNight = необязательный(api, "getNight", ServerLevel.class);
            методCleanseChunk = необязательный(api, "cleanseChunk",
                ServerLevel.class, int.class, int.class, float.class, float.class);
            методRecordSnapshot = необязательный(api, "recordSnapshot",
                ServerPlayer.class, BlockPos.class);
        } catch (ReflectiveOperationException e) {
            доступен = false;
        }
    }

    /**
     * Метод, которого может не быть у соседа старой версии. Отсутствие —
     * не поломка: отвалится ровно одна способность, а не весь мод.
     */
    private static Method необязательный(Class<?> api, String имя, Class<?>... типы) {
        try {
            return api.getMethod(имя, типы);
        } catch (ReflectiveOperationException e) {
            return null;
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

    // ── чанки ─────────────────────────────────────────────────────────

    /**
     * Уровень заражения чанка 0..5; {@code -1}, если чанк вне сетки мира
     * или `plaguecore` недоступен. Отличать «чисто» от «не знаю» здесь
     * важно: дикий бутон растёт только в заражённом чанке, и при
     * неизвестном уровне он не должен выпадать где попало.
     */
    public static int уровеньЧанка(ServerLevel уровень, int чанкX, int чанкZ) {
        инициализировать();
        if (!доступен || методGetChunkLevel == null) return -1;
        try {
            Object значение = методGetChunkLevel.invoke(null, уровень, чанкX, чанкZ);
            return значение instanceof Number число ? число.intValue() : -1;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** То же по позиции блока. */
    public static int уровеньЧанкаВ(ServerLevel уровень, BlockPos позиция) {
        return уровеньЧанка(уровень, позиция.getX() >> 4, позиция.getZ() >> 4);
    }

    /** Номер прошедшей ночи; {@code -1}, если `plaguecore` недоступен. */
    public static int ночь(ServerLevel уровень) {
        инициализировать();
        if (!доступен || методGetNight == null) return -1;
        try {
            Object значение = методGetNight.invoke(null, уровень);
            return значение instanceof Number число ? число.intValue() : -1;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /**
     * Ночной проход очистителя по чанку: поднять сопротивление и,
     * с вероятностью {@code сила}, снять один уровень заражения.
     * Правило «нельзя окопаться» (спек ядра 10.2) применяется внутри
     * `plaguecore`, а не здесь — оно про баланс эпидемии.
     *
     * @return true, если уровень действительно снизился
     */
    public static boolean очиститьЧанк(
            ServerLevel уровень, int чанкX, int чанкZ, float сила, float приростСопротивления) {
        инициализировать();
        if (!доступен || методCleanseChunk == null) return false;
        try {
            Object значение = методCleanseChunk.invoke(
                null, уровень, чанкX, чанкZ, сила, приростСопротивления);
            return значение instanceof Boolean флаг && флаг;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Крючок под подсистему 5: снимок Летописца как будущий лор-артефакт. */
    public static void записатьСнимок(ServerPlayer игрок, BlockPos позиция) {
        инициализировать();
        if (!доступен || методRecordSnapshot == null) return;
        try {
            методRecordSnapshot.invoke(null, игрок, позиция);
        } catch (ReflectiveOperationException e) {
            // молчим — лор ещё не подъехал, терять нечего
        }
    }
}
