package dev.denthe.classes;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Мост к `plaguecore`. Только рефлексия на публичные члены — Gradle-
 * зависимости между джарами нет и не будет. Любая ошибка отражения —
 * тихий отказ, не краш: без `plaguecore` (или при несовпавшей версии)
 * эти вызовы просто ничего не делают.
 */
public final class PlagueBridge {
    private PlagueBridge() {}

    private static final String ПАКЕТ = "dev.denthe.plaguecore.mc";

    private static Method методДанные;       // PlayerPlagueData.данные(Player)
    private static Method методЗадать;       // PlayerInfection.задать(ServerPlayer, float)
    private static Field полеЗаражённость;   // PlayerPlagueData.заражённость
    private static Field полеИммунитетДо;    // PlayerPlagueData.иммунитетДо
    private static boolean инициализирован;
    private static boolean доступен;

    private static synchronized void инициализировать() {
        if (инициализирован) return;
        инициализирован = true;
        try {
            Class<?> данныеКласс = Class.forName(ПАКЕТ + ".PlayerPlagueData");
            Class<?> infectionКласс = Class.forName(ПАКЕТ + ".PlayerInfection");
            методДанные = данныеКласс.getMethod("данные", net.minecraft.world.entity.player.Player.class);
            методЗадать = infectionКласс.getMethod("задать", ServerPlayer.class, float.class);
            полеЗаражённость = данныеКласс.getField("заражённость");
            полеИммунитетДо = данныеКласс.getField("иммунитетДо");
            доступен = true;
        } catch (ReflectiveOperationException e) {
            доступен = false;
        }
    }

    /** Есть ли plaguecore на сервере. */
    public static boolean доступен() {
        инициализировать();
        return доступен;
    }

    /** Текущая заражённость игрока, −1 если plaguecore недоступен. */
    public static float заражённость(ServerPlayer игрок) {
        инициализировать();
        if (!доступен) return -1f;
        try {
            Object данные = методДанные.invoke(null, игрок);
            return полеЗаражённость.getFloat(данные);
        } catch (ReflectiveOperationException e) {
            return -1f;
        }
    }

    /** Снять {@code amount} очков заражённости. */
    public static void cure(ServerPlayer игрок, float amount) {
        инициализировать();
        if (!доступен) return;
        try {
            float текущая = полеЗаражённость.getFloat(методДанные.invoke(null, игрок));
            методЗадать.invoke(null, игрок, Math.max(0f, текущая - amount));
        } catch (ReflectiveOperationException e) {
            // молчим — без plaguecore лечить нечего
        }
    }

    /** Дать иммунитет к набору заразы на {@code ticks} тиков вперёд. */
    public static void grantImmunity(ServerPlayer игрок, long ticks) {
        инициализировать();
        if (!доступен) return;
        try {
            Object данные = методДанные.invoke(null, игрок);
            long сейчас = игрок.level().getGameTime();
            полеИммунитетДо.setLong(данные, сейчас + ticks);
        } catch (ReflectiveOperationException e) {
            // молчим
        }
    }
}
