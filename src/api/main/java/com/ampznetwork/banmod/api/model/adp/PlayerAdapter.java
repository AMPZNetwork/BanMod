package com.ampznetwork.banmod.api.model.adp;

import org.comroid.api.info.Constraint;
import org.comroid.api.net.REST;

import java.util.UUID;

public interface PlayerAdapter {
    default UUID getId(String name) {
        return REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(uuid -> {
                    Constraint.notNull(uuid, "uuid string").run();
                    return uuid.length() > 32 ? uuid
                            : uuid.substring(0, 8) +
                            '-' + uuid.substring(8, 12) +
                            '-' + uuid.substring(12, 16) +
                            '-' + uuid.substring(16, 20) +
                            '-' + uuid.substring(20);
                })
                .thenApply(UUID::fromString)
                .join();
    }

    String getName(UUID playerId);

    boolean isOnline(UUID playerId);

    void kick(UUID playerId, String reason);

    void openBook(UUID playerId, BookAdapter book);
}
