package dev.denthe.plaguecore.client;

import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.CipherWords;
import dev.denthe.plaguecore.mc.PlagueNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Тайнопись бессонных — клиентская половина. Спек лора, раздел 5.
 *
 * Страницы написаны обычным русским текстом, никакой разметки внутри
 * книги нет. Руны подставляются здесь, при отрисовке: слово, чей
 * корень сервер прислал закрытым, получает шрифт {@link #ШРИФТ}.
 *
 * Так текст страниц можно переписать в датапаке когда угодно — движок
 * его не разбирает, он только смотрит на список корней.
 *
 * <p>ponytail: спрятано только начертание, сам текст лежит в NBT книги
 * открытым. Восьмерым друзьям этого хватает; закрывать по-настоящему —
 * значит переписывать книгу на сервере для каждого игрока отдельно.
 */
@EventBusSubscriber(modid = PlagueCore.MODID, value = Dist.CLIENT)
public final class SecretText {
    private SecretText() {}

    /** Рунический шрифт: русские буквы на ванильной картинке рун стола зачарований. */
    public static final ResourceLocation ШРИФТ =
        ResourceLocation.fromNamespaceAndPath(PlagueCore.MODID, "sleepless");

    /** Цвет уже раскрытого слова. Редкий фиолетовый акцент палитры чумы. */
    private static final ChatFormatting ЦВЕТ_РАСКРЫТОГО = ChatFormatting.DARK_PURPLE;

    private record Запись(boolean раскрыт, String подсказка) {}

    private static Map<String, Запись> словарь = Map.of();

    /** Пришёл словарь с сервера. Пустой — значит сервер не наш, ничего не трогаем. */
    public static void принять(PlagueNetwork.Words пакет) {
        Map<String, Запись> новый = new LinkedHashMap<>();
        for (PlagueNetwork.Words.Запись з : пакет.записи()) {
            новый.put(з.корень(), new Запись(з.раскрыт(), з.подсказка()));
        }
        словарь = Map.copyOf(новый);
    }

    /** Забыть словарь при выходе из мира, чтобы он не утёк на другой сервер. */
    @SubscribeEvent
    public static void приВыходе(ClientPlayerNetworkEvent.LoggingOut событие) {
        словарь = Map.of();
    }

    /**
     * Подменить тайные слова рунами. Возвращает исходный текст без
     * копирования, если менять нечего, — страница книги перебирается
     * при каждой перелистке.
     */
    public static FormattedText применить(FormattedText исходный) {
        if (словарь.isEmpty() || исходный == null) return исходный;

        MutableComponent итог = Component.empty();
        boolean[] тронуто = {false};

        исходный.visit((стиль, текст) -> {
            for (String кусок : CipherWords.разбить(текст)) {
                итог.append(Component.literal(кусок).setStyle(стильКуска(кусок, стиль, тронуто)));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return тронуто[0] ? итог : исходный;
    }

    private static Style стильКуска(String кусок, Style базовый, boolean[] тронуто) {
        if (кусок.isEmpty() || !CipherWords.букваСлова(кусок.charAt(0))) return базовый;

        String корень = CipherWords.корень(кусок, словарь.keySet());
        if (корень == null) return базовый;

        Запись з = словарь.get(корень);
        тронуто[0] = true;

        if (з.раскрыт()) return базовый.withColor(ЦВЕТ_РАСКРЫТОГО);

        Style руны = базовый.withFont(ШРИФТ);
        if (з.подсказка().isEmpty()) return руны;

        // Подсказка приходит непустой только Летописцу — так решил сервер.
        return руны.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            Component.literal(з.подсказка()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)));
    }
}
