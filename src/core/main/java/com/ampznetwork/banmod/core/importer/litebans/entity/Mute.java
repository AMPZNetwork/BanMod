package com.ampznetwork.banmod.core.importer.litebans.entity;

import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
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
@Table(name = "litebans_mutes")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public final class Mute implements LiteBansEntity {
    @Id
    @Column(columnDefinition = "bigint unsigned")
    long id;
    @Column(columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID uuid;
    @Column(columnDefinition = "varchar(45)")
    String ip;
    @Column(columnDefinition = "varchar(2048)")
    String reason;
    @Column(name = "banned_by_uuid", columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID bannedByUuid;
    @Column(name = "banned_by_name", columnDefinition = "varchar(128)")
    String bannedByName;
    @Column(name = "removed_by_uuid", columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID removedByUuid;
    @Column(name = "removed_by_name", columnDefinition = "varchar(128)")
    String removedByName;
    @Column(name = "removed_by_reason", columnDefinition = "varchar(2048)")
    String removedByReason;
    @Column(name = "removed_by_date")
    Instant removedByDate;
    long time;
    long until;
    @Column(name = "server_scope", columnDefinition = "varchar(32)")
    String serverScope;
    @Column(name = "server_origin", columnDefinition = "varchar(32)")
    String serverOrigin;
    boolean silent;
    @Column(name = "ipban")
    boolean ipBan;
    @Column(name = "ipban_wildcard")
    boolean ipBanWildcard;
    boolean active;

    public boolean equals(Object other) {
        return other instanceof Mute mute && mute.getId() == getId();
    }

    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
