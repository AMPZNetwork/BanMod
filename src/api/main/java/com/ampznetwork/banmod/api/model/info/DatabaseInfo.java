package com.ampznetwork.banmod.api.model.info;

import com.ampznetwork.banmod.api.database.EntityService;

public record DatabaseInfo(String impl, EntityService.DatabaseType type, String url, String user, String pass) {
}
