package com.ampznetwork.banmod.spigot.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
import lombok.Value;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.comroid.api.func.util.DelegateStream;
import org.comroid.api.java.StackTraceUtils;

import java.io.PrintStream;
import java.io.StringWriter;

import static net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer.get;
import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection;

@Value
public class SpigotEventDispatch extends EventDispatchBase implements Listener {
    public SpigotEventDispatch(BanMod banMod) {
        super(banMod);
    }

    @EventHandler
    public void handle(PlayerLoginEvent event) {
        try {
            var playerId = event.getPlayer().getUniqueId();
            var result = playerLogin(playerId, event.getRealAddress());
            if (result.forceDeny())
                BanMod.Resources.notify(mod, playerId, null, result, (id, msg) -> {
                    var serialize = legacySection().serialize(msg);
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, serialize);
                });
            else if (result.isBanned())
                BanMod.Resources.notify(mod, playerId, Punishment.Ban, result, (id, msg) -> {
                    var serialize = legacySection().serialize(msg);
                    event.disallow(PlayerLoginEvent.Result.KICK_BANNED, serialize);
                });
        } catch (Throwable t) {
            var writer = new StringWriter();
            var printer = new PrintStream(new DelegateStream.IO.Output(writer));
            StackTraceUtils.writeFilteredStacktrace(t, printer);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Internal error: " + writer.getBuffer());
        }
    }

    @EventHandler
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
