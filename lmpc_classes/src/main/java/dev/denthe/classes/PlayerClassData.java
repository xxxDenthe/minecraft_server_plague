package dev.denthe.classes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Класс игрока. Спек — 2026-09-04-klassy-design.md, раздел 2 и 10.
 *
 * Свой мод, свой Data Attachment: `plaguecore` не трогаем, класс живёт
 * отдельно от заражённости.
 *
 * **Вложение синхронизируется на клиент** ({@code .sync(STREAM_CODEC)}).
 * До 0.6.0 его не было, и это тихо ломало весь клиентский UI: вложения
 * по умолчанию живут только на сервере, поэтому гримуар читал у себя
 * пустую копию — всегда «без класса» и «мастерство 0», чем бы игрок
 * ни играл. Синк идёт всем, кто видит игрока, а не только ему самому:
 * так Клирик под прицелом знает класс цели, не спрашивая сервер.
 *
 * Вложение мутабельное, само себя не рассылает: после каждой правки
 * поля на сервере нужен {@link #синхронизировать(Player)}.
 */
public class PlayerClassData {

    public enum Класс { NONE, CLERIC, SMITH, FARMER, CHRONICLER }

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LmpcClasses.MODID);

    /** Текущий класс. NONE — без класса, чистые руки. */
    public Класс класс = Класс.NONE;

    /** Мировой тик последней смены. −1 — ни разу не менял, кулдаун не действует. */
    public long последняяСменаТик = -1L;

    /** Мастерство текущего класса, 0..{@link ClassMastery#МАКСИМУМ}. Спек, раздел 2.1. */
    public int мастерство = 0;

    /** Мировой тик, когда снова можно пить улучшенный отвар. −1 — готов сейчас. */
    public long отварГотовТик = -1L;

    /** Мировой тик, когда Летописец снова может сделать снимок. −1 — готов сейчас. */
    public long снимокГотовТик = -1L;

    public PlayerClassData() {}

    public PlayerClassData(
            Класс класс, long последняяСменаТик, int мастерство,
            long отварГотовТик, long снимокГотовТик) {
        this.класс = класс;
        this.последняяСменаТик = последняяСменаТик;
        this.мастерство = мастерство;
        this.отварГотовТик = отварГотовТик;
        this.снимокГотовТик = снимокГотовТик;
    }

    public static final Codec<PlayerClassData> CODEC = RecordCodecBuilder.create(и -> и.group(
        Codec.STRING.xmap(PlayerClassData::классПоИмени, Класс::name).fieldOf("class").forGetter(д -> д.класс),
        Codec.LONG.fieldOf("lastSwitchTick").forGetter(д -> д.последняяСменаТик),
        Codec.INT.fieldOf("mastery").forGetter(д -> д.мастерство),
        Codec.LONG.fieldOf("brewReadyTick").forGetter(д -> д.отварГотовТик),
        // Поле появилось в 0.7.0. Необязательное — иначе разбор старого
        // сейва падал бы, а класс и мастерство игрока в нём настоящие.
        Codec.LONG.optionalFieldOf("snapshotReadyTick", -1L).forGetter(д -> д.снимокГотовТик)
    ).apply(и, PlayerClassData::new));

    /**
     * Сетевой кодек синка. Пишем руками, а не через {@code
     * ByteBufCodecs.fromCodec(CODEC)}: пять скалярных полей дешевле
     * гонять числами, чем NBT-деревом, а порядковый номер класса
     * при чтении всё равно приходится зажимать в диапазон — пакет
     * приходит с сервера, но версии модов могут разойтись.
     */
    public static final StreamCodec<ByteBuf, PlayerClassData> STREAM_CODEC = StreamCodec.of(
        (буфер, д) -> {
            буфер.writeByte(д.класс.ordinal());
            буфер.writeLong(д.последняяСменаТик);
            буфер.writeInt(д.мастерство);
            буфер.writeLong(д.отварГотовТик);
            буфер.writeLong(д.снимокГотовТик);
        },
        буфер -> new PlayerClassData(
            классПоНомеру(буфер.readByte()),
            буфер.readLong(),
            буфер.readInt(),
            буфер.readLong(),
            буфер.readLong()));

    /** Неизвестное имя класса (старый сейв, чужая версия) — не краш, а NONE. */
    private static Класс классПоИмени(String имя) {
        try {
            return Класс.valueOf(имя);
        } catch (IllegalArgumentException e) {
            return Класс.NONE;
        }
    }

    private static Класс классПоНомеру(int номер) {
        Класс[] все = Класс.values();
        return номер >= 0 && номер < все.length ? все[номер] : Класс.NONE;
    }

    /**
     * Сменить класс. Мастерство старого класса срезается до доли
     * {@code keepFraction} (спек, раздел 2.1 — умолчание 0.3), а не
     * обнуляется: решение должно стоить, но не карать за пробу.
     */
    public void сменитьКласс(Класс новый, long тик, double keepFraction) {
        класс = новый;
        последняяСменаТик = тик;
        мастерство = (int) Math.floor(мастерство * Math.max(0.0, Math.min(1.0, keepFraction)));
    }

    /** Тир текущего класса, 1..3. Пороги — из конфига (спек, раздел 11). */
    public int тир() {
        return ClassMastery.тир(мастерство, ClassesConfig.порогТира2(), ClassesConfig.порогТира3());
    }

    /** Сколько мастерства до следующего тира; −1, если тир уже третий. */
    public int доСледующегоТира() {
        int порог = ClassMastery.следующийПорог(
            мастерство, ClassesConfig.порогТира2(), ClassesConfig.порогТира3());
        return порог < 0 ? -1 : порог - мастерство;
    }

    public static final Supplier<AttachmentType<PlayerClassData>> КЛАСС =
        ВЛОЖЕНИЯ.register("player_class", () -> AttachmentType
            .builder(PlayerClassData::new)
            .serialize(CODEC)
            .sync(STREAM_CODEC)
            .copyOnDeath()
            .build());

    /** Данные игрока. Вложение создаётся само при первом обращении. */
    public static PlayerClassData данные(Player игрок) {
        return игрок.getData(КЛАСС.get());
    }

    /**
     * Разослать изменённые данные клиентам. Вызывать после каждой
     * правки полей на сервере — вложение мутабельное, само себя
     * не рассылает. На клиенте — безвредная пустышка (NeoForge сам
     * отсекает не-ServerLevel).
     */
    public static void синхронизировать(Player игрок) {
        игрок.syncData(КЛАСС);
    }

    /** Прибавить мастерство и сразу разослать. Зажим в 0..100 — в {@link ClassMastery}. */
    public static void прибавитьМастерство(Player игрок, int сколько) {
        if (сколько == 0) return;
        PlayerClassData д = данные(игрок);
        int было = д.мастерство;
        д.мастерство = ClassMastery.прибавить(д.мастерство, сколько);
        if (д.мастерство != было) синхронизировать(игрок);
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
