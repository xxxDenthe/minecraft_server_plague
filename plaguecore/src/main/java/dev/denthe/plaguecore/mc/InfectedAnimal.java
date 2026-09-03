package dev.denthe.plaguecore.mc;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Заражённая скотина: одна сущность на всех, вид решает только тип.
 *
 * Поведение зомбиподобное — видит игрока, бежит и бьёт, — но {@link Monster}
 * сам по себе на солнце не горит, а мы этого и не добавляем: чума не боится
 * дня, животное портится и остаётся опасным круглые сутки.
 *
 * Отдельных классов на свинью и корову нет намеренно. Отличаются они ровно
 * тремя числами атрибутов и тремя звуками; наследование ради этого дало бы
 * два пустых класса. Геометрия у обеих ванильная, поэтому и модели свои
 * не нужны — рендерер берёт ванильные {@code PigModel} и {@code CowModel}.
 */
public class InfectedAnimal extends Monster {

    public InfectedAnimal(EntityType<? extends InfectedAnimal> тип, Level уровень) {
        super(тип, уровень);
    }

    /** Свинья: слабее и заметно быстрее коровы. */
    public static AttributeSupplier.Builder атрибутыСвиньи() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 12.0)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Корова: мяса больше, бьёт тяжелее, догоняет хуже. */
    public static AttributeSupplier.Builder атрибутыКоровы() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.MOVEMENT_SPEED, 0.24)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private boolean свинья() {
        return getType() == PlagueEntities.INFECTED_PIG.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return свинья() ? SoundEvents.PIG_AMBIENT : SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource источник) {
        return свинья() ? SoundEvents.PIG_HURT : SoundEvents.COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return свинья() ? SoundEvents.PIG_DEATH : SoundEvents.COW_DEATH;
    }
}
