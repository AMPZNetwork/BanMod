package com.ampznetwork.banmod.core.importer.litebans.entity;

import com.ampznetwork.libmod.api.model.convert.UuidVarchar36Converter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    long   id;
    @Column(columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    @JdbcTypeCode(SqlTypes.UUID)
    UUID   uuid;
    @Column(columnDefinition = "varchar(45)")
    String ip;
    @Column(columnDefinition = "varchar(16)")
    String name;
    @Column(columnDefinition = "timestamp")
    Instant date;

    public int hashCode() {
        return Objects.hashCode(getId());
    }

    public boolean equals(Object other) {
        return other instanceof History ban && ban.getId() == getId();
    }
}
