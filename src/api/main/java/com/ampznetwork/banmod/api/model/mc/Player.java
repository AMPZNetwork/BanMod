package com.ampznetwork.banmod.api.model.mc;

import com.ampznetwork.banmod.api.BanMod;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.attr.Named;
import org.comroid.api.attr.UUIDContainer;
import org.comroid.api.func.util.Cache;
import org.comroid.api.net.REST;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.comroid.api.net.REST.Method.*;

@Value
@AllArgsConstructor
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
@Slf4j(topic = BanMod.Strings.AddonName)
public class Player implements UUIDContainer, Named {
    UUID id;
    @NonFinal
    String name = null;

    public CompletableFuture<String> fetchName() {
        var future = Cache.get("minecraft.username." + id,
                () -> REST.request(GET, "https://sessionserver.mojang.com/session/minecraft/profile/" + id).execute()
                        .thenApply(REST.Response::validate2xxOK)
                        .thenApply(rsp -> rsp.getBody().get("name").asString())
                        .exceptionally(t -> {
                            log.warn("Could not retrieve Minecraft Username for user " + id, t);
                            return "Steve";
                        }));
        future.thenAccept(str -> name = str);
        return future;
    }
}
