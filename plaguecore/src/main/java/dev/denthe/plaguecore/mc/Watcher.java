package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.WatcherMath;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Наблюдатель. Спек `2026-09-17-horror-rezhissyor-design.md`, раздел 3.
 *
 * Не призрак: это человек в последней стадии чумы, потерявший разум.
 * Отсюда и родство с {@link MutatedZombie} — та же модель, та же
 * текстура, тот же рендерер. Мистики в проекте нет, и у страха всегда
 * приземлённый источник.
 *
 * Поведение построено на взгляде и живёт в {@link WatcherMath}: под
 * взглядом стоит, без взгляда — ближе, вплотную — исчезает. Играет это
 * против того, как человек проверяет испуг: чтобы убедиться, что никого
 * нет, надо отвести глаза.
 *
 * Первые явления за сессию всегда кончаются растворением. Настоящее —
 * последнее из отпущенных: к тому времени игроки уже решили, что он
 * безвреден, и именно поэтому ошибутся.
 */
public class Watcher extends MutatedZombie implements GeoEntity {

    private static final String KEY_REAL = "Real";
    private static final String KEY_TARGET = "Watched";

    /** Ближе этой дистанции фантом растворяется, а настоящий бросается. */
    private static final double РАСТВОРЕНИЕ = 10.0;

    /** Косинус конуса зрения: примерно 60 градусов в каждую сторону. */
    private static final double КОНУС = 0.5;

    /** Как часто он решает, что делать. Раз в полсекунды — не дёргано. */
    private static final int ПЕРИОД = 10;

    /** Дальше этого он не нужен: игрок ушёл, сцена не состоялась. */
    private static final double ЗАБЫТЬ = 80.0;

    /** Куда он подходит за один шаг, когда на него не смотрят. */
    private static final double ШАГ_БЛИЖЕ = 0.35;

    /** Ближе этой дистанции шаг не делается: иначе он прыгнет в лицо. */
    private static final double НЕ_БЛИЖЕ = 12.0;

    /** Он настоящий — последний за сессию. */
    private boolean настоящий;

    /** Кому он явился. Остальные игроки его поведением не управляют. */
    private UUID цель;

    private boolean бросился;

    /** Стоит и смотрит: тело мёртво неподвижно, раз в несколько секунд — сбой кадра. */
    private static final RawAnimation ПОКОЙ = RawAnimation.begin().thenLoop("idle");

    /** Маска снята: рваный бег с волочащейся ногой. */
    private static final RawAnimation БЕГ = RawAnimation.begin().thenLoop("run");

    private final AnimatableInstanceCache кэш = GeckoLibUtil.createInstanceCache(this);

    public Watcher(EntityType<? extends Watcher> тип, Level уровень) {
        super(тип, уровень);
        setPersistenceRequired();
        setNoAi(true);
        setSilent(true);
    }

    /**
     * Ни одной ванильной цели. Наблюдатель наследует зомби ради телосложения
     * и урона, но не ради поведения: зомби бродит
     * ({@code WaterAvoidingRandomStrollGoal}), сам находит жителей, големов
     * и черепах ({@code NearestAttackableTargetGoal}) и отвечает всякому,
     * кто его задел ({@code HurtByTargetGoal}). Стоило снять «без ИИ»
     * в {@link #броситься}, как всё это оживало разом — и вместо явления
     * по миру бегал зомби, дерущийся с мобами.
     *
     * Цель у него одна и выдаётся руками: игрок, за которым он пришёл.
     * Единственная цель поведения появляется там же, в {@link #броситься}.
     */
    @Override
    protected void registerGoals() {
    }

