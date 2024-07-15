package com.ampznetwork.banmod.api.model.info;

public record DatabaseInfo(
        EntityService.Type impl,
        EntityService.DatabaseType type,
        String url,
        String user,
        String pass
) {
}
