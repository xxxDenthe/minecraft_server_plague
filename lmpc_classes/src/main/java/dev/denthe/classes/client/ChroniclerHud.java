package dev.denthe.classes.client;

import dev.denthe.classes.ClassNetwork;
import dev.denthe.classes.LmpcClasses;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Обзор Летописца — маленькая летопись в углу экрана: кто рядом
 * и насколько заражён. Спек, раздел 7: «подсказка должна показывать
 * точное число уровня вместо округлённой строки».
 *
 * Плагина Jade, на который рассчитывал спек, в проекте нет — тот,
 * что лежал в `plaguecore`, был нерабочей заглушкой и удалён
 * 2026-09-04. Вместо интеграции с чужим модом — свой HUD на
 * ванильном {@code RenderGuiEvent}: ни зависимости, ни чужого API.
 *
 * Числа целиком приходят с сервера ({@link ClassNetwork.Insight}):
 * заражённость живёт в чужом вложении `plaguecore` и на клиент
 * не синкается, придумать её здесь не из чего. Поэтому же панель
 * молча пуста, если `plaguecore` не стоит.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = LmpcClasses.MODID)
public final class ChroniclerHud {
    private ChroniclerHud() {}

    /** Свежесть данных: сервер шлёт раз в секунду, три секунды тишины — прячем. */
    private static final int ЖИВЁТ_ТИКОВ = 60;

    private static List<ClassNetwork.Insight.Запись> записи = List.of();
    private static int уровеньЧанка = -1;
    private static long обновленоТик = Long.MIN_VALUE;

    /** Пришёл свежий обзор. Зовётся из {@code ClassNetwork} уже в потоке клиента. */
    public static void принять(ClassNetwork.Insight пакет) {
        записи = пакет.записи();
        уровеньЧанка = пакет.уровеньЧанка();
        Minecraft mc = Minecraft.getInstance();
        обновленоТик = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void рисовать(RenderGuiEvent.Post событие) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (записи.isEmpty() || mc.level.getGameTime() - обновленоТик > ЖИВЁТ_ТИКОВ) return;

        GuiGraphics графика = событие.getGuiGraphics();
        Component заголовок = Component.translatable("hud.lmpc_classes.chronicle");

        Component местность = строкаМестности();

        int ширина = Math.max(mc.font.width(заголовок), mc.font.width(местность));
        for (ClassNetwork.Insight.Запись з : записи) {
            ширина = Math.max(ширина, mc.font.width(строка(з)));
        }
        ширина += 8;
        int высота = 8 + (записи.size() + 2) * (mc.font.lineHeight + 1);

        графика.fill(4, 4, 4 + ширина, 4 + высота, 0x88120E08);
        графика.renderOutline(4, 4, ширина, высота, 0x66B8942F);

        int y = 8;
        графика.drawString(mc.font, заголовок.copy().withStyle(ChatFormatting.BOLD),
            8, y, ClassStyle.цвет(dev.denthe.classes.PlayerClassData.Класс.CHRONICLER), false);
        y += mc.font.lineHeight + 1;

        for (ClassNetwork.Insight.Запись з : записи) {
            графика.drawString(mc.font, строка(з), 8, y, цветСтадии(з.стадия(), з.этоЯ()), false);
            y += mc.font.lineHeight + 1;
        }

        графика.drawString(mc.font, местность, 8, y, 0xA89878, false);
    }

    /**
     * Заражение чанка, на который Летописец смотрит, точным числом.
     * Это и есть обещанная спеком замена «округлённой строки» Jade:
     * плагина к Jade нет — он потребовал бы жёсткой зависимости на
     * чужой API, а Jade в этом паке уже один раз ронял клиент
     * (заметка 2026-09-05-jade-otkachen-radi-zhazhdy).
     */
    private static Component строкаМестности() {
        return уровеньЧанка < 0
            ? Component.translatable("hud.lmpc_classes.chunk_unknown")
            : Component.translatable("hud.lmpc_classes.chunk", уровеньЧанка);
    }

    /** «Ник — стадия 2 · 41». Неизвестные числа показываем прочерком, а не нулём. */
    private static Component строка(ClassNetwork.Insight.Запись з) {
        String имя = з.этоЯ() ? "▸ " + з.имя() : з.имя();
        if (з.стадия() < 0) {
            return Component.translatable("hud.lmpc_classes.unknown", имя);
        }
        return Component.translatable("hud.lmpc_classes.entry",
            имя, з.стадия(), String.format("%.0f", Math.max(0f, з.заражённость())));
    }

    /**
     * Цвет строки — от стадии, а не от того, кто это: Летописцу важно
     * с одного взгляда увидеть, кому хуже всех. Своя строка только
     * помечена стрелкой.
     */
    private static int цветСтадии(int стадия, boolean этоЯ) {
        int цвет = switch (Math.min(4, Math.max(0, стадия))) {
            case 0 -> 0x7FA05A;
            case 1 -> 0xC8B44A;
            case 2 -> 0xC8843A;
            case 3 -> 0xB8523A;
            default -> 0x8A2A2A;
        };
        return этоЯ ? осветлить(цвет) : цвет;
    }

    private static int осветлить(int цвет) {
        int r = Math.min(255, ((цвет >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((цвет >> 8) & 0xFF) + 40);
        int b = Math.min(255, (цвет & 0xFF) + 40);
        return (r << 16) | (g << 8) | b;
    }
}
