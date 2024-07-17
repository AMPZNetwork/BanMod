package com.ampznetwork.banmod.api.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PlayerResult(
        UUID playerId,
        boolean isMuted,
        boolean isBanned,
        @Nullable String reason,
        @Nullable Instant timestamp,
        @Nullable Instant expires,
        @Nullable UUID detail
) {
}
