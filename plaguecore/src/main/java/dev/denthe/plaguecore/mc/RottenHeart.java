package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.core.HeartDecay;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Сердце чумы: источник заразы, который игрокам предстоит уничтожить.
 *
 * Наследник {@link Mob}, а не PathfinderMob: Сердце стоит там, где его
 * поставили, и навигация ему не нужна — она стоила бы тиков на пустом
 * месте. По той же причине выключены ИИ и гравитация: Сердце должно
 * висеть и в воздухе зала логова, если GM так поставил.
 *
 * Разрушение — по кускам. Двадцать костей модели прячутся по одной, по
 * мере урона; какие именно, считает {@link HeartDecay} по одному лишь
 * здоровью, без счётчика ударов. Итог хранится битовой маской: бит i
 * поднят — кусок {@code HeartDecay.КОСТИ[i]} отвалился. Маска
 * синхронизируется на клиент, иначе рендер не знал бы, что прятать.
 *
 * Дизайн — docs/superpowers/specs/2026-09-05-serdce-chumy-design.md
 */
public class RottenHeart extends Mob implements GeoEntity {

    private static final EntityDataAccessor<Integer> МАСКА =
        SynchedEntityData.defineId(RottenHeart.class, EntityDataSerializers.INT);

    private static final String КЛЮЧ_МАСКИ = "BrokenMask";

    private static final RawAnimation БИЕНИЕ = RawAnimation.begin().thenLoop("heartbeat");

    private final AnimatableInstanceCache кэш = GeckoLibUtil.createInstanceCache(this);

    public RottenHeart(EntityType<? extends RottenHeart> тип, Level уровень) {
        super(тип, уровень);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();

        // Здоровье берётся из конфига, а не из атрибутов реестра: атрибуты
        // строятся один раз при регистрации мода, а конфиг перечитывается
        // на живом сервере. У загружаемого из мира Сердца значение из NBT
        // ляжет поверх сразу после конструктора — тут только новое.
        AttributeInstance запас = getAttribute(Attributes.MAX_HEALTH);
        if (запас != null) запас.setBaseValue(PlagueConstants.HEART_HEALTH);
        setHealth(getMaxHealth());
    }

    /**
     * Здоровье здесь — только умолчание реестра, настоящее ставит
     * конструктор. Сопротивление отбрасыванию полное: иначе волна мобов
     * вытолкала бы Сердце из зала.
     */
    public static AttributeSupplier.Builder атрибуты() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, PlagueConstants.HEART_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ── разрушение ────────────────────────────────────────────────────

    /** Битовая маска отвалившихся кусков; её читает рендер. */
    public int маскаКусков() {
        return this.entityData.get(МАСКА);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder строитель) {
        super.defineSynchedData(строитель);
        строитель.define(МАСКА, 0);
    }

    @Override
    public boolean hurt(DamageSource источник, float урон) {
        boolean попали = super.hurt(источник, урон);
        if (попали && !level().isClientSide) пересчитатьКуски();
        return попали;
    }

    /**
     * Сердце ломают игроки, а не декорации логова. Огонь, холод, вода
     * и падение проходят мимо; удар оружием, стрела и взрыв — нет.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource источник) {
        return super.isInvulnerableTo(источник)
            || источник.is(DamageTypeTags.IS_FIRE)
            || источник.is(DamageTypeTags.IS_FREEZING)
            || источник.is(DamageTypeTags.IS_DROWNING)
            || источник.is(DamageTypeTags.IS_FALL)
            || источник.is(DamageTypes.IN_WALL)
            || источник.is(DamageTypes.CACTUS);
    }

    private void пересчитатьКуски() {
        int новая = HeartDecay.маска(getHealth(), getMaxHealth());
        int прежняя = маскаКусков();
        if (новая == прежняя) return;

        this.entityData.set(МАСКА, новая);
        осыпать(Integer.bitCount(прежняя), Integer.bitCount(новая));
    }

    /**
     * Пепел и хруст на месте отломанного куска. Высоту берём по группе:
     * корни осыпаются снизу, ветки обламываются сверху, тело рвётся
     * посередине — иначе все двадцать кусков пылили бы из одной точки.
     */
    private void осыпать(int былоСломано, int сталоСломано) {
        if (!(level() instanceof ServerLevel сервер)) return;

        for (int кусок = былоСломано; кусок < сталоСломано; кусок++) {
            double высота = getY() + getBbHeight() * долиВысоты(кусок);
            сервер.sendParticles(ParticleTypes.ASH,
                getX(), высота, getZ(),
                40, 0.8, 0.5, 0.8, 0.02);
            сервер.sendParticles(ParticleTypes.SCULK_SOUL,
                getX(), высота, getZ(),
                4, 0.5, 0.4, 0.5, 0.01);
        }

        сервер.playSound(null, getX(), getY(), getZ(),
            SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.4F, 0.55F);
    }

