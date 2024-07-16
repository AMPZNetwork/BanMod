package com.ampznetwork.banmod.core.importer.litebans.entity;

import java.time.Instant;
import java.util.UUID;

public interface LiteBansEntity {
    UUID getUuid();

    long getTime();

    long getUntil();

    String getReason();

    UUID getBannedByUuid();

    UUID getRemovedByUuid();

    Instant getRemovedByDate();
}
