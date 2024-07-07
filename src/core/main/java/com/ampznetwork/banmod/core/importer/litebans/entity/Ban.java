package com.ampznetwork.banmod.core.importer.litebans.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
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
