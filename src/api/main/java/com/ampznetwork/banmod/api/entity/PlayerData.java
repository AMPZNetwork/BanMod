package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.convert.UuidBinary16Converter;
import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.comroid.annotations.Doc;
import org.comroid.api.net.REST;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static com.ampznetwork.banmod.api.database.EntityService.*;
import static java.time.Instant.*;
import static org.comroid.api.net.REST.Method.*;

@Data
@Slf4j
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "banmod_playerdata")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData implements DbObject {
    public static final Comparator<Map.Entry<?, Instant>> MOST_RECENTLY_SEEN = Comparator.comparingLong(e -> e.getValue().toEpochMilli());
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
        return future;
    }
    @Id
    @lombok.Builder.Default
    @Convert(converter = UuidBinary16Converter.class)
    @Column(columnDefinition = "binary(16)", updatable = false, nullable = false)
    UUID id = UUID.randomUUID();
    @Nullable
    @lombok.Builder.Default
    Instant                                          lastSeen = null;
    @Singular
    @ElementCollection
    @Column(name = "seen")
    @MapKeyColumn(name = "name")
    @CollectionTable(name = "banmod_playerdata_names", joinColumns = @JoinColumn(name = "id"))
    Map<@Doc("name") String, @Doc("lastSeen") Instant> knownNames = new HashMap<>();
    @Singular
    @ElementCollection
    @Column(name = "seen")
    @MapKeyColumn(name = "ip")
    @CollectionTable(name = "banmod_playerdata_ips", joinColumns = @JoinColumn(name = "id"))
    Map<@Doc("ip") String, @Doc("lastSeen") Instant> knownIPs = new HashMap<>();

    public CompletableFuture<String> getOrFetchUsername() {
        return getLastKnownName().map(CompletableFuture::completedFuture)
                .orElseGet(() -> fetchUsername(id));
    }

    public Optional<String> getLastKnownName() {
        return knownNames.entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
    }

    public Optional<String> getLastKnownIp() {
        return knownIPs.entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.PlayerData;
    }

    @Contract(value = "!null->this", pure = true)
    public PlayerData pushKnownName(String name) {
        var map = getKnownNames();
        map = new HashMap<>(map);
        map.compute(name, ($0, $1) -> now());
        return this;
    }

    @Contract(value = "!null->this", pure = true)
    public PlayerData pushKnownIp(InetAddress ip) {
        var map = getKnownIPs();
        map = new HashMap<>(map);
        map.compute(ip2string(ip), ($0, $1) -> now());
        return this;
    }
}
