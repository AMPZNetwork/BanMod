package com.ampznetwork.banmod.core.importer.vanilla.entry;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BanEntry {
    LocalDateTime created;
    String source;
    String expires; // = "forever"
    String reason;
}
