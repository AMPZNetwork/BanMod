package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.info.DefaultReason;
import com.ampznetwork.libmod.api.entity.DbObject;
import com.ampznetwork.libmod.api.model.EntityType;
import com.ampznetwork.libmod.api.model.convert.UuidBinary16Converter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static java.time.Instant.*;
import static java.util.function.Predicate.*;
import static lombok.Builder.*;

@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banmod_infractions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Infraction extends DbObject {
    public static final EntityType<Infraction, Builder> TYPE
                                                                                      = new EntityType<>(Infraction::builder,
            null,
            Infraction.class,
            Infraction.Builder.class);
    public static final Instant                         TOO_EARLY                     = Instant.EPOCH.plus(Duration.ofDays(2));
    public static final Predicate<Infraction>                            IS_IN_EFFECT = i -> !i.getPunishment().isInherentlyTemporary()
                                                                                             && (i.getRevoker() == null
                                                                                                 && (i.getExpires() == null || i.getExpires().isAfter(now())
                                                                                                     || i.getExpires()
                                                                                                             .isBefore(TOO_EARLY))) /* fix for a conversion bug */;
    public static       Comparator<Infraction>                           BY_SEVERITY  = Comparator.<Infraction>comparingInt(i ->
            i.getPunishment().ordinal()).reversed();
    public static       Comparator<Infraction>                           BY_NEWEST    = Comparator.<Infraction>comparingLong(i ->
            i.timestamp.toEpochMilli()).reversed();
    public static       Comparator<Infraction>                           BY_SHORTEST  = Comparator.<Infraction>comparingLong(i ->
            i.expires == null
            ? Long.MIN_VALUE
            : i.expires.toEpochMilli()).reversed();
    @NotNull
    @OneToOne
    PlayerData player;
    @NotNull
    @ManyToOne
    PunishmentCategory category;
    @NotNull
    Punishment punishment;
    @NotNull
    @Default
    Instant    timestamp = now();
    @Nullable
    @Default
    Instant    expires   = null;
    @Nullable
    @Default
    String     reason    = null;
    @Nullable
    @Default
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UuidBinary16Converter.class)
    UUID       issuer    = null;
    @Nullable
    @Default
    @Column(columnDefinition = "binary(16)")
    @Convert(converter = UuidBinary16Converter.class)
    UUID       revoker   = null;
    @Nullable
    @Default
    Instant    revokedAt = null;

    public @Nullable String getReason() {
        return Optional.ofNullable(reason)
                .or(() -> Optional.of(category)
                        .map(DefaultReason::getDefaultReason)
                        .filter(not(String::isBlank)))
                .or(() -> Optional.of(punishment)
                        .map(DefaultReason::getDefaultReason)
                        .filter(not(String::isBlank)))
                .orElseGet(() -> "You were " + punishment.getAdverb());
    }

    public PlayerResult toResult() {
        return new PlayerResult(player.getId(),
                punishment == Punishment.Mute,
                punishment == Punishment.Ban,
                reason,
                timestamp,
                expires,
                this.getId());
    }
}
