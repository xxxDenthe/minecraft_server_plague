package dev.denthe.plaguecore.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denthe.plaguecore.PlagueCore;
import dev.denthe.plaguecore.core.Marks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Пометки Мастера игры на отдельно взятом игроке.
 * Спек «Пометки Мастера игры в экране здоровья», раздел 6.3.
 *
 * Хранится ванильным Data Attachment — тем же способом, что
 * {@link PlayerPlagueData}: NeoForge сам кладёт это в файл игрока
 * и сам достаёт при входе, своего кода сохранения не пишем.
 *
 * copyOnDeath здесь намеренно НЕ стоит, в отличие от чумы. Решение
 * владельца: пометка живёт до снятия ГМ или до смерти. Вложение без
 * этого флага не переживает смерть само собой, поэтому кода очистки
 * не нужно вовсе.
 */
// bus не указываем: в 21.1 шина определяется по типу события,
// PlayerLoggedInEvent — игровое
@EventBusSubscriber(modid = PlagueCore.MODID)
public class PlayerHealthMarks {

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PlagueCore.MODID);

    /** Пометки в порядке добавления. */
    public List<Marks.Пометка> пометки = new ArrayList<>();

    /** Следующий свободный номер. Хранится, иначе после перезахода номера повторятся. */
    public int следующийId = 1;

    public PlayerHealthMarks() {}

    public PlayerHealthMarks(List<Marks.Пометка> пометки, int следующийId) {
        this.пометки = new ArrayList<>(пометки);
        this.следующийId = следующийId;
    }

    /** Кодек одной пометки. Им же пользуется команда и запись в файл игрока. */
    public static final Codec<Marks.Пометка> КОДЕК_ПОМЕТКИ = RecordCodecBuilder.create(и -> и.group(
        Codec.INT.fieldOf("id").forGetter(Marks.Пометка::id),
        Codec.STRING.fieldOf("place").forGetter(п -> п.место().name()),
        Codec.BOOL.fieldOf("replaces").forGetter(Marks.Пометка::заменяет),
        Codec.STRING.optionalFieldOf("preset", "").forGetter(Marks.Пометка::заготовка),
        Codec.STRING.optionalFieldOf("text", "").forGetter(Marks.Пометка::текст)
    ).apply(и, (id, место, заменяет, заготовка, текст) -> {
        Marks.Место м = Marks.место(место);
        // Испорченное место не роняет загрузку игрока: пометка просто
        // уезжает в «Самочувствие», и ГМ снимет её руками.
        return new Marks.Пометка(id, м == null ? Marks.Место.OVERALL : м,
            заменяет, заготовка, текст);
    }));

    public static final Codec<PlayerHealthMarks> CODEC = RecordCodecBuilder.create(и -> и.group(
        КОДЕК_ПОМЕТКИ.listOf().fieldOf("marks").forGetter(д -> д.пометки),
        Codec.INT.fieldOf("nextId").forGetter(д -> д.следующийId)
    ).apply(и, PlayerHealthMarks::new));

    public static final Supplier<AttachmentType<PlayerHealthMarks>> ПОМЕТКИ =
        ВЛОЖЕНИЯ.register("player_health_marks", () -> AttachmentType
            .builder(PlayerHealthMarks::new)
            .serialize(CODEC)
            .build());

    public static PlayerHealthMarks данные(Player игрок) {
        return игрок.getData(ПОМЕТКИ.get());
    }

    /** Пометки игрока, только для чтения. */
    public static List<Marks.Пометка> список(Player игрок) {
        return List.copyOf(данные(игрок).пометки);
    }

    /**
     * Добавить пометку. Пределы проверяются здесь, а не в экране: экран
     * можно подменить, сервер — нет.
     *
     * @return номер новой пометки или −1, если предел уже выбран
     *         или обе строки пусты
     */
    public static int добавить(Player игрок, Marks.Место место, boolean заменяет,
                               String заготовка, String текст) {
        PlayerHealthMarks д = данные(игрок);
        if (д.пометки.size() >= Marks.ПРЕДЕЛ) return -1;

        String чистыйТекст = Marks.чистить(текст);
        boolean естьЗаготовка = заготовка != null && !заготовка.isEmpty();
        if (!естьЗаготовка && чистыйТекст.isEmpty()) return -1;

        int id = д.следующийId++;
        д.пометки.add(new Marks.Пометка(id, место, заменяет,
            естьЗаготовка ? заготовка.toLowerCase(Locale.ROOT) : "",
            естьЗаготовка ? "" : чистыйТекст));
        игрок.setData(ПОМЕТКИ.get(), д);
        return id;
    }

    /** @return {@code true}, если такая пометка была */
    public static boolean удалить(Player игрок, int id) {
        PlayerHealthMarks д = данные(игрок);
        boolean было = д.пометки.removeIf(п -> п.id() == id);
        if (было) игрок.setData(ПОМЕТКИ.get(), д);
        return было;
    }

    /** Вернуть экран к тому, что диктуют показатели: снять все пометки. */
    public static void очистить(Player игрок) {
        PlayerHealthMarks д = данные(игрок);
        д.пометки.clear();
        игрок.setData(ПОМЕТКИ.get(), д);
    }

    /**
     * При входе игрок получает свои пометки: иначе его экран будет
     * чистым до первой правки ГМ, хотя пометки лежат в сохранении.
     */
    @SubscribeEvent
    public static void приВходе(PlayerEvent.PlayerLoggedInEvent событие) {
        if (событие.getEntity() instanceof ServerPlayer игрок) {
            PlagueNetwork.отправитьПометки(игрок, игрок, (byte) 0);
        }
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
