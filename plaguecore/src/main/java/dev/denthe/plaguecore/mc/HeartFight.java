package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.FightPhases;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Финальный бой: что происходит вокруг Сердца, пока его бьют.
 *
 * Живёт на самой сущности и тикает от неё: когда Сердце выгружено,
 * боя нет и считать нечего. Победа сюда не входит — она идёт три
 * минуты уже после исчезновения сущности, ею занимается
 * {@link HeartVictory}.
 *
 * Ритм боя один и повторяется трижды: игроки бьют, на границе фазы
 * Сердце закрывается спазмом и выпускает волну, волну вычищают,
 * Сердце открывается. Правило читается без единой строчки текста.
 *
 * Дизайн — docs/superpowers/specs/2026-09-07-final-boj-design.md
 */
public final class HeartFight {

    private static final String КЛЮЧ_ФАЗЫ = "FightPhase";

    private final RottenHeart сердце;

    /**
     * Фаза, на которой уже прошёл спазм. Ноль — бой ещё не начинался:
     * Сердце стоит целое и его никто не трогал.
     */
    private int фазаСпазма = 0;

    public HeartFight(RottenHeart сердце) {
        this.сердце = сердце;
    }

    // ── бой ───────────────────────────────────────────────────────────

    /** Каждый тик сущности, только на сервере. */
    public void тик() {
        // Волны, импульс и сон приедут сюда в задачах 4–6.
    }

    /**
     * Сердце закрыто спазмом — урон по нему не проходит.
     *
     * Пока волн нет, спазм мгновенный: закрылись и тут же открылись.
     * Настоящее условие «жива ли волна» ставит задача 4.
     */
    public boolean закрыто() {
        return false;
    }

    /**
     * Позвать после того, как удар прошёл и здоровье уже уменьшилось.
     *
     * Первый же удар по целому Сердцу начинает бой: спазм первой фазы
     * идёт сразу, а не в конце неё. Иначе первая волна вышла бы только
     * к двум третям здоровья, и начало боя было бы пустым.
     */
    public void приУдаре() {
        int фаза = FightPhases.фаза(сердце.getHealth(), сердце.getMaxHealth());
        if (фаза <= фазаСпазма) return;
        фазаСпазма = фаза;
        спазм(фаза);
    }

    /** Сердце сжимается: замирает, гудит, выпускает волну своей фазы. */
    private void спазм(int фаза) {
        if (!(сердце.level() instanceof ServerLevel мир)) return;

        мир.playSound(null, сердце.getX(), сердце.getY(), сердце.getZ(),
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0F, 0.35F);
        // Волну выпускает задача 4.
    }

    // ── зал ───────────────────────────────────────────────────────────

    /**
     * Живые игроки в зале. Расстояние — по горизонтали: зал бывает
     * многоярусным, и человек на балконе такой же участник, как внизу.
     */
    public List<ServerPlayer> залВСборе() {
        List<ServerPlayer> свои = new ArrayList<>();
        if (!(сердце.level() instanceof ServerLevel мир)) return свои;

        double радиус = PlagueConstants.HEART_ARENA_RADIUS;
        for (ServerPlayer игрок : мир.players()) {
            if (игрок.isSpectator() || !игрок.isAlive()) continue;
            double дх = игрок.getX() - сердце.getX();
            double дз = игрок.getZ() - сердце.getZ();
            if (дх * дх + дз * дз <= радиус * радиус) свои.add(игрок);
        }
        return свои;
    }

    /** Фаза, на которой стоит бой. Ноль — бой ещё не начинался. */
    public int фазаСпазма() {
        return фазаСпазма;
    }

    // ── сохранение ────────────────────────────────────────────────────

    public void сохранить(CompoundTag тег) {
        тег.putInt(КЛЮЧ_ФАЗЫ, фазаСпазма);
    }

    public void загрузить(CompoundTag тег) {
        фазаСпазма = тег.getInt(КЛЮЧ_ФАЗЫ);
    }
}
