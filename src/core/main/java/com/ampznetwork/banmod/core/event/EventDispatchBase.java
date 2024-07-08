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
    BanMod banMod;

    protected PlayerResult player(UUID playerId) {
        return banMod.getEntityService().queuePlayer(playerId);
    }

    protected PlayerResult playerLogin(UUID playerId, InetAddress address) {
        // push player data cache
        banMod.getEntityService().pingIpCache(playerId, address);

        // queue player
        return player(playerId);
    }
}
