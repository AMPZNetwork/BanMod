package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.Punishment;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.annotations.Default;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Infraction {
    @Id
    UUID id = UUID.randomUUID();
    @NotNull
    UUID playerId;
    @NotNull
    Punishment punishment;
    @NotNull
    @Default
    Instant timestamp = Instant.now();
    @Nullable
    @Default
    Instant expires = null;
    @Nullable
    @Default
    String reason = null;
    @Nullable
    @Default
    UUID issuer = null;
    @Nullable
    @Default
    UUID revoker = null;

    public @Nullable String getReason() {
        return reason == null ? switch (punishment) {
            case Mute -> "You were muted";
            case Kick -> "You were kicked";
            case Ban -> "You were banned";
        } : reason;
    }
}
