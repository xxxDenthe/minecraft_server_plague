package dev.denthe.plaguecore.mc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Мутировавший зомби: обычный зомби, простоявший в заражённом чанке.
 *
 * Наследуется от ванильного {@link Zombie}, а не от Monster, как заражённая
 * скотина. Причина простая: у зомби уже написано всё, чего мы хотим —
 * ломает двери на сложности «трудно», зовёт подкрепление, обращает жителей,
 * тонет как зомби. Писать это заново значило бы переписать полтысячи строк
 * ванили ради двух изменённых чисел.
 *
 * Два поведения ванили мы всё же отключаем:
 * <ul>
 *   <li>солнце его не жжёт — чума дня не боится, так же как заражённая
 *       скотина в {@link InfectedAnimal};</li>
 *   <li>в воде не превращается в утопленника — утопленник вернул бы
 *       зелёную ванильную шкуру посреди серого мира.</li>
 * </ul>
 */
public class MutatedZombie extends Zombie {

    public MutatedZombie(EntityType<? extends MutatedZombie> тип, Level уровень) {
        super(тип, уровень);
    }

    /**
     * Ванильный зомби с прибавкой к урону и скорости.
     *
     * Числа: урон 3 → 5, скорость 0.23 → 0.29. Это заметно, но не ломает
     * бой — обогнать его игрок всё ещё может, ходьба у игрока 0.1 при
     * скорости зомби 0.29 в единицах атрибута, бег быстрее. Здоровье
     * поднято до 24, чтобы прибавка к урону не делала его стеклянным.
     */
    public static AttributeSupplier.Builder атрибуты() {
        return Zombie.createAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.MOVEMENT_SPEED, 0.29)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }
}
