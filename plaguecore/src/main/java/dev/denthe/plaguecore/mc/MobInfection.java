package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Живность, застрявшая в заражённом чанке, со временем мутирует.
 *
 * Раньше класс звался AnimalInfection и ловил только {@code Animal} —
 * мирную скотину. Теперь сюда же заходит зомби, а он {@code Monster},
 * поэтому проверка идёт по {@link Mob}: кого превращать, решает один
 * список {@link #заражённаяВерсия} и никто больше.
 *
 * Проверка висит на тике сущности, а не на общем тике сервера: перебирать
 * всех мобов мира каждый раз пришлось бы самим, а так каждый моб приходит
 * к нам сам и ровно тогда, когда его чанк загружен.
 *
 * Дороже одного чтения из сетки проверка не стоит: сначала отсеиваем по
 * {@link PlagueConstants#ANIMAL_CHECK_TICKS} остатку от деления, и только
 * у одного моба из сотни тиков доходит до заглядывания в карту.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class MobInfection {
    private MobInfection() {}

    /**
     * Во что превращается вид. Здесь и расширяется список: заведёшь
     * заражённую овцу — допишешь строку, всё остальное уже работает.
     *
     * Наши собственные заражённые версии сюда не попадают: их видов
     * в списке нет, значит вернётся null и превращение не пойдёт по кругу.
     * Хаски, утопленники и зомби-жители — тоже: у них свои виды, и в
     * ванильном мире они всё равно редки.
     */
    private static EntityType<? extends Mob> заражённаяВерсия(EntityType<?> вид) {
        if (вид == EntityType.PIG) return PlagueEntities.INFECTED_PIG.get();
        if (вид == EntityType.COW) return PlagueEntities.INFECTED_COW.get();
        if (вид == EntityType.ZOMBIE) return PlagueEntities.MUTATED_ZOMBIE.get();
        return null;
    }

    /**
     * Порог превращения за одну проверку. Растёт с уровнем чанка линейно:
     * на кромке моб портится примерно за две минуты, в Гнили — за
     * полминуты. Ноль в конфиге выключает превращение целиком.
     */
    public static float порог(int уровень) {
        if (уровень <= 0) return 0f;
        return Math.min(1f, PlagueConstants.ANIMAL_INFECT_CHANCE * уровень);
    }

    @SubscribeEvent
    public static void приТике(EntityTickEvent.Post событие) {
        Entity сущность = событие.getEntity();
        if (!(сущность instanceof Mob моб)) return;
        if (моб.tickCount % PlagueConstants.ANIMAL_CHECK_TICKS != 0) return;
        if (!(моб.level() instanceof ServerLevel мир)) return;
        // Сетка чумы живёт только в верхнем мире — в аду и Крае её нет.
        if (мир.dimension() != Level.OVERWORLD) return;

        EntityType<? extends Mob> цель = заражённаяВерсия(моб.getType());
        if (цель == null) return;

        PlagueGrid сетка = PlagueState.get(мир).grid();
        int cx = SectionPos.blockToSectionCoord(моб.getBlockX());
        int cz = SectionPos.blockToSectionCoord(моб.getBlockZ());
        if (!сетка.contains(cx, cz)) return;

        if (моб.getRandom().nextFloat() >= порог(сетка.getLevel(cx, cz))) return;

        Mob заражённый = моб.convertTo(цель, true);
        if (заражённый == null) return;
        мир.playSound(null, заражённый.blockPosition(),
            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }
}
