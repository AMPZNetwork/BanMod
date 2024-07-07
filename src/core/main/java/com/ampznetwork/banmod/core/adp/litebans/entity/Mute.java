package com.ampznetwork.banmod.core.adp.litebans.entity;

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
