package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Проба записи: дев-клиент сам берёт записи и открывает одну из них.
 *
 * Нужна потому, что посмотреть на экран чтения иначе нечем. Синтетические
 * нажатия до Minecraft не доходят ни в одном лаунчере — ни мышь, ни
 * клавиатура, — а аргумент запуска доходит
 * (docs/pamyat/proverka-gui-cherez-dev-klient.md). Без пробы правка
 * вёрстки страницы проверяется только на глаз владельца.
 *
 * Заодно это сквозная проверка: проба зовёт настоящую функцию выдачи,
 * поэтому разбор SNBT в mcfunction, компонент книги, шрифт тайнописи
 * и врезка расшифровки проходят один и тот же путь, что и в игре.
 *
 * Спит, пока клиент не запущен со свойством:
 * {@code ./gradlew runClient -PquickPlay="New World" -PprobaZapis=0},
 * где число — ячейка инвентаря, запись из которой надо открыть
 * (0 — первая шифрованная страница, 12 — первая обычная записка).
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class ArchiveRecordProbe {
    private ArchiveRecordProbe() {}

    private static final String СВОЙСТВО = "plaguecore.proba.zapis";

    /** Сколько тиков ждать после входа: мир должен догрузиться. */
    private static final int ПАУЗА = 100;

    /** Через столько тиков проба листает страницу — чтобы снимок застал разные. */
    private static final int ШАГ = 40;

    private static int обратныйСчёт = -1;
    private static int ячейка;
    private static boolean выдано;

    @SubscribeEvent
    public static void приВходе(ClientPlayerNetworkEvent.LoggingIn событие) {
        String значение = System.getProperty(СВОЙСТВО);
        if (значение == null) return;
        try {
            ячейка = Integer.parseInt(значение.trim());
        } catch (NumberFormatException ignored) {
            ячейка = 0;
        }
        обратныйСчёт = ПАУЗА;
        выдано = false;
    }

    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        обратныйСчёт = -1;
    }

    @SubscribeEvent
    public static void тик(ClientTickEvent.Post событие) {
        if (обратныйСчёт < 0 || --обратныйСчёт > 0) return;

        Minecraft клиент = Minecraft.getInstance();
        MinecraftServer сервер = клиент.getSingleplayerServer();
        if (сервер == null || клиент.player == null) {
            обратныйСчёт = -1;
            PlagueCore.LOG.warn("проба записи: нужен одиночный мир");
            return;
        }

        if (!выдано) {
            выдано = true;
            обратныйСчёт = ШАГ;
            сервер.execute(() -> {
                ServerPlayer игрок = сервер.getPlayerList().getPlayers().getFirst();
                var источник = игрок.createCommandSourceStack()
                    .withSuppressedOutput().withPermission(2);
                // Инвентарь чистится обязательно: мир дев-клиента живёт
                // между прогонами, и без этого в ячейке лежит запись
                // с текстом прошлой сборки — а выглядит это как «правка
                // не доехала».
                сервер.getCommands().performPrefixedCommand(источник, "clear @s");
                сервер.getCommands().performPrefixedCommand(
                    источник, "function plaguecore:zapisi/vse");
            });
            return;
        }

        if (клиент.screen instanceof ArchiveRecordScreen экран) {
            экран.pageForward();   // protected, но экран лежит в этом же пакете
            обратныйСчёт = ШАГ;
            return;
        }

        ItemStack запись = клиент.player.getInventory().getItem(ячейка);
        if (запись.isEmpty()) {
            обратныйСчёт = -1;
            PlagueCore.LOG.warn("проба записи: в ячейке {} пусто", ячейка);
            return;
        }
        // Кафедра и резная полка — единственный технический риск замысла:
        // ванильные блоки пускают к себе только предметы из этих двух
        // тегов (LecternBlock.useItemOn, ChiseledBookShelfBlock.useItemOn),
        // а дальше уже не спрашивают, что за предмет. Поэтому проверяется
        // именно принадлежность тегам, и проверяется в игре: теги живут
        // в датапаке и в юнит-тест не заглядывают.
        PlagueCore.LOG.info("проба записи: кафедра принимает — {}, полка — {}",
            запись.is(ItemTags.LECTERN_BOOKS), запись.is(ItemTags.BOOKSHELF_BOOKS));

        ArchiveRecordScreen.открыть(запись);
        обратныйСчёт = ШАГ;
        // Метка латиницей: её ищет в логе launcher/tools/dev-zapis.ps1.
        PlagueCore.LOG.info("proba-zapis-ready: открыта «{}»",
            запись.getHoverName().getString());
    }
}
