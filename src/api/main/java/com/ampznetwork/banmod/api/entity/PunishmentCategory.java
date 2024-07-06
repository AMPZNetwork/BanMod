package com.ampznetwork.banmod.api.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PunishmentCategory {
    @Id String name;
    double baseLevel;
    double repetitionFactor;
}
