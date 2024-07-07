package com.ampznetwork.banmod.core.importer.vanilla.entry;

import lombok.Data;

import java.util.UUID;

@Data
public final class Ban extends BanEntry {
    UUID uuid;
    String name;
}
