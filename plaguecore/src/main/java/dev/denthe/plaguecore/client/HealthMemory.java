package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Готовые строки, старое к новому. Круг правок 1: {@code записи()}
     * звалась из кадрового пути ({@code render → правая → MEMORY}) и
     * на каждый вызов собирала новый список и новые {@code Component} —
     * тот же дефект, что уже чинили в задачах 8 и 9 (раздел 14 спеки).
     * Теперь {@code Component} строится один раз, в {@code записать()},
     * а {@code записи()} отдаёт готовый неизменяемый вид без аллокаций.
     */
    private static final List<Component> кольцо = new ArrayList<>(ДЛИНА);
    private static final List<Component> вид = Collections.unmodifiableList(кольцо);

    /** Записать событие. Ключ — из языкового файла, текста тут нет. */
    public static void записать(String ключ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long сутки = mc.level.getDayTime() % 24000L;
        int час = (int) ((сутки / 1000L + 6L) % 24L);
        int минута = (int) ((сутки % 1000L) * 60L / 1000L);
        String время = String.format("%02d:%02d", час, минута);

        if (кольцо.size() >= ДЛИНА) кольцо.remove(0);
        кольцо.add(Component.literal(время + "  ").append(Component.translatable(ключ)));
    }

    /** Готовые строки журнала, снизу — самое свежее. Ничего не создаёт. */
    public static List<Component> записи() {
        return вид;
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
