package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.convert.UuidBinary16Converter;
import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.comroid.annotations.Doc;
import org.comroid.api.net.REST;

import javax.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.comroid.api.net.REST.Method.GET;

@Data
@Slf4j
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "banmod_playerdata")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData {
    private static final Comparator<Map.Entry<?, Instant>> MOST_RECENTLY_SEEN = Comparator.comparingLong(e -> e.getValue().toEpochMilli());
    @Id
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UuidBinary16Converter.class)
    UUID id;
    @Singular
    @ElementCollection
    @CollectionTable(name = "banmod_playerdata_names")
    Map<@Doc("name") String, @Doc("lastSeen") Instant> knownNames = new HashMap<>();
    @Singular
    @ElementCollection
    @CollectionTable(name = "banmod_playerdata_ips")
    Map<@Doc("ip") String, @Doc("lastSeen") Instant> knownIPs = new HashMap<>();

    public static BiConsumer<UUID, String> CACHE_NAME = null;

    public static CompletableFuture<UUID> fetchId(String name) {
        var future = REST.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .thenApply(UuidVarchar36Converter::fillDashes)
                .thenApply(UUID::fromString);
        future.thenAccept(id -> CACHE_NAME.accept(id, name));
        return future;
    }

    public static CompletableFuture<String> fetchUsername(UUID id) {
        var future = REST.request(GET, "https://sessionserver.mojang.com/session/minecraft/profile/" + id).execute()
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("name").asString());
        future.thenAccept(name -> CACHE_NAME.accept(id, name));
        return future.exceptionally(t -> {
            log.warn("Could not retrieve Minecraft Username; returning 'Steve' for ID {}", id, t);
            return "Steve";
        });
    }

    public Optional<String> getLastKnownName() {
        return knownNames.entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
    }

    public CompletableFuture<String> getOrFetchUsername() {
        return getLastKnownName().map(CompletableFuture::completedFuture)
                .orElseGet(() -> fetchUsername(id));
    }

    public Optional<String> getLastKnownIp() {
        return knownIPs.entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
    }
}
