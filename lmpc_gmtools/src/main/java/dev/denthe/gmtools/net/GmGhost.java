package dev.denthe.gmtools.net;

import dev.denthe.gmtools.GmTools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Режим-призрак для мастера: невидимость (без частиц), неуязвимость,
 * скрытие из списка игроков (tab), беззвучность. Двигаться и
 * взаимодействовать можно. Шаги, к сожалению, всё равно слышно —
 * это чисто клиентский звук.
 *
 * Команда `/gmtools ghost` (переключатель), только оператору.
 */
@EventBusSubscriber(modid = GmTools.MODID)
public final class GmGhost {
    private GmGhost() {}

    private static final Set<UUID> ghosts = new HashSet<>();

    static int toggle(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Только от лица игрока"));
            return 0;
        }
        if (ghosts.remove(p.getUUID())) {
            p.removeEffect(MobEffects.INVISIBILITY);
            p.setInvulnerable(false);
            p.setSilent(false);
            var add = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p));
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                if (o != p) o.connection.send(add);
            }
            src.sendSuccess(() -> Component.literal("Режим-призрак выключен"), false);
        } else {
            ghosts.add(p.getUUID());
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false));
            p.setInvulnerable(true);
            p.setSilent(true);
            var remove = new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID()));
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                if (o != p) o.connection.send(remove);
            }
            src.sendSuccess(() ->
                Component.literal("Режим-призрак включён: невидим, скрыт из списка, неуязвим"), false);
        }
        return 1;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ghosts.remove(e.getEntity().getUUID());
    }
}