    /**
     * Крепче мутировавшего зомби, но не босс: вчетвером его кладут
     * быстро, в одиночку ночью — тяжело. Урон в полтора раза больше
     * ванильного зомби, скорость выше человеческой ходьбы.
     */
    public static AttributeSupplier.Builder атрибуты() {
        return MutatedZombie.атрибуты()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 4.5)
            .add(Attributes.MOVEMENT_SPEED, 0.33);
    }

    /**
     * Явить Наблюдателя рядом с игроком. Точка — сбоку и сзади, в 24–40
     * блоках: прямо перед лицом это не явление, а спавн моба.
     *
     * @return true, если он встал в мир
     */
    public static boolean явить(ServerPlayer игрок) {
        ServerLevel уровень = игрок.serverLevel();
        PlagueState состояние = PlagueState.get(уровень);
        RandomSource случай = уровень.getRandom();

        for (int попытка = 0; попытка < 12; попытка++) {
            double угол = Math.toRadians(игрок.getYRot() + 90f + случай.nextInt(180));
            double дальность = 24.0 + случай.nextDouble() * 16.0;
            int x = (int) Math.round(игрок.getX() + Math.sin(угол) * дальность);
            int z = (int) Math.round(игрок.getZ() - Math.cos(угол) * дальность);
            BlockPos точка = уровень.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, игрок.getBlockY(), z));

            // Под землёй heightmap уводит на поверхность — там его
            // никто не увидит. В пещере остаёмся на своей высоте.
            if (игрок.getBlockY() < PlagueConstants.DREAD_DEPTH_Y) {
                точка = new BlockPos(x, игрок.getBlockY(), z);
                if (!уровень.getBlockState(точка).isAir()) continue;
            }

            Watcher н = PlagueEntities.WATCHER.get().create(уровень);
            if (н == null) return false;

            н.цель = игрок.getUUID();
            н.настоящий = WatcherMath.настоящий(состояние.watchers(),
                PlagueConstants.DREAD_WATCHERS_PER_SESSION);
            н.moveTo(точка.getX() + 0.5, точка.getY(), точка.getZ() + 0.5,
                игрок.getYRot() + 180f, 0f);
            н.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                игрок.position());

            if (!уровень.addFreshEntity(н)) continue;
            состояние.addWatcher();
            return true;
        }
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || бросился) return;

        Player игрок = цель == null ? null : level().getPlayerByUUID(цель);

        // Голова ворочается каждый тик, а не раз в ПЕРИОД: раз в полсекунды
        // она бы дёргалась рывками, а рывок читается как глюк моба.
        if (игрок != null) следитьГоловой(игрок);

        if (tickCount % ПЕРИОД != 0) return;

        if (игрок == null || игрок.isSpectator()) {
            discard();
            return;
        }

        double дистанция = position().distanceTo(игрок.position());
        if (дистанция > ЗАБЫТЬ) {
            discard();
            return;
        }

        Vec3 взгляд = игрок.getLookAngle();
        Vec3 на = position().subtract(игрок.getEyePosition());
        boolean смотрят = WatcherMath.смотрит(взгляд.x, взгляд.y, взгляд.z,
            на.x, на.y, на.z, КОНУС) && игрок.hasLineOfSight(this);

        switch (WatcherMath.решение(смотрят, дистанция, настоящий, РАСТВОРЕНИЕ)) {
            // Стоять — буквально стоять: ни шага, ни движения телом.
            // Живёт одна голова, и это хуже полной неподвижности:
            // неподвижная фигура сойдёт за декорацию, а декорация,
            // повернувшая голову, — уже нет.
            case СТОЯТЬ -> setDeltaMovement(Vec3.ZERO);
            case ПОДОЙТИ -> подойти(игрок, дистанция);
            case РАСТВОРИТЬСЯ -> discard();
            case НАПАСТЬ -> броситься(игрок);
        }
    }

    /**
     * Подойти рывком, а не шагом: пока на него не смотрят, он оказывается
     * ближе — и игрок не видел, как это произошло. Ходьба мимо кустов
     * превратила бы его в обычного преследователя.
     */
    private void подойти(Player игрок, double дистанция) {
        if (дистанция <= НЕ_БЛИЖЕ) return;

        Vec3 куда = position().add(игрок.position().subtract(position())
            .normalize().scale(дистанция * ШАГ_БЛИЖЕ));
        BlockPos точка = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(куда));
        if (игрок.getBlockY() < PlagueConstants.DREAD_DEPTH_Y) {
            точка = BlockPos.containing(куда);
            if (!level().getBlockState(точка).isAir()) return;
        }

        moveTo(точка.getX() + 0.5, точка.getY(), точка.getZ() + 0.5, getYRot(), 0f);
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            игрок.position());
    }

    /**
     * Повернуть на игрока только голову.
     *
     * {@code lookAt} здесь не годится: он разворачивает и корпус, а фигура,
     * которая всё время стоит к тебе анфас, читается как моб, который
     * тебя агрит. Нужно другое: тело стоит, как встало, а голова его
     * догоняет. Поэтому пишем прямо в {@code yHeadRot}, мимо {@code yBodyRot}.
     */
    private void следитьГоловой(Player игрок) {
        Vec3 на = игрок.getEyePosition().subtract(getEyePosition());
        yHeadRot = (float) (Mth.atan2(на.z, на.x) * (180.0 / Math.PI)) - 90.0F;
        setXRot((float) (-(Mth.atan2(на.y, на.horizontalDistance()) * (180.0 / Math.PI))));
    }

    /**
     * Ванильный моб доворачивает корпус, когда голова отвернута больше
     * чем на 75° десяток тиков подряд. Наблюдателю этого нельзя:
     * весь смысл в том, что он смотрит через плечо и не шевелится.
     * После броска ограничение возвращается — иначе он будет гнаться
     * боком, а это уже не страшно, а сломано.
     */
    @Override
    public int getMaxHeadYRot() {
        return бросился ? super.getMaxHeadYRot() : 180;
    }

    /** Маска снята. Читает клиентская модель: до броска он не анимирован. */
    public boolean бросился() {
        return бросился;
    }

    /** Третий раз. Тишина кончилась. */
    private void броситься(Player игрок) {
        бросился = true;
        // Немым он и остаётся: стоны, хрип от удара и предсмертный крик
        // зомби выдают в нём обычного моба, а он не моб. Слышен только
        // кашель ниже — он звучит от мира, а не от сущности.
        setNoAi(false);
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
        setTarget(игрок instanceof net.minecraft.world.entity.LivingEntity живой ? живой : null);
        level().playSound(null, getX(), getY(), getZ(),
            PlagueSounds.PLAYER_COUGH.get(), getSoundSource(), 1.2f, 0.6f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag тег) {
        super.addAdditionalSaveData(тег);
        тег.putBoolean(KEY_REAL, настоящий);
        if (цель != null) тег.putUUID(KEY_TARGET, цель);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag тег) {
        super.readAdditionalSaveData(тег);
        настоящий = тег.getBoolean(KEY_REAL);
        цель = тег.hasUUID(KEY_TARGET) ? тег.getUUID(KEY_TARGET) : null;
    }

    // ── GeckoLib ──────────────────────────────────────────────────────

    /**
     * Два состояния, и переключает их не флаг {@code бросился}: он живёт
     * только на сервере, а контроллер крутится на клиенте. Зато по сети
     * ходит флаг «без ИИ», который до броска стоит, а в {@link #броситься}
     * снимается, — по нему и различаем.
     *
     * Переход в три тика: idle и бег должны схлопываться друг в друга,
     * а не переключаться кадром, иначе первый шаг выглядит как телепорт.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar реестр) {
        реестр.add(new AnimationController<>(this, "поза", 3, состояние ->
            состояние.isMoving() && !isNoAi()
                ? состояние.setAndContinue(БЕГ)
                : состояние.setAndContinue(ПОКОЙ)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return кэш;
    }

    /** Настоящий оставляет записку: единственный след, что он был. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel уровень,
                                       net.minecraft.world.damagesource.DamageSource источник,
                                       boolean отИгрока) {
        super.dropCustomDeathLoot(уровень, источник, отИгрока);
        if (настоящий) spawnAtLocation(PlagueBlocks.ARCHIVE_RECORD.get());
    }
}
