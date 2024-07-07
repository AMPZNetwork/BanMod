package com.ampznetwork.banmod.core.importer.litebans.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Data
@Entity
@SuperBuilder
@AllArgsConstructor
@Table(name = "litebans_mutes")
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class Mute extends LiteBansEntity {
    public boolean equals(Object other) {
        return other instanceof Mute mute && mute.getId() == getId();
    }

    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
