package dev.denthe.plaguecore.mc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.CipherWords;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Тайнопись бессонных — серверная половина. Спек лора, раздел 5.
 *
 * Правило одно: слово из списка показывается рунами, пока кто-нибудь
 * не скажет его вслух в чате. Сказанное открывается сразу всем и
 * навсегда — «оно не любит, когда его помнят вслух».
 *
 * Java не знает ни одного тайного слова. Список лежит в датапаке
 * (data/plaguecore/tainopis/*.json) и перечитывается по /reload, как
 * и всё игровое в этом моде. Текст страниц можно переписать за час
 * до сессии, не пересобирая мод.
 *
 * <p><b>Гейта нет намеренно.</b> Слово откроется, даже если страницу
 * ещё никто не нашёл: достаточно случайно написать его в чат. Защита
 * не в коде, а в подборе слов — шифровать надо то, что восемь человек
 * не скажут между делом («бессонные», «колыбельная», «Онисим»),
 * а не «соль» и «яму».
 * ponytail: гейт «страница должна быть найдена» — если на игре
 * окажется, что слова открываются сами собой; он потребует Хронику.
 */
@EventBusSubscriber(modid = PlagueCore.MODID)
public final class PlagueWords {
    private PlagueWords() {}

    /** Папка датапака со списками слов. */
    public static final String ПАПКА = "tainopis";

    /** Корень, как это слово показывать, и подсказка Летописцу. */
    public record Тайна(String корень, String слово, String подсказка) {}

    private static Map<String, Тайна> словарь = Map.of();

    public static Set<String> корни() { return словарь.keySet(); }

    public static Тайна тайна(String корень) { return словарь.get(корень); }

    // загрузка списка из датапака

    @SubscribeEvent
    public static void добавитьЗагрузчик(AddReloadListenerEvent событие) {
        событие.addListener(new SimpleJsonResourceReloadListener(new Gson(), ПАПКА) {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> файлы,
                                 ResourceManager менеджер, ProfilerFiller профайлер) {
                разобрать(файлы);
            }
        });
    }

    private static void разобрать(Map<ResourceLocation, JsonElement> файлы) {
        Map<String, Тайна> собрано = new LinkedHashMap<>();
        List<String> жалобы = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> файл : файлы.entrySet()) {
            if (!файл.getValue().isJsonObject()) {
                жалобы.add(файл.getKey() + ": ожидался объект");
                continue;
            }
            JsonElement список = файл.getValue().getAsJsonObject().get("слова");
            if (список == null || !список.isJsonArray()) {
                жалобы.add(файл.getKey() + ": нет массива слова");
                continue;
            }
            for (JsonElement э : список.getAsJsonArray()) {
                if (!э.isJsonObject()) {
                    жалобы.add(файл.getKey() + ": в списке не объект");
                    continue;
                }
                JsonObject о = э.getAsJsonObject();
                String корень = строка(о, "корень");
                if (корень == null) {
                    жалобы.add(файл.getKey() + ": запись без поля корень");
                    continue;
                }
                корень = CipherWords.нормализовать(корень);
                if (корень.length() < CipherWords.МИН_ДЛИНА_КОРНЯ) {
                    жалобы.add(файл.getKey() + ": корень " + корень + " короче "
                        + CipherWords.МИН_ДЛИНА_КОРНЯ + " букв, поймает пол-текста");
                    continue;
                }
                String слово = строка(о, "слово");
                String подсказка = строка(о, "подсказка");
                собрано.put(корень, new Тайна(корень,
                    слово != null ? слово : корень,
                    подсказка != null ? подсказка : ""));
            }
        }

        словарь = Map.copyOf(собрано);
        for (String ж : жалобы) PlagueCore.LOG.warn("тайнопись: {}", ж);
        PlagueCore.LOG.info("тайнопись: загружено корней — {}", словарь.size());
    }

    private static String строка(JsonObject о, String ключ) {
        JsonElement э = о.get(ключ);
        return э != null && э.isJsonPrimitive() ? э.getAsString() : null;
    }

    // угадывание вслух

    @SubscribeEvent
    public static void услышать(ServerChatEvent событие) {
        ServerPlayer игрок = событие.getPlayer();
        MinecraftServer сервер = игрок.server;
        ServerLevel мир = сервер.getLevel(Level.OVERWORLD);
        if (мир == null || словарь.isEmpty()) return;

        PlagueState состояние = PlagueState.get(мир);
        List<String> угаданные = CipherWords.угаданные(событие.getRawText(), словарь.keySet());

        boolean естьНовое = false;
        for (String корень : угаданные) {
            if (состояние.раскрыть(корень)) {
                естьНовое = true;
                объявить(сервер, игрок.getName().getString(), словарь.get(корень));
            }
        }
        if (естьНовое) синхронизироватьВсех(сервер);
    }

    private static void объявить(MinecraftServer сервер, String кто, Тайна тайна) {
        Component строка = Component.empty()
            .append(Component.literal(кто).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" назвал вслух: ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(тайна.слово()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(". Оно вздрогнуло.").withStyle(ChatFormatting.DARK_GRAY));

        for (ServerPlayer п : сервер.getPlayerList().getPlayers()) {
            п.sendSystemMessage(строка);
            п.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 0.6f, 0.7f);
        }
    }

    // раздача клиенту

    @SubscribeEvent
    public static void приВходе(PlayerEvent.PlayerLoggedInEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) синхронизировать(игрок);
    }

    /** Послать одному игроку весь словарь. Подсказки — только Летописцу. */
    public static void синхронизировать(ServerPlayer игрок) {
        ServerLevel мир = игрок.server.getLevel(Level.OVERWORLD);
        if (мир == null) return;

        Set<String> раскрытые = PlagueState.get(мир).раскрытыеСлова();
        boolean сПодсказками = ClassBridge.летописец(игрок);

        List<PlagueNetwork.Words.Запись> записи = new ArrayList<>(словарь.size());
        for (Тайна т : словарь.values()) {
            записи.add(new PlagueNetwork.Words.Запись(
                т.корень(),
                раскрытые.contains(т.корень()),
                сПодсказками ? т.подсказка() : ""));
        }
        PlagueNetwork.отправитьСлова(игрок, записи);
    }

    public static void синхронизироватьВсех(MinecraftServer сервер) {
        for (ServerPlayer п : сервер.getPlayerList().getPlayers()) синхронизировать(п);
    }
}
