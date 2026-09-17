package dev.denthe.classes.client;

import dev.denthe.classes.ClassNetwork;
import dev.denthe.classes.LmpcClasses;
import dev.denthe.classes.PlayerClassData;
import dev.denthe.classes.Trend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обзор Летописца — маленькая летопись в углу экрана: кто рядом и куда
 * катится его здоровье. Спек классов, раздел 7.
 *
 * <p><b>Точных чисел здесь больше нет.</b> До 0.21.0 строка выглядела
 * как «Ник — стадия 2 · 41»: Летописец знал о чужой болезни больше
 * самого больного, осмотр Клирика рядом с этим терял смысл, а правило
 * интерфейса здоровья «игроку не показывается ни стадия, ни проценты»
 * держалось только до тех пор, пока в партии нет Летописца. Теперь
 * панель показывает направление ({@link Trend}) — «хуже», «легче»,
 * «без перемен», — и ни стадии, ни очков в пакете уже не приходит.
 *
 * <p><b>Тир мастерства даёт память.</b> Сервер шлёт только тех, кто
 * сейчас в радиусе, и одно число: сколько держать ушедшего. Держит
 * его клиент — это его же собственные, только что полученные данные,
 * и гонять их по сети второй раз незачем.
 *
 * <p>Оформление — то же, что у планшета осмотра на клавише `Y`:
 * кожа, латунь и клеймёный шрифт из {@link LazaretHud}. Панель
 * молча пуста, если `plaguecore` не стоит: числа считает он.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = LmpcClasses.MODID)
public final class ChroniclerHud {
    private ChroniclerHud() {}

    /** Свежесть данных: сервер шлёт раз в секунду, три секунды тишины — прячем. */
    private static final int ЖИВЁТ_ТИКОВ = 60;

    /**
     * Строка летописи на клиенте.
     *
     * @param виден тик, когда человека видели в последний раз
     */
    private record Строка(int направление, boolean этоЯ, long виден) {}

    /** Порядок вставки — порядок строк: свой первым, дальше как пришли. */
    private static final Map<String, Строка> ЖУРНАЛ = new LinkedHashMap<>();

    private static int уровеньЧанка = -1;
    private static int памятьТиков;
    private static long обновленоТик = Long.MIN_VALUE;

