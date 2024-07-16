package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.convert.UuidBinary16Converter;
import com.ampznetwork.banmod.api.model.info.DefaultReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Described;
import org.comroid.api.attr.Named;
import org.hibernate.annotations.GenericGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Data
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "name")
@Table(name = "banmod_categories")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PunishmentCategory implements Named, Described, DefaultReason, DbObject {
    public static PunishmentCategory.Builder standard(String name) {
        return builder().name(name)
                .punishmentThreshold(0, Punishment.Kick)
                .punishmentThreshold(2, Punishment.Mute)
                .punishmentThreshold(5, Punishment.Ban);
    }
    @Id
    @lombok.Builder.Default
    @GeneratedValue(generator = "UUID")
    @Convert(converter = UuidBinary16Converter.class)
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "binary(16)", updatable = false, nullable = false)
    UUID id = UUID.randomUUID();
    String   name;
    @lombok.Builder.Default
    @Nullable
    String   description       = null;
    @lombok.Builder.Default
    @Nullable
    String   defaultReason     = null;
    @lombok.Builder.Default
    Duration baseDuration      = Duration.ofHours(3);
    @lombok.Builder.Default
    double   repetitionExpBase = 3;
    @Singular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "banmod_categories_thresholds")
    Map<@NotNull Integer, Punishment> punishmentThresholds;

    public Duration calculateDuration(int repetition) {
        var factor = Math.pow(repetitionExpBase, repetition);
        return baseDuration.multipliedBy((long) factor);
    }

    public Optional<Punishment> calculatePunishment(int rep) {
        return punishmentThresholds.entrySet().stream()
                .filter(e -> e.getKey() < rep)
                .min(Punishment.BY_SEVERITY)
                .map(Map.Entry::getValue);
    }
}
