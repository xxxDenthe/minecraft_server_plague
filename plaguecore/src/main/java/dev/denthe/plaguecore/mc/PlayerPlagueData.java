package dev.denthe.plaguecore.mc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denthe.plaguecore.PlagueCore;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Чума в отдельно взятом игроке. Спек подсистемы 2, раздел 8.
 *
 * Хранится ванильным Data Attachment: NeoForge сам кладёт это в файл
 * игрока и сам достаёт при входе. Своего кода сохранения не пишем.
 *
 * Вложение помечено copyOnDeath: смерть не лечит. Иначе самоубийство
 * стало бы самым дешёвым лекарством и вся подсистема потеряла бы смысл.
 */
public class PlayerPlagueData {

    public static final DeferredRegister<AttachmentType<?>> ВЛОЖЕНИЯ =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PlagueCore.MODID);

    /** Накопленная заражённость, 0–100. */
    public float заражённость;

    /** Стадия 0–4. Производная от заражённости, держится кэшем для сравнения. */
    public int стадия;

    /** Игровой тик, до которого действует иммунитет. */
    public long иммунитетДо;

    /** Смертей на стадии 2+ за сессию. */
    public int смертей;

    /** Глотков отвара подряд. */
    public int глотков;

    /** Тик последнего глотка. −1 — не пил ни разу. */
    public long тикПоследнегоГлотка = -1L;

    public PlayerPlagueData() {}

    public PlayerPlagueData(float заражённость, int стадия, long иммунитетДо,
                            int смертей, int глотков, long тикПоследнегоГлотка) {
        this.заражённость = заражённость;
        this.стадия = стадия;
        this.иммунитетДо = иммунитетДо;
        this.смертей = смертей;
        this.глотков = глотков;
        this.тикПоследнегоГлотка = тикПоследнегоГлотка;
    }

    public static final Codec<PlayerPlagueData> CODEC = RecordCodecBuilder.create(и -> и.group(
        Codec.FLOAT.fieldOf("infection").forGetter(д -> д.заражённость),
        Codec.INT.fieldOf("stage").forGetter(д -> д.стадия),
        Codec.LONG.fieldOf("immunityUntil").forGetter(д -> д.иммунитетДо),
        Codec.INT.fieldOf("plagueDeaths").forGetter(д -> д.смертей),
        Codec.INT.fieldOf("brewCount").forGetter(д -> д.глотков),
        Codec.LONG.fieldOf("lastBrewTick").forGetter(д -> д.тикПоследнегоГлотка)
    ).apply(и, PlayerPlagueData::new));

    public static final Supplier<AttachmentType<PlayerPlagueData>> ЧУМА =
        ВЛОЖЕНИЯ.register("player_plague", () -> AttachmentType
            .builder(PlayerPlagueData::new)
            .serialize(CODEC)
            .copyOnDeath()
            .build());

    /** Данные игрока. Вложение создаётся само при первом обращении. */
    public static PlayerPlagueData данные(Player игрок) {
        return игрок.getData(ЧУМА.get());
    }

    public static void register(IEventBus modEventBus) {
        ВЛОЖЕНИЯ.register(modEventBus);
    }
}
