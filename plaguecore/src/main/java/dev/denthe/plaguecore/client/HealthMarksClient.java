package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Marks;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Пометки Мастера игры на клиенте: свои и тех, кого осматриваем.
 * Спек «Пометки Мастера игры», раздел 8.
 *
 * Версия растёт при каждом приёме пакета. По ней экран понимает, что
 * готовые {@code Component} пора пересобрать: собирать их в кадре
 * нельзя — то же правило, что в разделе 14 базового спека.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthMarksClient {
    private HealthMarksClient() {}

    private static List<Marks.Пометка> свои = List.of();
    private static final Map<Integer, List<Marks.Пометка>> чужие = new HashMap<>();
    private static int версия;

    public static List<Marks.Пометка> свои() { return свои; }

    /** Пометки соседа. Пусто, если сервер про него ничего не присылал. */
    public static List<Marks.Пометка> чужие(int сущность) {
        return чужие.getOrDefault(сущность, List.of());
    }

    /** Растёт при каждом изменении. Экран держит её у своего кэша. */
    public static int версия() { return версия; }

    public static void принять(PlagueNetwork.MarkList пакет) {
        Minecraft mc = Minecraft.getInstance();
        boolean моё = mc.player != null && mc.player.getId() == пакет.сущность();
        if (моё) свои = пакет.пометки();
        else чужие.put(пакет.сущность(), пакет.пометки());
        версия++;

        if (пакет.режим() == 1) HealthEditScreen.принять(пакет);
    }

    /** Чужие пометки живут только эту сессию: мир сменился — забыли. */
    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        забыть();
    }

    public static void забыть() {
        свои = List.of();
        чужие.clear();
        версия++;
    }
}
