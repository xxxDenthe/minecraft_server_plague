package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Что человек за собой заметил. Спек интерфейса, раздел 11.
 *
 * Сам игрок сюда ничего не пишет — записи рождаются из событий.
 * Кольцо на двенадцать строк, живёт до выхода из мира и обнуляется
 * при перезаходе: память тут короткая, это не дневник.
 *
 * Пишется и с закрытым экраном. Иначе журнал будет пуст ровно в тот
 * момент, когда его впервые открывают, и вкладка окажется бессмысленной.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class HealthMemory {
    private HealthMemory() {}

    /** Сколько записей помним. Больше — вкладка перестаёт читаться. */
    private static final int ДЛИНА = 12;

    private record Запись(String время, String ключ) {}

    private static final Deque<Запись> кольцо = new ArrayDeque<>();

    /** Записать событие. Ключ — из языкового файла, текста тут нет. */
    public static void записать(String ключ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long сутки = mc.level.getDayTime() % 24000L;
        int час = (int) ((сутки / 1000L + 6L) % 24L);
        int минута = (int) ((сутки % 1000L) * 60L / 1000L);
        String время = String.format("%02d:%02d", час, минута);

        if (кольцо.size() >= ДЛИНА) кольцо.removeFirst();
        кольцо.addLast(new Запись(время, ключ));
    }

    /** Готовые строки журнала, снизу — самое свежее. */
    public static List<Component> записи() {
        List<Component> список = new ArrayList<>(кольцо.size());
        for (Запись з : кольцо) {
            список.add(Component.literal(з.время() + "  ")
                .append(Component.translatable(з.ключ())));
        }
        return список;
    }

    public static void забыть() {
        кольцо.clear();
    }

    /**
     * Стадия сменилась. Зовёт {@link PlagueClientAccess} при получении
     * пакета: это единственное место, где клиент узнаёт о смене.
     */
    public static void приСмене(int была, int стала) {
        if (стала > была) записать("plaguecore.health.memory.worse");
        else if (стала < была) записать("plaguecore.health.memory.better");
    }

    /** Отвар выпит. Ловим окончание использования — не начало. */
    @SubscribeEvent
    public static void приДопитом(LivingEntityUseItemEvent.Finish событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || событие.getEntity() != mc.player) return;
        String имя = событие.getItem().getItem().toString();
        if (имя.contains("plague_brew") || имя.contains("clerics_brew")) {
            записать("plaguecore.health.memory.brew");
        }
    }

    /** Выход из мира стирает память: она живёт только эту сессию. */
    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        забыть();
    }
}
