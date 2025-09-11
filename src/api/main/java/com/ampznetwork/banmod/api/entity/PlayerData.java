package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.libmod.api.entity.Player;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.time.Instant.*;
import static org.comroid.api.Polyfill.*;

/**
 * @deprecated use {@link Player}
 */
@Data
@Slf4j
@SuperBuilder
@Deprecated(forRemoval = true)
@Getter(onMethod_ = @__(@JsonIgnore))
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData extends Player {
    public static final Comparator<Map.Entry<?, Instant>>              MOST_RECENTLY_SEEN = Comparator.comparingLong(e -> e.getValue().toEpochMilli());

    @JsonIgnore
    public Optional<Instant> getLastSeen() {
        return Stream.concat(getKnownNames().values().stream(), getKnownIPs().values().stream())
                .max(Comparator.comparingLong(Instant::toEpochMilli));
    }

    @JsonIgnore
    public Optional<String> getLastKnownName() {
        return getKnownNames().entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
    }

    @JsonIgnore
    public Optional<String> getLastKnownIp() {
        return getKnownIPs().entrySet().stream()
                .max(PlayerData.MOST_RECENTLY_SEEN)
                .map(Map.Entry::getKey);
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
