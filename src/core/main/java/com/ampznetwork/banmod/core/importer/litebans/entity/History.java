package com.ampznetwork.banmod.core.importer.litebans.entity;

import com.ampznetwork.libmod.api.model.convert.UuidVarchar36Converter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "litebans_history")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public final class History {
    @Id
    @Column(columnDefinition = "bigint(20) unsigned")
    long id;
    @Column(columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID uuid;
    @Column(columnDefinition = "varchar(45)")
    String ip;
    @Column(columnDefinition = "varchar(16)")
    String name;
    @Column(columnDefinition = "timestamp")
    Instant date;

    public boolean equals(Object other) {
        return other instanceof History ban && ban.getId() == getId();
    }

    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
