package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Wellbeing;
import dev.denthe.plaguecore.mc.ClassBridge;
import dev.denthe.plaguecore.mc.ThirstBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Что человек чувствует прямо сейчас — собранное и готовое к рисованию.
 * Спек интерфейса «Состояние здоровья», разделы 4 и 14.
 *
 * Ничего не спрашивает у сервера: стадия уже приходит пакетом Stage,
 * здоровье, голод и эффекты лежат у клиента, класс и жажда читаются
 * мостами рефлексии. Для осмотра себя новой сети не нужно вовсе.
 *
 * Пересчёт раз в десять тиков, готовые Component лежат полями. Экран
 * и HUD рисуются каждый кадр, и собирать там переводы заново значило бы
 * тысячи объектов в секунду ради текста, который меняется раз в минуту.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthSense {
    private HealthSense() {}

    /** Как часто пересчитываем, тиков. Полсекунды — глазу хватает. */
    private static final int ПЕРИОД = 10;

    private static int ступень;
    private static boolean клирик;
    private static Component общее = Component.empty();
    private static Component общееКратко = Component.empty();
    private static Component голод = Component.empty();
    private static Component жажда;                       // null — мода жажды нет
    private static List<Component> ощущения = List.of();
    private static final Component[] части = new Component[Wellbeing.Часть.values().length];

    public static int ступень() { return ступень; }
    public static boolean клирик() { return клирик; }
    public static Component общее() { return общее; }
    public static Component общееКратко() { return общееКратко; }
    public static Component голод() { return голод; }

    /** {@code null}, если мода жажды в сборке нет: строку тогда не рисуем вовсе. */
    public static Component жажда() { return жажда; }

    public static List<Component> ощущения() { return ощущения; }

    public static Component часть(Wellbeing.Часть часть) {
        Component готово = части[часть.ordinal()];
        return готово == null ? Component.empty() : готово;
    }

    @SubscribeEvent
    public static void приТике(ClientTickEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer игрок = mc.player;
        if (игрок == null) return;
        if (игрок.tickCount % ПЕРИОД != 0) return;
        пересчитать(игрок);
    }

    private static void пересчитать(LocalPlayer игрок) {
        ступень = Wellbeing.ступень(PlagueClientAccess.стадия());
        клирик = ClassBridge.клирик(игрок);

        общее = Component.translatable(Wellbeing.общее(ступень));
        общееКратко = Component.translatable(Wellbeing.общееКратко(ступень));
        голод = Component.translatable(Wellbeing.голод(игрок.getFoodData().getFoodLevel()));

        int вода = ThirstBridge.уровень(игрок);
        жажда = вода < 0 ? null : Component.translatable(Wellbeing.жажда(вода));

        for (Wellbeing.Часть часть : Wellbeing.Часть.values()) {
            части[часть.ordinal()] = Component.translatable(Wellbeing.часть(часть, ступень));
        }

        ощущения = собратьОщущения(игрок);
    }

    /**
     * Эффекты человеческими словами. Незнакомые пропускаются молча,
     * уровень и длительность не показываются — они технические.
     */
    private static List<Component> собратьОщущения(LocalPlayer игрок) {
        List<Component> список = new ArrayList<>();
        for (MobEffectInstance эффект : игрок.getActiveEffects()) {
            String идентификатор = BuiltInRegistries.MOB_EFFECT
                .getKey(эффект.getEffect().value()).toString();
            String ключ = Wellbeing.ощущение(идентификатор);
            if (ключ != null) список.add(Component.translatable(ключ));
        }
        return List.copyOf(список);
    }
}
