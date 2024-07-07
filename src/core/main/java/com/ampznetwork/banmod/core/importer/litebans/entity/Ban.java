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
@Table(name = "litebans_bans")
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class Ban extends LiteBansEntity {
    public boolean equals(Object other) {
        return other instanceof Ban ban && ban.getId() == getId();
    }

    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
