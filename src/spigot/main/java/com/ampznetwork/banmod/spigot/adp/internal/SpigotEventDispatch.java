package com.ampznetwork.banmod.spigot.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
import lombok.Value;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import static net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer.*;
import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.*;

@Value
public class SpigotEventDispatch extends EventDispatchBase implements Listener {
    public SpigotEventDispatch(BanMod banMod) {
        super(banMod);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void handle(PlayerLoginEvent event) {
        try {
            var playerId = event.getPlayer().getUniqueId();
            try {
                var result = playerLogin(playerId, event.getRealAddress());
                if (result.isBanned())
                    BanMod.Resources.notify(mod, playerId, Punishment.Ban, result, (id, msg) -> {
                        var serialize = legacySection().serialize(msg);
                        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, serialize);
                    });
            } catch (Throwable t) {
                handleThrowable(playerId, t, legacySection()::serialize,
                        component -> event.disallow(PlayerLoginEvent.Result.KICK_OTHER, component));
            }
        } catch (Throwable t) {
            if (mod.allowUnsafeConnections())
                return;
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Unable to log in");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void handle(AsyncPlayerChatEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        var result = player(playerId);
        if (result.isMuted()) {
            event.setCancelled(true);
            BanMod.Resources.notify(mod, playerId, Punishment.Mute, result, (id, msg) -> {
                var serialize = get().serialize(msg);
                event.getPlayer().spigot().sendMessage(serialize);
            });
        }
    }
}
