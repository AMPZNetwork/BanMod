package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.libmod.api.entity.Player;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.time.Instant.*;
import static org.comroid.api.java.StackTraceUtils.*;

@Log
@Value
@NonFinal
public abstract class EventDispatchBase {
    protected BanMod mod;

    protected PlayerResult playerLogin(UUID playerId, InetAddress address) {
        var service = mod.getEntityService();
        var name    = mod.getLib().getPlayerAdapter().getName(playerId);
        var data = service.getAccessor(Player.TYPE)
                .getOrCreate(playerId)
                .setUpdateOriginal(original -> {
                    original.setName(name);
                    return original
                            .pushKnownName(name)
                            .pushKnownIp(address);
                })
                .complete(builder -> {
                    var now = now();
                    builder//.lastSeen(now())
                            .knownIPs(new HashMap<>() {{put(address.toString().substring(1), now);}})
                            .knownNames(new HashMap<>() {{put(name, now);}})
                            .id(playerId)
                            .name(name);
                });
        service.save(data);

        // queue player
        return player(playerId);
    }

    protected PlayerResult player(UUID playerId) {
        return mod.queuePlayer(playerId);
    }

    protected <C> void handleThrowable(
            UUID playerId,
            Throwable t,
            Function<Component, C> componentSerializer,
            Consumer<C> forwardAndDisconnect
    ) {
        var player = mod.getLib().getPlayerAdapter()
                .getPlayer(playerId).orElseThrow();
        mod.log().warn("An internal error occurred", t);
        //StackTraceUtils.writeFilteredStacktrace(t, printer);
        BanMod.Resources.notify(mod, playerId, null,
                new PlayerResult(player, false, false,
                        "%s: %s".formatted(lessSimpleDetailedName(t.getClass()), t.getMessage()),
                        null, null, null),
                (uuid, component) -> {
                    var serialize = componentSerializer.apply(component);
                    if (!mod.allowUnsafeConnections())
                        forwardAndDisconnect.accept(serialize);
                });
    }
}
