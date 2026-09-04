package dev.denthe.classes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 */
public class PlayerClassData {

    public enum Класс { NONE, CLERIC, SMITH, FARMER, CHRONICLER }

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LmpcClasses.MODID);

    /** Текущий класс. NONE — без класса, чистые руки. */
    public Класс класс = Класс.NONE;

    /** Мировой тик последней смены. −1 — ни разу не менял, кулдаун не действует. */
    public long последняяСменаТик = -1L;

    /** Мастерство текущего класса. Спек, раздел 2.1. */
    public int мастерство = 0;

    /** Мировой тик, когда снова можно пить улучшенный отвар. −1 — готов сейчас. */
    public long отварГотовТик = -1L;

    public PlayerClassData() {}

    public PlayerClassData(Класс класс, long последняяСменаТик, int мастерство, long отварГотовТик) {
        this.класс = класс;
        this.последняяСменаТик = последняяСменаТик;
        this.мастерство = мастерство;
        this.отварГотовТик = отварГотовТик;
    }

    public static final Codec<PlayerClassData> CODEC = RecordCodecBuilder.create(и -> и.group(
        Codec.STRING.xmap(Класс::valueOf, Класс::name).fieldOf("class").forGetter(д -> д.класс),
        Codec.LONG.fieldOf("lastSwitchTick").forGetter(д -> д.последняяСменаТик),
        Codec.INT.fieldOf("mastery").forGetter(д -> д.мастерство),
        Codec.LONG.fieldOf("brewReadyTick").forGetter(д -> д.отварГотовТик)
    ).apply(и, PlayerClassData::new));

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

    public static final Supplier<AttachmentType<PlayerClassData>> КЛАСС =
        ВЛОЖЕНИЯ.register("player_class", () -> AttachmentType
            .builder(PlayerClassData::new)
            .serialize(CODEC)
            .copyOnDeath()
            .build());

    /** Данные игрока. Вложение создаётся само при первом обращении. */
    public static PlayerClassData данные(Player игрок) {
        return игрок.getData(КЛАСС.get());
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
