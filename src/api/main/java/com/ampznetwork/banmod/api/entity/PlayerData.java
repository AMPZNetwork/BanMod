package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.adp.UUIDConverter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;
import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData {
    @Id
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UUIDConverter.class)
    UUID id;
    @ElementCollection
    Set<String> knownNames;
    @ElementCollection
    Set<InetAddress> knownIPs;
}
