package com.ampznetwork.banmod.api.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlayerData {
    @Id
    UUID id;
    @ElementCollection
    Set<InetAddress> knownIPs;
    @ElementCollection
    Set<String> knownNames;
}
