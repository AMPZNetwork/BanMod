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

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "banmod_notify")
@IdClass(NotifyEvent.CompositeKey.class)
@ToString(of = { "type", "timestamp", "data" })
@EqualsAndHashCode(of = { "ident", "timestamp" })
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class NotifyEvent implements DbObject {
    @Id                               long    ident;
    @Id @lombok.Builder.Default       Instant timestamp   = Instant.now();
    @lombok.Builder.Default           Type    type        = Type.SYNC;
    @lombok.Builder.Default @Nullable UUID    data        = null;
    @lombok.Builder.Default           long    acknowledge = 0;

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

    @Data
    public static class CompositeKey implements Serializable {
        long ident;
        Instant timestamp;
    }
}