    /** Доля высоты хитбокса, на которой ломается кусок с этим номером. */
    private static double долиВысоты(int кусок) {
        if (кусок < 8) return 0.18;    // roots_p1..p8   — корни, низ
        if (кусок < 12) return 0.85;   // arteries_p1..p4 — ветки, верх
        return 0.5;                    // body_p1..p8    — тело, середина
    }

    // ── смерть ────────────────────────────────────────────────────────

    @Override
    public void die(DamageSource источник) {
        super.die(источник);
        if (level() instanceof ServerLevel сервер) сердцеУничтожено(сервер);
    }

    /**
     * Сердце не заваливается набок, как труп: у него нет позы. Вспышка
     * уже была в {@link #сердцеУничтожено}, остаётся убрать сущность.
     */
    @Override
    protected void tickDeath() {
        discard();
    }

    /**
     * Крючок подсистемы 6 «Финал». Сейчас здесь только вспышка и звук.
     * Очистка мира, конец сессии и всё прочее впишутся сюда, когда финал
     * спроектируют, — искать это место больше нигде не придётся.
     */
    private void сердцеУничтожено(ServerLevel сервер) {
        double центр = getY() + getBbHeight() * 0.5;
        сервер.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), центр, getZ(),
            1, 0.0, 0.0, 0.0, 0.0);
        сервер.sendParticles(ParticleTypes.ASH, getX(), центр, getZ(),
            300, 1.5, 1.5, 1.5, 0.08);
        сервер.playSound(null, getX(), getY(), getZ(),
            SoundEvents.SCULK_CATALYST_BREAK, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    // ── сохранение ────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag тег) {
        super.addAdditionalSaveData(тег);
        тег.putInt(КЛЮЧ_МАСКИ, маскаКусков());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag тег) {
        super.readAdditionalSaveData(тег);
        // Маска в файле — не источник истины, а след для чужих глаз:
        // здоровье прочитано выше супервызовом, и маска выводится из него.
        // Расхождение возможно после правки здоровья в конфиге,
        // и побеждает здоровье.
        this.entityData.set(МАСКА, HeartDecay.маска(getHealth(), getMaxHealth()));
    }

    // ── неподвижность ─────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        // Целей нет намеренно: Сердце не двигается и никого не ищет.
    }

    /**
     * Хитбокс растёт вместе с картинкой. Иначе крупное Сердце ловило бы
     * удары только по своей исходной середине, а рендер рисовал бы куски
     * там, куда попасть нельзя.
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose поза) {
        return super.getDefaultDimensions(поза).scale(PlagueConstants.HEART_SCALE);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double расстояние) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource источник) {
        return SoundEvents.SCULK_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    // ── GeckoLib ──────────────────────────────────────────────────────

    /**
     * Один контроллер, одна зациклённая анимация. Скорость падает вместе
     * со здоровьем: умирающее Сердце бьётся заметно медленнее — это
     * читается лучше любой полоски здоровья.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar реестр) {
        реестр.add(new AnimationController<>(this, "beat", 0, состояние -> {
            состояние.getController().setAnimationSpeed(
                HeartDecay.скоростьБиения(getHealth(), getMaxHealth()));
            return состояние.setAndContinue(БИЕНИЕ);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return кэш;
    }
}
