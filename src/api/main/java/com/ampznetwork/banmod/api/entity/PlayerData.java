package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.convert.UuidBinary16Converter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.annotations.Doc;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "banmod_playerdata")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData {
    @Id
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UuidBinary16Converter.class)
    UUID id;
    @ElementCollection
    @CollectionTable(name = "banmod_playerdata_names")
    Map<@Doc("name") String, @Doc("lastSeen") Instant> knownNames;
    @ElementCollection
    @CollectionTable(name = "banmod_playerdata_ips")
    Set<InetAddress> knownIPs;

    @Basic
    @Nullable
    public String getLastKnownName() {
        return knownNames.entrySet().stream()
                .max(Comparator.comparingLong(e -> e.getValue().toEpochMilli()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
