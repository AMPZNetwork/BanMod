package com.ampznetwork.banmod.api.model.adp;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import org.comroid.api.net.REST;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.time.Instant.now;

public interface PlayerAdapter {
    static CompletableFuture<UUID> fetchId(BanMod banMod, String name) {
        var fetch = REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(UuidVarchar36Converter::fillDashes)
                .thenApply(UUID::fromString);
        // put into cache
        //noinspection DuplicatedCode
        fetch.thenAccept(id -> banMod.getEntityService().getPlayerData(id)
                .ifPresentOrElse(
                        data -> {
                            data.getKnownNames().put(name, now());
                            banMod.getEntityService().save(data);
                        },
                        () -> {
                            var data = new PlayerData(id, new HashMap<>() {{
                                put(name, now());
                            }}, new HashSet<>());
                            banMod.getEntityService().save(data);
                        }));
        return fetch;
    }

    static CompletableFuture<String> fetchName(BanMod banMod, UUID id) {
        var fetch = REST.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("name").asString());
        // put into cache
        //noinspection DuplicatedCode
        fetch.thenAccept(name -> banMod.getEntityService().getPlayerData(id)
                .ifPresentOrElse(
                        data -> {
                            data.getKnownNames().put(name, now());
                            banMod.getEntityService().save(data);
                        },
                        () -> {
                            var data = new PlayerData(id, new HashMap<>() {{
                                put(name, now());
                            }}, new HashSet<>());
                            banMod.getEntityService().save(data);
                        }));
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
