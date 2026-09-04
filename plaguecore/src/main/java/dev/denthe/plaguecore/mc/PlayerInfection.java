package dev.denthe.plaguecore.mc;

import dev.denthe.plaguecore.PlagueConstants;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.InfectionMath;
import dev.denthe.plaguecore.core.PlagueGrid;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Тик игрока: сколько чумы он набрал и что с ним от этого происходит.
 *
 * Считается раз в секунду, а не каждый тик: восемь игроков против сотен
 * чанков — работа копеечная, но и её нет смысла делать двадцать раз
 * в секунду, когда числа в спеке заданы «за секунду».
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlayerInfection {
    private PlayerInfection() {}

    @SubscribeEvent
    public static void приТике(PlayerTickEvent.Post событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (игрок.tickCount % PlagueConstants.PLAYER_TICK_INTERVAL != 0) return;
        if (!(игрок.level() instanceof ServerLevel мир)) return;
        // Сетка чумы живёт только в верхнем мире.
        if (мир.dimension() != Level.OVERWORLD) return;
        if (игрок.isCreative() || игрок.isSpectator()) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        int былаСтадия = д.стадия;

        float экспозиция = экспозицияДля(игрок, мир, д);
        д.заражённость = InfectionMath.следующая(д.заражённость, экспозиция);
        д.стадия = InfectionMath.стадия(д.заражённость);

        if (д.стадия != былаСтадия) пересчитатьЗдоровье(игрок);

        // Обращение: чума добивает сама. Период отсчитывается от общего
        // времени мира, а не от личного счётчика, чтобы вход и выход
        // из игры не сбрасывали таймер.
        if (д.стадия >= 4 && PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS > 0
                && мир.getGameTime() % PlagueConstants.PLAYER_STAGE4_DAMAGE_TICKS
                   < PlagueConstants.PLAYER_TICK_INTERVAL) {
            игрок.hurt(игрок.damageSources().source(DamageTypes.WITHER),
                PlagueConstants.PLAYER_STAGE4_DAMAGE);
        }
    }

    /** Сколько очков в секунду набирает или теряет игрок там, где стоит. */
    private static float экспозицияДля(ServerPlayer игрок, ServerLevel мир, PlayerPlagueData д) {
        if (мир.getGameTime() < д.иммунитетДо) return 0f;

        PlagueGrid сетка = PlagueState.get(мир).grid();
        int cx = SectionPos.blockToSectionCoord(игрок.getBlockX());
        int cz = SectionPos.blockToSectionCoord(игрок.getBlockZ());
        if (!сетка.contains(cx, cz)) return 0f;

        int уровень = сетка.getLevel(cx, cz);
        boolean подЗемлёй = !мир.canSeeSky(игрок.blockPosition());
        return InfectionMath.экспозиция(уровень, подЗемлёй, защита(игрок));
    }

    /**
     * Доля погашенной экспозиции, 0..1.
     *
     * Броня: очко брони гасит один процент, полный алмаз (20 очков) —
     * пятая часть. Плюс необязательная добавка от классового кулона
     * ({@link ClassBridge}) — рефлексия на `lmpc_classes`, без него
     * добавка нулевая.
     *
     * ponytail: линейная прикидка от брони; заменить настоящей формулой,
     * когда появятся маски
     */
    public static float защита(ServerPlayer игрок) {
        float отБрони = игрок.getArmorValue() * 0.01f;
        float отКлассов = ClassBridge.дополнительнаяЗащита(игрок);
        return Math.min(0.9f, отБрони + отКлассов);
    }

    /** Выставить заражённость снаружи: команда, отвар, лекарство Клирика. */
    public static void задать(ServerPlayer игрок, float значение) {
        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        д.заражённость = Math.max(0f, Math.min(100f, значение));
        д.стадия = InfectionMath.стадия(д.заражённость);
        пересчитатьЗдоровье(игрок);
    }

    // ── эффекты стадий ────────────────────────────────────────────────

    /** Временный штраф стадии. Снимается вместе с лечением. */
    private static final ResourceLocation ШТРАФ_СТАДИИ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "stage_penalty");

    /** Постоянная потеря за смерти на стадии 2+. Переживает возрождение. */
    private static final ResourceLocation ШТРАФ_СМЕРТЕЙ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "death_penalty");

    /**
     * Привести максимум здоровья в соответствие со стадией и смертями.
     *
     * Оба штрафа считаются вместе, а не по отдельности: временный урезается
     * так, чтобы вместе с постоянным не свести максимум к нулю. Логика
     * зажима живёт в InfectionMath и проверяется тестами.
     */
    public static void пересчитатьЗдоровье(ServerPlayer игрок) {
        AttributeInstance атрибут = игрок.getAttribute(Attributes.MAX_HEALTH);
        if (атрибут == null) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        float постоянный = InfectionMath.постоянныйШтраф(д.смертей);
        float временный = InfectionMath.временныйШтраф(д.стадия, постоянный);

        атрибут.removeModifier(ШТРАФ_СМЕРТЕЙ);
        атрибут.removeModifier(ШТРАФ_СТАДИИ);

        if (постоянный > 0f) {
            атрибут.addOrReplacePermanentModifier(new AttributeModifier(
                ШТРАФ_СМЕРТЕЙ, -постоянный, AttributeModifier.Operation.ADD_VALUE));
        }
        if (временный > 0f) {
            атрибут.addOrUpdateTransientModifier(new AttributeModifier(
                ШТРАФ_СТАДИИ, -временный, AttributeModifier.Operation.ADD_VALUE));
        }

        // Максимум мог упасть ниже текущего здоровья — подрезаем, иначе
        // в интерфейсе останутся сердца, которых уже нет.
        if (игрок.getHealth() > игрок.getMaxHealth()) {
            игрок.setHealth(игрок.getMaxHealth());
        }

        // Заодно сообщаем клиенту стадию — для тусклого экрана. Отправка
        // живёт здесь, а не в шести местах смены стадии: этот метод и так
        // вызывается всеми ими, а забыть один вызов — значит получить
        // экран, отставший от здоровья.
        PlagueNetwork.отправитьСтадию(игрок, д.стадия);
    }

    /**
     * Обращение не лечится ничем.
     *
     * Ловим общее событие лечения, а не сытость отдельно: так одним
     * условием отсекаются и регенерация от еды, и зелья, и золотые
     * яблоки, и всё, что принесут другие моды. Единственным выходом
     * остаётся Клирик — ровно как задумано спеком.
     */
    @SubscribeEvent
    public static void приЛечении(LivingHealEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerPlagueData.данные(игрок).стадия >= 4) событие.setCanceled(true);
    }

    /**
     * Больного еда сытит хуже.
     *
     * Считаем по свойствам съеденного предмета и отнимаем разницу сразу
     * после того, как ваниль её начислила. Так работает с любой едой,
     * включая чужую модовую, — своего списка продуктов держать не нужно.
     */
    @SubscribeEvent
    public static void послеЕды(LivingEntityUseItemEvent.Finish событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;
        if (PlayerPlagueData.данные(игрок).стадия < 1) return;

        ItemStack съеденное = событие.getItem();
        FoodProperties свойства = съеденное.getFoodProperties(игрок);
        if (свойства == null) return;

        float доля = 1f - Math.max(0f, Math.min(1f, PlagueConstants.PLAYER_FOOD_MULTIPLIER));
        if (доля <= 0f) return;

        FoodData сытость = игрок.getFoodData();
        int отнятьЕды = Math.round(свойства.nutrition() * доля);
        float отнятьНасыщения = свойства.saturation() * доля;

        сытость.setFoodLevel(Math.max(0, сытость.getFoodLevel() - отнятьЕды));
        сытость.setSaturation(Math.max(0f, сытость.getSaturationLevel() - отнятьНасыщения));
    }

    // ── постоянная цена смерти ────────────────────────────────────────

    /**
     * Смерть на стадии 2+ стоит полсердца навсегда.
     *
     * Считается стадия в момент смерти, а не источник урона: умер
     * от лихорадки, от моба, от падения — неважно, важно, что был болен.
     * Правило простое и не требует объяснений.
     *
     * Работает в связке с модом Corpse: лут не теряется, поэтому
     * единственной ценой смерти остаётся здоровье.
     */
    @SubscribeEvent
    public static void приСмерти(LivingDeathEvent событие) {
        if (!(событие.getEntity() instanceof ServerPlayer игрок)) return;

        PlayerPlagueData д = PlayerPlagueData.данные(игрок);
        if (д.стадия < 2) return;

        д.смертей++;
        PlagueCore.LOG.info("{} умер на стадии {}, смертей от чумы: {}",
            игрок.getGameProfile().getName(), д.стадия, д.смертей);
    }

    /**
     * После возрождения игрок — новая сущность с чистыми атрибутами.
     * Вложение переносится само (copyOnDeath), а модификаторы здоровья
     * приходится вешать заново.
     */
    @SubscribeEvent
    public static void приВозрождении(PlayerEvent.PlayerRespawnEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) пересчитатьЗдоровье(игрок);
    }

    /** Тем же порядком — при входе в игру: атрибуты грузятся без наших модификаторов. */
    @SubscribeEvent
    public static void приВходе(PlayerEvent.PlayerLoggedInEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) пересчитатьЗдоровье(игрок);
    }
}
