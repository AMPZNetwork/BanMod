package com.ampznetwork.banmod.fabric.cfg;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.Nest;
import io.wispforest.owo.config.annotation.RestartRequired;

@Modmenu(modId = BanMod.AddonId)
@SuppressWarnings({"FieldMayBeFinal", "unused"})
@Config(name = "banMod-config", wrapperName = "Config")
public class ConfigModel {
    @RestartRequired
    EntityService.Type entityService = EntityService.Type.DATABASE;
    @Nest
    Database database;

    public static class Database {
        EntityService.DatabaseType type = EntityService.DatabaseType.h2;
        String url = "jdbc:h2:file:./BanMod.h2";
        String username = "sa";
        String password = "";
    }
}
