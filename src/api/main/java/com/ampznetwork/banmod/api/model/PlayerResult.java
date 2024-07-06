package com.ampznetwork.banmod.api.model;

import java.util.UUID;

public record PlayerResult(UUID playerId, boolean isMuted, boolean isBanned) {
}
