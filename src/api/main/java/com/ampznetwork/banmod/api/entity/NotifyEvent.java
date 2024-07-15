package com.ampznetwork.banmod.api.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banmod_notify")
@EqualsAndHashCode(of = { "incr" })
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(of = { "ident", "timestamp", "type", "data" })
public final class NotifyEvent implements DbObject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) long    incr;
    @lombok.Builder.Default                                 Instant timestamp = Instant.now();
    @lombok.Builder.Default                                 Type    type      = Type.SYNC;
    @lombok.Builder.Default @Nullable                       UUID    data      = null;
    @lombok.Builder.Default
    @Column(columnDefinition = "varchar(64)")               String  ident     = "";

    public enum Type implements Named {
        /**
         * sent immediately after connecting for the first time, together with an {@code ident} value
         */
        HELLO,
        /**
         * sent with a player ID as {@code data} after storing an infraction
         * after polling SYNC, it is expected to merge thyself into ident
         */
        SYNC
    }
}
