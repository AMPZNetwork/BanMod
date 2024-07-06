package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.model.Punishment;
import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Duration;

@Data
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "name")
@Table(name = "banmod_categories")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PunishmentCategory {
    @Id
    String name;
    Punishment punishment;
    Duration baseDuration;
    double repetitionExpBase;

    public Duration calculateDuration(int repetition) {
        var factor = Math.pow(repetitionExpBase, repetition);
        return baseDuration.multipliedBy((long) factor);
    }
}
