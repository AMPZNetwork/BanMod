package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.core.database.hibernate.unit.BanModCombinedPersistenceUnit;
import com.ampznetwork.banmod.core.database.hibernate.unit.BanModEntityPersistenceUnit;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotEventDispatch;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.func.util.Command;
import org.comroid.api.java.StackTraceUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.stream.Stream;

import static org.comroid.api.Polyfill.parseDuration;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Spigot extends JavaPlugin implements BanMod {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    private final SpigotPlayerAdapter            playerAdapter = new SpigotPlayerAdapter(this);
    private final SpigotEventDispatch            eventDispatch = new SpigotEventDispatch(this);
    private       FileConfiguration              config;
    private       Command.Manager                cmdr;
    @Delegate(types = { TabCompleter.class, CommandExecutor.class })
    private       Command.Manager.Adapter$Spigot adapter;
    private       EntityService                  entityService;
    private       PunishmentCategory             defaultCategory;

    @Override
    public String getMessagingServiceTypeName() {
        return config.getString("messaging-service.type", "polling-db");
    }

    @Override
    public MessagingService.Config getMessagingServiceConfig() {
        switch (getMessagingServiceTypeName()) {
            case "polling-db":
                var interval = parseDuration(config.getString("messaging-service.interval", "2s"));
                var dbInfo = getDatabaseInfo(config.getConfigurationSection("messaging-service"),
                        "MySQL", null, "anonymous", "anonymous");
                return new MessagingService.PollingDatabase.Config(dbInfo, interval);
            case "rabbit-mq":
                return new MessagingService.RabbitMQ.Config(config.getString("messaging-service.uri",
                        "amqp://anonymous:anonymous@localhost:5672/banmod_messaging"));
            default:
                throw new UnsupportedOperationException("Unknown messaging service type: " + getMessagingServiceTypeName());
        }
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public void reload() {
        try {
            onDisable();
        } catch (Throwable ignored) {
        }
        reloadConfig();
        config = getConfig();
        onEnable();
    }

    @Override
    public DatabaseInfo getDatabaseInfo() {
        return getDatabaseInfo(config.getConfigurationSection("database"),
                "h2", "jdbc:h2:file:./BanMod.h2", "sa", "");
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        var url = getConfig().get("ban-appeal-url", null);
        var txt = url == null ? null : url.toString();
        if (txt != null && txt.isBlank()) txt = null;
        return txt;
    }

    @Override
    public boolean allowUnsafeConnections() {
        return config.getBoolean("allow-unsafe-connections", false);
    }

    @Override
    public void executeSync(Runnable task) {
        Bukkit.getScheduler()
                .runTask(this, task);
    }

    @Override
    public void onLoad() {
        if (!getServer().getOnlineMode())
            log.warn("Offline mode is not fully supported! Players can rejoin even after being banned.");

        saveDefaultConfig();
        this.config = super.getConfig();

        this.cmdr = new Command.Manager() {{
            this.<Command.ContextProvider>addChild($ -> Stream.of(BanMod$Spigot.this));
        }};
        this.adapter = cmdr.new Adapter$Spigot(this);
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();
    }

    @Override
    public void onDisable() {
        try {
            if (entityService != null)
                this.entityService.terminate();
        } catch (Throwable t) {
            log().error("Error while disabling", t);
        }
    }

    @Override
    public void onEnable() {
        this.entityService = new HibernateEntityService(this,
                getMessagingServiceConfig().inheritDatasource() ? BanModCombinedPersistenceUnit::new : BanModEntityPersistenceUnit::new);
        defaultCategory    = entityService.defaultCategory();

        Bukkit.getPluginManager().registerEvents(eventDispatch, this);
    }

    @Override
    public boolean checkOpLevel(UUID playerId) {
        return getServer().getOfflinePlayer(playerId).isOp();
    }

    @Override
    public boolean checkOpLevel(UUID playerId, int $) {
        log.debug("Spigot does not support minimum OP levels");
        return checkOpLevel(playerId);
    }

    @Override
    public TriState checkPermission(UUID playerId, String key, boolean explicit) {
        return null;
    }

    private DatabaseInfo getDatabaseInfo(@Nullable ConfigurationSection config, String defType, String defUrl, String defUser, String defPass) {
        if (config == null) config = new MemorySection() {};
        var dbType = EntityService.DatabaseType.valueOf(config.getString("type", defType));
        var dbUrl  = config.getString("url", defUrl);
        var dbUser = config.getString("username", defUser);
        var dbPass = config.getString("password", defPass);
        return new DatabaseInfo(dbType, dbUrl, dbUser, dbPass);
    }
}
