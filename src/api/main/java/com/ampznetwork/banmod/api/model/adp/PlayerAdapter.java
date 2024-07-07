package com.ampznetwork.banmod.api.model.adp;

import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import org.comroid.api.net.REST;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerAdapter {
    static CompletableFuture<UUID> fetchId(String name) {
        return REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(UuidVarchar36Converter::fillDashes)
                .thenApply(UUID::fromString);
    }

    static CompletableFuture<String> fetchName(UUID id) {
        return REST.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("name").asString());
    }

    default UUID getId(String name) {
        return fetchId(name).join();
    }

    default String getName(UUID playerId) {
        return fetchName(playerId).join();
    }

    boolean isOnline(UUID playerId);

    void kick(UUID playerId, String reason);

    void openBook(UUID playerId, BookAdapter book);
}
