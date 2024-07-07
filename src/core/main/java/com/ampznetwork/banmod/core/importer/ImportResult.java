package com.ampznetwork.banmod.core.importer;

public record ImportResult(int muteCount, int banCount) {
    public static final ImportResult ZERO = new ImportResult(0, 0);
}
