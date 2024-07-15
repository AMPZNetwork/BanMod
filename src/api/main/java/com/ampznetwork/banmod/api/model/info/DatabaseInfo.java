package com.ampznetwork.banmod.api.model.info;

import com.ampznetwork.libmod.api.database.EntityService;

public record DatabaseInfo(
        EntityService.Type impl,
        EntityService.DatabaseType type,
        String url,
        String user,
        String pass
) {
}
