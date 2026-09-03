package dev.denthe.gmtools.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Locale;

/**
 * Быстрый вход в наблюдателя и возврат на прежнее место.
 *
 * Всё делается клиентскими командами /gamemode и /tp: сервер и так
 * проверяет право на них по ванильному OP, отдельного серверного кода
 * не нужно.
 *
 * ponytail: прежний режим и точка возврата хранятся в статике и теряются
 * при переподключении к серверу — для восьми друзей это допустимо; если
 * начнёт мешать, перенести в серверные данные игрока.
 */
public final class SpectatorToggle {
    private SpectatorToggle() {}

    private static String savedMode;                 // null — ещё ни разу не входили
    private static double savedX = Double.NaN;        // NaN — точки возврата нет
    private static double savedY, savedZ;
    private static float savedYaw, savedPitch;

    public static void toggle(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;

        if (mc.player.isSpectator()) {
            String mode = savedMode != null ? savedMode : "survival";
            for (String cmd : commandsToLeaveSpectator(mode, savedX, savedY, savedZ, savedYaw, savedPitch)) {
                mc.getConnection().sendCommand(cmd);
            }
        } else {
            enterSpectator(mc);
        }
    }

    /**
     * Запомнить, где и в каком режиме игрок, и уйти в наблюдатели.
     * Отдельный метод — им же пользуется «наблюдать за игроком» в панели,
     * чтобы возврат по хоткею потом сработал.
     */
    public static void enterSpectator(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.player.isSpectator()) return;
        savedMode = currentMode(mc);
        savedX = mc.player.getX();
        savedY = mc.player.getY();
        savedZ = mc.player.getZ();
        savedYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();
        mc.getConnection().sendCommand("gamemode spectator");
    }

    private static String currentMode(Minecraft mc) {
        GameType t = mc.gameMode != null ? mc.gameMode.getPlayerMode() : GameType.SURVIVAL;
        return t.getName();   // survival / creative / adventure
    }

    /**
     * Чистая часть: какими командами вернуть игрока из наблюдателя.
     * Locale.ROOT обязателен — на машине с русской локалью %f иначе
     * ставит запятую, и /tp не разбирает координату. Проверяется тестом.
     */
    static List<String> commandsToLeaveSpectator(String mode, double x, double y, double z,
                                                 float yaw, float pitch) {
        if (Double.isNaN(x)) return List.of("gamemode " + mode);
        return List.of(
            "gamemode " + mode,
            String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch));
    }
}
