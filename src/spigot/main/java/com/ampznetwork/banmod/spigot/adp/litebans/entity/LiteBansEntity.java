package com.ampznetwork.banmod.spigot.adp.litebans.entity;


import com.ampznetwork.banmod.api.model.convert.UuidVarchar36Converter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class LiteBansEntity {
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
    @Column(columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID bannedByUuid;
    @Column(columnDefinition = "varchar(128)")
    String bannedByName;
    @Column(columnDefinition = "varchar(36)")
    @Convert(converter = UuidVarchar36Converter.class)
    UUID removedByUuid;
    @Column(columnDefinition = "varchar(128)")
    String removedByName;
    @Column(columnDefinition = "varchar(2048)")
    String removedByReason;
    Instant removedByDate;
    long time;
    long until;
    @Column(columnDefinition = "varchar(32)")
    String serverScope;
    @Column(columnDefinition = "varchar(32)")
    String serverOrigin;
    boolean silent;
    boolean ipBan;
    boolean ipBanWildcard;
    boolean active;
    @Column(columnDefinition = "tinyint unsigned")
    byte template;
}
