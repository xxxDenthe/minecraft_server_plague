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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Мирная скотина, застрявшая в заражённом чанке, со временем мутирует.
 *
 * Проверка висит на тике сущности, а не на общем тике сервера: перебирать
 * всех животных мира каждый раз пришлось бы самим, а так каждое животное
 * приходит к нам само и ровно тогда, когда его чанк загружен.
 *
 * Дороже одного чтения из сетки проверка не стоит: сначала отсеиваем по
 * {@link PlagueConstants#ANIMAL_CHECK_TICKS} остатку от деления, и только
 * у одного животного из сотни тиков доходит до заглядывания в карту.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class AnimalInfection {
    private AnimalInfection() {}

    /**
     * Во что превращается вид. Здесь и расширяется список: заведёшь
     * заражённую овцу — допишешь строку, всё остальное уже работает.
     */
    private static EntityType<InfectedAnimal> заражённаяВерсия(EntityType<?> вид) {
        if (вид == EntityType.PIG) return PlagueEntities.INFECTED_PIG.get();
        if (вид == EntityType.COW) return PlagueEntities.INFECTED_COW.get();
        return null;
    }

    /**
     * Порог превращения за одну проверку. Растёт с уровнем чанка линейно:
     * на кромке животное портится примерно за две минуты, в Гнили — за
     * полминуты. Ноль в конфиге выключает превращение целиком.
     */
    public static float порог(int уровень) {
        if (уровень <= 0) return 0f;
        return Math.min(1f, PlagueConstants.ANIMAL_INFECT_CHANCE * уровень);
    }

    @SubscribeEvent
    public static void приТике(EntityTickEvent.Post событие) {
        Entity сущность = событие.getEntity();
        if (!(сущность instanceof Animal животное)) return;
        if (животное.tickCount % PlagueConstants.ANIMAL_CHECK_TICKS != 0) return;
        if (!(животное.level() instanceof ServerLevel мир)) return;
        // Сетка чумы живёт только в верхнем мире — в аду и Крае её нет.
        if (мир.dimension() != Level.OVERWORLD) return;

        EntityType<InfectedAnimal> цель = заражённаяВерсия(животное.getType());
        if (цель == null) return;

        PlagueGrid сетка = PlagueState.get(мир).grid();
        int cx = SectionPos.blockToSectionCoord(животное.getBlockX());
        int cz = SectionPos.blockToSectionCoord(животное.getBlockZ());
        if (!сетка.contains(cx, cz)) return;

        if (животное.getRandom().nextFloat() >= порог(сетка.getLevel(cx, cz))) return;

        InfectedAnimal заражённое = животное.convertTo(цель, true);
        if (заражённое == null) return;
        мир.playSound(null, заражённое.blockPosition(),
            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }
}
