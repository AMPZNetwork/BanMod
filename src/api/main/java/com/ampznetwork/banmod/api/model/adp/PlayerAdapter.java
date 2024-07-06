package com.ampznetwork.banmod.api.model.adp;

import org.comroid.api.net.REST;

import java.util.UUID;

public interface PlayerAdapter {
    default UUID getId(String name) {
        return REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(UUID::fromString)
                .join();
    }

    String getName(UUID playerId);

    boolean isOnline(UUID playerId);

    void kick(UUID playerId, String reason);

    void openBook(UUID playerId, BookAdapter book);
}
