package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.libmod.api.entity.Player;
import com.ampznetwork.libmod.api.model.EntityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.comroid.annotations.Doc;
import org.jetbrains.annotations.Contract;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.time.Instant.now;
import static org.comroid.api.Polyfill.ip2string;

@Data
@Slf4j
@Entity
@SuperBuilder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banmod_playerdata")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData extends Player {
    public static final EntityType<PlayerData, PlayerData.Builder> TYPE
                                                                                      = new EntityType<>(PlayerData::builder,
            Player.TYPE,
            PlayerData.class,
            PlayerData.Builder.class);
    public static final Comparator<Map.Entry<?, Instant>>          MOST_RECENTLY_SEEN = Comparator.comparingLong(e -> e.getValue().toEpochMilli());
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
