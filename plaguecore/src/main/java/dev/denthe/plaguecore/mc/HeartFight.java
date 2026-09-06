package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.FightPhases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private static final String КЛЮЧ_ВОЛНЫ = "FightWave";

    /** Сколько раз пробуем найти место под одного моба. */
    private static final int ПОПЫТОК_НА_МОБА = 24;

    /**
     * Дальность, на которой моб не забывает игрока. Ванильный зомби
     * теряет цель за 35 блоков, а зал больше — без прибавки волна
     * разбрелась бы по логову.
     */
    private static final double АГРО_ДАЛЬНОСТЬ = 64.0;

    private final RottenHeart сердце;

    /**
     * Фаза, на которой уже прошёл спазм. Ноль — бой ещё не начинался:
     * Сердце стоит целое и его никто не трогал.
     */
    private int фазаСпазма = 0;

    /**
     * Кто вышел в текущей волне. Держим список UUID, а не считаем мобов
     * вокруг: случайный зомби, забредший в зал, запирал бы Сердце
     * навсегда. Список уезжает в NBT вместе с сущностью.
     */
    private final List<UUID> волна = new ArrayList<>();

    /**
     * Тиков до следующего импульса. Не сохраняется: после перезахода
     * счёт пойдёт заново, и это ровно то, что нужно — иначе игрок
     * получал бы удар чумой в момент входа в мир.
     */
    private int доИмпульса = 0;

    public HeartFight(RottenHeart сердце) {
        this.сердце = сердце;
    }

    // ── бой ───────────────────────────────────────────────────────────

    /** Каждый тик сущности, только на сервере. */
    public void тик() {
        if (фазаСпазма == 0) return;   // бой ещё не начинался

        if (--доИмпульса > 0) return;
        доИмпульса = Math.max(1, PlagueConstants.HEART_PULSE_SECONDS * 20);
        импульс();
    }

    /**
     * Сердце давит чумой на весь зал.
     *
     * Всю работу делает подсистема 2: экран тускнеет, голос хрипнет,
     * идёт кашель. К третьей фазе восемь человек в голосовом чате
     * перестают разбирать друг друга — дописывать для этого нечего.
     *
     * Импульс режется вдвое, если чанк под Сердцем опущен до чистого
     * уровня. Опустить его может только очиститель Кузнеца, поставленный
     * до боя: у штурма появляется подготовка, а у Кузнеца — роль.
     */
    private void импульс() {
        if (!(сердце.level() instanceof ServerLevel мир)) return;

        List<ServerPlayer> зал = залВСборе();
        if (зал.isEmpty()) return;

        int фаза = Math.min(фазаСпазма, FightPhases.ФАЗ);
        float сила = PlagueConstants.HEART_PULSE[фаза - 1];

        int уровень = PlagueApi.getChunkLevelAt(мир, сердце.blockPosition());
        if (уровень <= PlagueConstants.HEART_PULSE_CLEAN_LEVEL) сила *= 0.5f;
        if (сила <= 0f) return;

        for (ServerPlayer игрок : зал) {
            PlayerInfection.задать(игрок, PlagueApi.getInfection(игрок) + сила);
        }

        мир.sendParticles(ParticleTypes.SCULK_SOUL,
            сердце.getX(), сердце.getY() + сердце.getBbHeight() * 0.5, сердце.getZ(),
            60, 2.0, 1.0, 2.0, 0.05);
        мир.playSound(null, сердце.getX(), сердце.getY(), сердце.getZ(),
            SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    /**
     * Сердце закрыто спазмом: жив хоть один моб волны — урон не проходит.
     *
     * Мёртвых вычищаем прямо здесь, при опросе. Отдельный тик под это
     * заводить незачем: метод и так дёргается на каждом ударе.
     */
    public boolean закрыто() {
        if (волна.isEmpty()) return false;
        if (!(сердце.level() instanceof ServerLevel мир)) return false;

        волна.removeIf(ид -> {
            Entity моб = мир.getEntity(ид);
            return моб == null || !моб.isAlive();
        });
        return !волна.isEmpty();
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

        List<ServerPlayer> зал = залВСборе();
        if (зал.isEmpty()) return;

        int сколько = PlagueConstants.HEART_WAVE_BASE
            + PlagueConstants.HEART_WAVE_PER_PLAYER * зал.size();

        RandomSource ГСЧ = мир.getRandom();
        for (int i = 0; i < сколько; i++) {
            выпустить(мир, ГСЧ, видДля(фаза, i), зал.get(ГСЧ.nextInt(зал.size())));
        }
    }

    /**
     * Кто выходит в этой фазе. Своих мобов не пишем: волна должна
     * читаться как «нежить полезла», а не как зоопарк.
     */
    private static EntityType<? extends Mob> видДля(int фаза, int номер) {
        return switch (фаза) {
            case 1 -> (номер % 3 == 0) ? EntityType.SKELETON : EntityType.ZOMBIE;
            case 2 -> PlagueEntities.MUTATED_ZOMBIE.get();
            default -> (номер % 3 == 0)
                ? PlagueEntities.INFECTED_COW.get()
                : PlagueEntities.MUTATED_ZOMBIE.get();
        };
    }

    /** Один моб волны: поставить, натравить, запомнить. */
    private void выпустить(ServerLevel мир, RandomSource ГСЧ,
                           EntityType<? extends Mob> тип, Player цель) {
        BlockPos место = найтиМесто(мир, ГСЧ);
        if (место == null) return;

        Mob моб = тип.create(мир);
        if (моб == null) return;

        моб.moveTo(место.getX() + 0.5, место.getY(), место.getZ() + 0.5,
            ГСЧ.nextFloat() * 360f, 0f);
        моб.finalizeSpawn(мир, мир.getCurrentDifficultyAt(место),
            MobSpawnType.EVENT, null);
        // Волна не должна растаять, пока команда отбивается в дальнем
        // углу зала: без этого мобы деспавнятся и Сердце откроется само.
        моб.setPersistenceRequired();
        мир.addFreshEntity(моб);

        AttributeInstance дальность = моб.getAttribute(Attributes.FOLLOW_RANGE);
        if (дальность != null && дальность.getBaseValue() < АГРО_ДАЛЬНОСТЬ) {
            дальность.setBaseValue(АГРО_ДАЛЬНОСТЬ);
        }
        моб.setTarget(цель);

        волна.add(моб.getUUID());
    }

    /**
     * Место под моба в зале: две клетки воздуха на твёрдом полу.
     *
     * Ищем по всему кругу зала, а не вплотную к Сердцу: волна должна
     * приходить со стороны, иначе она вылезает игрокам в спину прямо
     * у цели и бой превращается в свалку.
     */
    private BlockPos найтиМесто(ServerLevel мир, RandomSource ГСЧ) {
        int радиус = PlagueConstants.HEART_ARENA_RADIUS;
        BlockPos центр = сердце.blockPosition();

        for (int i = 0; i < ПОПЫТОК_НА_МОБА; i++) {
            double угол = ГСЧ.nextDouble() * Math.PI * 2.0;
            double дальше = радиус * (0.5 + 0.5 * ГСЧ.nextDouble());
            BlockPos место = центр.offset(
                (int) Math.round(Math.cos(угол) * дальше),
                ГСЧ.nextInt(9) - 4,
                (int) Math.round(Math.sin(угол) * дальше));

            if (!мир.isLoaded(место)) continue;
            if (!мир.getBlockState(место.below()).isSolidRender(мир, место.below())) continue;
            if (!мир.getBlockState(место).isAir()) continue;
            if (!мир.getBlockState(место.above()).isAir()) continue;
            return место;
        }
        return null;
    }

    /** Убрать волну целиком: Сердце уснуло, держать её незачем. */
    public void разогнатьВолну() {
        if (!(сердце.level() instanceof ServerLevel мир)) return;
        for (UUID ид : волна) {
            Entity моб = мир.getEntity(ид);
            if (моб != null) моб.discard();
        }
        волна.clear();
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

        ListTag список = new ListTag();
        for (UUID ид : волна) список.add(NbtUtils.createUUID(ид));
        тег.put(КЛЮЧ_ВОЛНЫ, список);
    }

    public void загрузить(CompoundTag тег) {
        фазаСпазма = тег.getInt(КЛЮЧ_ФАЗЫ);

        волна.clear();
        for (Tag элемент : тег.getList(КЛЮЧ_ВОЛНЫ, Tag.TAG_INT_ARRAY)) {
            волна.add(NbtUtils.loadUUID(элемент));
        }
    }
}
