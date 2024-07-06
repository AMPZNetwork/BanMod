package com.ampznetwork.banmod.api.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Infraction {
    @Id
    UUID id = UUID.randomUUID();
    @NotNull
    UUID playerId;
    @NotNull
    Instant timestamp;
    @Nullable
    Instant expires;
    @Nullable
    UUID issuer;
    @Nullable
    String reason;
    @ManyToOne
    PunishmentCategory category;
}
