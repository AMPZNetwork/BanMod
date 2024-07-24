package com.ampznetwork.banmod.fabric.cfg;

import blue.endless.jankson.Comment;
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
    public Database         database               = new Database();
    @Nest
    public MessagingService messagingService       = new MessagingService();
    public String           banAppealUrl           = "";
    public boolean          allowUnsafeConnections = false;

    public static class Database {
        public EntityService.DatabaseType type     = EntityService.DatabaseType.h2;
        public String                     url      = "jdbc:h2:file:./BanMod.h2";
        public String                     username = "sa";
        public String                     password = "";
    }

    public static class MessagingService {
        @Comment("polling-db or rabbit-mq")
        public String type     = "polling-db";
        @Comment("use with 'type=polling-db', example: jdbc:mariadb://localhost:3306/banmod_messaging")
        public String url      = null;
        @Comment("use with 'type=polling-db'")
        public String username = "anonymous";
        @Comment("use with 'type=polling-db'")
        public String password = "anonymous";
        @Comment("use with 'type=polling-db'")
        public String interval = "2s";
        @Comment("use with 'type=rabbit-mq', example: amqp://anonymous:anonymous@localhost:5672/banmod_messaging")
        public String uri      = null;
    }
}
