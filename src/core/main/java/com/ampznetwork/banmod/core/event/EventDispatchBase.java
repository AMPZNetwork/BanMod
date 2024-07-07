package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.model.PlayerResult;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
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
        final var service = banMod.getEntityService();
        final var name = banMod.getPlayerAdapter().getName(playerId);
        final var now = Instant.now();

        // push player data cache
        var data = service.getPlayerData(playerId)
                // update cached player data
                .map(existing -> {
                    existing.getKnownNames().put(name, now);
                    existing.getKnownIPs().put(address.toString(), now);
                    return existing;
                })
                // create new player data
                .orElseGet(() -> new PlayerData(playerId,
                        new HashMap<>() {{
                            put(name, now);
                        }},
                        new HashMap<>() {{
                            put(address.toString(), now);
                        }}));
        service.save(data);

        // queue player
        return player(playerId);
    }
}
