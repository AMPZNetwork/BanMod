package com.ampznetwork.banmod.api.model;

import com.ampznetwork.libmod.api.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public record PlayerResult(
        Player player,
        boolean isMuted,
        boolean isBanned,
        @Nullable String reason,
        @Nullable Instant timestamp,
        @Nullable Instant expires,
        @Nullable Player detail
) {
}
