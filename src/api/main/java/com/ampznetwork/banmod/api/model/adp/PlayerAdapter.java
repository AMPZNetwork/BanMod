package com.ampznetwork.banmod.api.model.adp;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import org.comroid.api.net.REST;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerAdapter {
    private static void cache(BanMod banMod, UUID id, String name) {
        banMod.getEntityService().pingUsernameCache(id, name);
    }
    static CompletableFuture<UUID> fetchId(BanMod banMod, String name) {
        var fetch = REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(UuidVarchar36Converter::fillDashes)
                .thenApply(UUID::fromString);
        // put into cache
        //noinspection DuplicatedCode
        fetch.thenAccept(id -> cache(banMod, id, name));
        return fetch;
    }

    static CompletableFuture<String> fetchName(BanMod banMod, UUID id) {
        var fetch = REST.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("name").asString());
        // put into cache
        //noinspection DuplicatedCode
        fetch.thenAccept(name -> cache(banMod, id, name));
        return fetch;
    }

    BanMod getBanMod();

    default UUID getId(String name) {
        return fetchId(getBanMod(), name).join();
    }

    default String getName(UUID playerId) {
        return fetchName(getBanMod(), playerId).join();
    }

    boolean isOnline(UUID playerId);

    void kick(UUID playerId, String reason);

    void openBook(UUID playerId, BookAdapter book);
}
