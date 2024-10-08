package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.info.DefaultReason;
import com.ampznetwork.libmod.api.entity.DbObject;
import com.ampznetwork.libmod.api.model.EntityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.comroid.api.Polyfill;
import org.comroid.api.attr.Described;
import org.comroid.api.attr.Named;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Data
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@SuperBuilder
@EqualsAndHashCode(of = "name")
@Table(name = "banmod_punishment_categories")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PunishmentCategory extends DbObject.WithName implements Named, Described, DefaultReason {
    public static final EntityType<PunishmentCategory, Builder<PunishmentCategory, ?>> TYPE = Polyfill.uncheckedCast(new EntityType<>(PunishmentCategory::builder,
            null,
            PunishmentCategory.class,
            Builder.class));

    public static PunishmentCategory.Builder defaultBuilder(String name) {
        return builder().name(name)
                .punishmentThreshold(0, Punishment.Kick)
                .punishmentThreshold(2, Punishment.Mute)
                .punishmentThreshold(5, Punishment.Ban);
    }

    String name;
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
    @Column(name = "punishment_type")
    @MapKeyColumn(name = "repetition")
    @CollectionTable(name = "banmod_punishment_thresholds", joinColumns = @JoinColumn(name = "id"))
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
