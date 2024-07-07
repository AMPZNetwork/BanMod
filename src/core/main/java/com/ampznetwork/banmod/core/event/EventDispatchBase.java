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
import java.util.HashSet;
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
        service.getPlayerData(playerId)
                .ifPresentOrElse(existing -> {
                    // update cached player data
                    existing.getKnownNames().put(name, now);
                    existing.getKnownIPs().add(address);
                    service.save(existing);
                }, () -> {
                    // create new player data
                    var newData = new PlayerData(playerId,
                            new HashMap<>() {{
                                put(name, now);
                            }},
                            new HashSet<>() {{
                                add(address);
                            }});
                    service.save(newData);
                });

        // queue player
        return player(playerId);
    }
}
