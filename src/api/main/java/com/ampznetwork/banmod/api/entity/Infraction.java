package com.ampznetwork.banmod.api.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.IntegerAttribute;
import org.comroid.api.attr.Named;
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
    @Id UUID id = UUID.randomUUID();
    @NotNull UUID playerId;
    @NotNull Instant timestamp;
    @Nullable UUID issuer;
    State state = State.Active;
    @ManyToOne PunishmentCategory category;

    @Getter @AllArgsConstructor
    public enum State implements Named, IntegerAttribute {
        Revoked(-1),
        Expired(0),
        Active(1);

        final int value;
    }
}
