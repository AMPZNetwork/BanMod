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
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banmod_notify")
@IdClass(NotifyEvent.CompositeKey.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = { "ident", "timestamp" })
@ToString(of = { "type", "timestamp", "infraction" })
public final class NotifyEvent {
    @Id @Column(columnDefinition = "bigint(64)") long       ident;
    @Id @lombok.Builder.Default                  Instant    timestamp   = Instant.now();
    @lombok.Builder.Default                      Type       type        = Type.SYNC;
    @lombok.Builder.Default @Nullable @ManyToOne Infraction infraction  = null;
    @lombok.Builder.Default
    @Column(columnDefinition = "bigint(64)")     long       acknowledge = 0;

    public enum Type implements Named {
        /**
         * sent immediately after connecting for the first time, together with an {@code ident} value
         */
        HELLO,
        /**
         * sent with an infraction ID as {@code data} after storing an infraction
         * after polling SYNC, it is expected to merge thyself into ident
         */
        SYNC
    }

    @Data
    public static class CompositeKey implements Serializable {
        long ident;
        Instant timestamp;
    }
}
