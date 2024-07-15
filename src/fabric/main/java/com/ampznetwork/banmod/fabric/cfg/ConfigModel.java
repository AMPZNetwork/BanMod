package com.ampznetwork.banmod.fabric.cfg;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.Nest;

@Modmenu(modId = BanMod.Strings.AddonId)
@SuppressWarnings({ "FieldMayBeFinal", "unused" })
@Config(name = "banMod-config", wrapperName = "Config")
public class ConfigModel {
    @Nest
    public Database database               = new Database();
    public String   banAppealUrl           = "";
    public boolean  allowUnsafeConnections = false;

    public static class Database {
        public EntityService.DatabaseType type     = EntityService.DatabaseType.h2;
        public String                     url      = "jdbc:h2:file:./BanMod.h2";
        public String                     username = "sa";
        public String                     password = "";
    }
}