    /** Пришёл свежий обзор. Зовётся из {@code ClassNetwork} уже в потоке клиента. */
    public static void принять(ClassNetwork.Insight пакет) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ЖУРНАЛ.clear();
            обновленоТик = Long.MIN_VALUE;
            return;
        }

        long сейчас = mc.level.getGameTime();
        уровеньЧанка = пакет.уровеньЧанка();
        памятьТиков = пакет.памятьТиков();
        обновленоТик = сейчас;

        for (ClassNetwork.Insight.Запись з : пакет.записи()) {
            ЖУРНАЛ.put(з.имя(), new Строка(з.направление(), з.этоЯ(), сейчас));
        }
        // Забытые уходят молча: без этого журнал рос бы весь сеанс,
        // а на первом тире (память 0) он и вовсе обязан очищаться сразу.
        Iterator<Map.Entry<String, Строка>> обход = ЖУРНАЛ.entrySet().iterator();
        while (обход.hasNext()) {
            if (сейчас - обход.next().getValue().виден() > памятьТиков) обход.remove();
        }
    }

    @SubscribeEvent
    public static void рисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (ЖУРНАЛ.isEmpty() || mc.level.getGameTime() - обновленоТик > ЖИВЁТ_ТИКОВ) return;

        long сейчас = mc.level.getGameTime();
        Component заголовок = LazaretHud.клеймо("hud.lmpc_classes.chronicle");
        Component местность = строкаМестности();

        List<Component> строки = new ArrayList<>(ЖУРНАЛ.size());
        List<Integer> цвета = new ArrayList<>(ЖУРНАЛ.size());
        for (Map.Entry<String, Строка> запись : ЖУРНАЛ.entrySet()) {
            boolean ушёл = сейчас - запись.getValue().виден() > ЖИВЁТ_ТИКОВ;
            строки.add(строка(запись.getKey(), запись.getValue(), ушёл, сейчас));
            цвета.add(ушёл ? LazaretHud.ТУСКЛЫЙ : цвет(запись.getValue().направление()));
        }

        GuiGraphics графика = событие.getGuiGraphics();
        int ширина = Math.max(mc.font.width(заголовок), mc.font.width(местность));
        for (Component с : строки) ширина = Math.max(ширина, mc.font.width(с));

        int шаг = mc.font.lineHeight + 1;
        ширина += LazaretHud.ПОЛЕ * 2;
        int высота = LazaretHud.ПОЛЕ * 2 + (строки.size() + 2) * шаг;

        // Ниже строки самочувствия `plaguecore`: она живёт в том же
        // левом верхнем углу, и панель на четвёртом пикселе легла бы
        // прямо поверх неё.
        int x = 4, y = 4 + mc.font.lineHeight + 4;
        LazaretHud.панель(графика, x, y, ширина, высота);

        int текстX = x + LazaretHud.ПОЛЕ;
        int текстY = y + LazaretHud.ПОЛЕ;
        графика.drawString(mc.font, заголовок, текстX, текстY,
            ClassStyle.цвет(PlayerClassData.Класс.CHRONICLER), false);
        текстY += шаг;

        for (int i = 0; i < строки.size(); i++) {
            графика.drawString(mc.font, строки.get(i), текстX, текстY, цвета.get(i), false);
            текстY += шаг;
        }

        графика.drawString(mc.font, местность, текстX, текстY, LazaretHud.ТУСКЛЫЙ, false);
    }

    /**
     * Заражение чанка, на который Летописец смотрит, точным числом.
     * Число местности осталось точным намеренно: оно про землю, а не
     * про человека, и ниши Клирика не задевает — тот смотрит тело.
     */
    private static Component строкаМестности() {
        return уровеньЧанка < 0
            ? Component.translatable("hud.lmpc_classes.chunk_unknown")
            : Component.translatable("hud.lmpc_classes.chunk", уровеньЧанка);
    }

    /** «Ник — хуже» и, у забытого журналом, ещё и «видел 3 мин назад». */
    private static Component строка(String имя, Строка данные, boolean ушёл, long сейчас) {
        String подпись = данные.этоЯ() ? "▸ " + имя : имя;
        Component куда = Component.translatable(ключНаправления(данные.направление()));
        if (!ушёл) {
            return Component.translatable("hud.lmpc_classes.entry", подпись, куда);
        }
        long минут = Math.max(1, (сейчас - данные.виден()) / 1200);
        return Component.translatable("hud.lmpc_classes.entry_seen", подпись, куда, минут);
    }

    private static String ключНаправления(int направление) {
        return switch (направление) {
            case Trend.ХУЖЕ -> "hud.lmpc_classes.trend.worse";
            case Trend.ЛЕГЧЕ -> "hud.lmpc_classes.trend.better";
            case Trend.БЕЗ_ПЕРЕМЕН -> "hud.lmpc_classes.trend.same";
            default -> "hud.lmpc_classes.trend.unknown";
        };
    }

    /**
     * Цвет строки — от направления. Палитра проекта: тревога уходит
     * в бурый, облегчение — в приглушённый зелёный, покой — костяной.
     */
    private static int цвет(int направление) {
        return switch (направление) {
            case Trend.ХУЖЕ -> 0xFFB8523A;
            case Trend.ЛЕГЧЕ -> 0xFF7FA05A;
            case Trend.БЕЗ_ПЕРЕМЕН -> LazaretHud.КОСТЬ;
            default -> LazaretHud.ТУСКЛЫЙ;
        };
    }
}
