package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.PlayerResult;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;

import java.net.InetAddress;
import java.util.UUID;

@Log
@Value
@NonFinal
public abstract class EventDispatchBase {
    protected BanMod mod;

    protected PlayerResult player(UUID playerId) {
        return mod.getEntityService().queuePlayer(playerId);
    }

    protected PlayerResult playerLogin(UUID playerId, InetAddress address) {
        final var service = mod.getEntityService();
        final var name = mod.getPlayerAdapter().getName(playerId);

        service.pingUsernameCache(playerId, name);
        service.pingIpCache(playerId, address);

        // queue player
        return player(playerId);
    }
}
