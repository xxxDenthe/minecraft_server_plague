package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.core.DreadMath;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Каталог шорохов: чем именно мир скрипит за спиной.
 * Спек `2026-09-17-horror-rezhissyor-design.md`, раздел 2.
 *
 * Звуки ванильные, с переписанным питчем, плюс собственный кашель мода.
 * Своих сэмплов не пишем: каждый файл — это вес в раздаче пака, а восемь
 * человек за четыре дня не успеют выучить ванильную библиотеку шорохов
 * настолько, чтобы отличить её от специально записанной.
 *
 * Пакет уходит лично игроку, а не через `level.playSound`: соседи не
 * должны слышать ничего. Весь смысл в ответе «ты слышал? — нет».
 */
public final class DreadCatalog {
    private DreadCatalog() {}

    /** Один шорох: звук, громкость и питч. */
    private record Шорох(SoundEvent звук, float громкость, float питч) {}

    /**
     * Пять поводов обернуться. Все — обычные звуки живого мира, поэтому
     * человек сначала решает, что ему показалось, и только потом
     * начинает проверять, показалось ли.
     */
    private static final Шорох[] ШОРОХИ = {
        new Шорох(SoundEvents.GRAVEL_STEP, 0.7f, 1.0f),
        new Шорох(SoundEvents.STONE_STEP, 0.7f, 1.0f),
        new Шорох(SoundEvents.WOODEN_DOOR_OPEN, 0.5f, 0.6f),
        new Шорох(SoundEvents.STONE_BREAK, 0.6f, 0.8f),
        new Шорох(SoundEvents.ZOMBIE_AMBIENT, 0.4f, 0.5f),
    };

    /** Ближняя и дальняя граница расстояния до источника. */
    private static final float БЛИЖЕ = 3f;
    private static final float ДАЛЬШЕ = 6f;

    /** Доля шорохов, которая достаётся чужому кашлю. */
    private static final float ДОЛЯ_КАШЛЯ = 0.25f;

    /** Проиграть один случайный шорох за спиной игрока. Слышит только он. */
    public static void шорох(ServerPlayer игрок, RandomSource случай) {
        if (случай.nextFloat() < ДОЛЯ_КАШЛЯ) {
            кашель(игрок, случай);
            return;
        }
        Шорох ш = ШОРОХИ[случай.nextInt(ШОРОХИ.length)];
        float дальность = БЛИЖЕ + случай.nextFloat() * (ДАЛЬШЕ - БЛИЖЕ);
        послать(игрок, ш.звук(), ш.громкость(), ш.питч(), дальность, случай);
    }

    /**
     * Чужой кашель из-за стены. Свой звук мода — тот же, которым кашляют
     * живые больные, и в этом весь смысл: отличить галлюцинацию от
     * соседа на четвёртой стадии на слух нельзя.
     */
    public static void кашель(ServerPlayer игрок, RandomSource случай) {
        послать(игрок, PlagueSounds.PLAYER_COUGH.get(), 0.6f, 0.8f, ДАЛЬШЕ, случай);
    }

    private static void послать(ServerPlayer игрок, SoundEvent звук, float громкость,
                                float питч, float дальность, RandomSource случай) {
        float[] точка = DreadMath.заСпиной(
            (float) игрок.getX(), (float) игрок.getY(), (float) игрок.getZ(),
            игрок.getYRot(), дальность);
        игрок.connection.send(new ClientboundSoundPacket(
            Holder.direct(звук), SoundSource.AMBIENT,
            точка[0], точка[1], точка[2], громкость, питч, случай.nextLong()));
    }
}
