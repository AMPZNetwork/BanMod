package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.adp.UUIDConverter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

import static lombok.Builder.Default;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "banmod_infractions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Infraction {
    @Default
    @Id
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UUIDConverter.class)
    UUID id = UUID.randomUUID();
    @NotNull
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UUIDConverter.class)
    UUID playerId;
    @NotNull
    @ManyToOne
    PunishmentCategory category;
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
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UUIDConverter.class)
    UUID issuer = null;
    @Nullable
    @Default
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UUIDConverter.class)
    UUID revoker = null;

    public @Nullable String getReason() {
        return reason == null ? switch (category.getPunishment()) {
            case Mute -> "You were muted";
            case Kick -> "You were kicked";
            case Ban -> "You were banned";
        } : reason;
    }
}
