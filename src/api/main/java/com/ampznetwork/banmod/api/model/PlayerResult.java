package com.ampznetwork.banmod.api.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlayerResult(
        UUID playerId,
        boolean isMuted,
        boolean isBanned,
        @Nullable String reason
) {
}
