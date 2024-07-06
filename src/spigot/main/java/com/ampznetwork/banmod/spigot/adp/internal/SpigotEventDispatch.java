package com.ampznetwork.banmod.spigot.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
import lombok.Value;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;

@Value
public class SpigotEventDispatch extends EventDispatchBase {
    public SpigotEventDispatch(BanMod banMod) {
        super(banMod);
    }

    @EventHandler
    public void handle(PlayerLoginEvent event) {
        var result = playerLogin(event.getPlayer().getUniqueId(), event.getRealAddress());
        if (result.isBanned()) {
            if (result.reason() != null)
                event.setKickMessage(result.reason());
            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
        }
    }

    @EventHandler
    public void handle(AsyncPlayerChatEvent event) {
        var result = player(event.getPlayer().getUniqueId());
        if (result.isMuted()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You are muted! " + (result.reason() == null
                    ? ""
                    : "Reason: " + result.reason()));
        }
    }
}
