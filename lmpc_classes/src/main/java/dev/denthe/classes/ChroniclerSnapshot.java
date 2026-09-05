package dev.denthe.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Активка Летописца — снимок. Спек классов, раздел 7.
 *
 * Щёлкаешь камерой из мода `exposure` — и вся партия на несколько
 * минут видит точные уровни заражения чанков вокруг точки съёмки,
 * а не только под ногами. Разведка, а не украшение: спек даёт
 * Летописцу роль «глаза партии», и это единственная способность,
 * которая работает на других, а не на него.
 *
 * <p><b>Камера опознаётся по идентификатору предмета, а не по типу.</b>
 * Жёсткой зависимости на `exposure` у мода нет — по тем же причинам,
 * что и на `plaguecore` с Create. Список идентификаторов лежит
 * в конфиге, поэтому камеру можно заменить на что угодно, если
 * `exposure` однажды выпадет из сборки, — правкой файла, не пересборкой.
 *
 * <p><b>Лоровая половина не сделана и это ожидаемо.</b> Артефакт-снимок
 * и записи в базу лора — подсистема 5, её ещё нет. Крючок
 * {@code PlagueApi.recordSnapshot} уже дёргается на каждый снимок,
 * так что наполнять его можно будет не трогая этот класс.
 */
@EventBusSubscriber(modid = LmpcClasses.MODID)
public final class ChroniclerSnapshot {
    private ChroniclerSnapshot() {}

    @SubscribeEvent
    public static void щёлкнул(PlayerInteractEvent.RightClickItem событие) {
        if (!(событие.getEntity() instanceof ServerPlayer летописец)) return;
        if (!(летописец.level() instanceof ServerLevel уровень)) return;

        PlayerClassData д = PlayerClassData.данные(летописец);
        if (д.класс != PlayerClassData.Класс.CHRONICLER) return;
        if (!этоКамера(событие.getItemStack())) return;

        long сейчас = уровень.getGameTime();
        long осталось = ClassSwitch.осталосьТиков(
            д.снимокГотовТик, сейчас, ClassesConfig.снимокКулдаунТики(д.тир()));
        if (осталось > 0) {
            летописец.displayClientMessage(Component.translatable(
                "msg.lmpc_classes.snapshot.cooldown", ClassSwitch.минутОсталось(осталось)), true);
            return;
        }

        BlockPos точка = летописец.blockPosition();
        int сторона = снять(уровень, летописец, точка, д.тир());
        if (сторона == 0) {
            летописец.displayClientMessage(
                Component.translatable("msg.lmpc_classes.snapshot.no_data"), true);
            return;
        }

        д.снимокГотовТик = сейчас;
        PlayerClassData.синхронизировать(летописец);
        PlagueBridge.записатьСнимок(летописец, точка);
        PlayerClassData.прибавитьМастерство(летописец, ClassesConfig.летописецМастерствоЗаСнимок());
    }

    private static boolean этоКамера(ItemStack стопка) {
        if (стопка.isEmpty()) return false;
        return ClassesConfig.камерыЛетописца()
            .contains(BuiltInRegistries.ITEM.getKey(стопка.getItem()).toString());
    }

    /**
     * Собрать квадрат уровней вокруг точки и разослать его всем.
     * Возвращает сторону квадрата, либо 0, если чума не отвечает —
     * тогда снимок не считается сделанным и кулдаун не жжётся.
     */
    private static int снять(
            ServerLevel уровень, ServerPlayer автор, BlockPos точка, int тир) {
        int сторона = SnapshotGrid.сторона(
            ClassesConfig.снимокРадиусЧанков(тир), ClassNetwork.Snapshot.МАКС_СТОРОНА);
        int началоX = (точка.getX() >> 4) - сторона / 2;
        int началоZ = (точка.getZ() >> 4) - сторона / 2;

        byte[] уровни = new byte[сторона * сторона];
        boolean естьХотьЧто = false;
        for (int dz = 0; dz < сторона; dz++) {
            for (int dx = 0; dx < сторона; dx++) {
                int значение = PlagueBridge.уровеньЧанка(уровень, началоX + dx, началоZ + dz);
                уровни[SnapshotGrid.индекс(dx, dz, сторона)] = SnapshotGrid.упаковать(значение);
                if (значение >= 0) естьХотьЧто = true;
            }
        }
        if (!естьХотьЧто) return 0;

        ClassNetwork.Snapshot пакет = new ClassNetwork.Snapshot(
            началоX, началоZ, сторона, уровни,
            ClassesConfig.снимокЖивётТики(), автор.getGameProfile().getName());

        // Всей партии, включая самого Летописца: спек говорит «открывает
        // партии», а не «показывает автору».
        PacketDistributor.sendToAllPlayers(пакет);
        return сторона;
    }
}
