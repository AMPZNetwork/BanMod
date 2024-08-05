package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.info.DefaultReason;
import com.ampznetwork.libmod.api.entity.DbObject;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Table;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Data
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "name")
@Table(name = "banmod_categories")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PunishmentCategory extends DbObject implements Named, Described, DefaultReason {
    public static PunishmentCategory.Builder standard(String name) {
        return builder().name(name)
                .punishmentThreshold(0, Punishment.Kick)
                .punishmentThreshold(2, Punishment.Mute)
                .punishmentThreshold(5, Punishment.Ban);
    }
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

    @Override
    public BanModEntityType<PunishmentCategory, Builder> getEntityType() {
        return BanModEntityType.PUNISHMENT_CATEGORY;
    }

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
