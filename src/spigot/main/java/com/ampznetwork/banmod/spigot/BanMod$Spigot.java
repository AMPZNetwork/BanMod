package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.core.cmd.PermissionAdapter;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotEventDispatch;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import com.ampznetwork.banmod.spigot.adp.perm.SpigotLuckPermsPermissionAdapter;
import com.ampznetwork.banmod.spigot.adp.perm.SpigotPermissionAdapter;
import com.ampznetwork.banmod.spigot.adp.perm.VaultPermissionAdapter;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.func.util.Command;
import org.comroid.api.java.StackTraceUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.stream.Stream;

import static org.comroid.api.func.util.Streams.cast;

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
    public DatabaseInfo getDatabaseInfo() {
        var dbType = EntityService.DatabaseType.valueOf(config.getString("database.type", "h2"));
        var dbUrl  = config.getString("database.url", "jdbc:h2:file:./BanMod.h2");
        var dbUser = config.getString("database.username", "sa");
        var dbPass = config.getString("database.password", "");
        return new DatabaseInfo(dbType, dbUrl, dbUser, dbPass);
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

        var permAdapter = Stream.ofNullable(Bukkit.getPluginManager().getPlugin("LuckPerms"))
                .flatMap(cast(LuckPerms.class))
                .findAny()
                .map(lp -> {
                    var permissionAdapter = new SpigotLuckPermsPermissionAdapter(this, lp);
                    lp.getContextManager().registerCalculator(permissionAdapter);
                    return (PermissionAdapter) permissionAdapter;
                })
                .or(() -> Stream.ofNullable(Bukkit.getPluginManager().getPlugin("Vault"))
                        .findAny()
                        .flatMap($ -> Optional.ofNullable(getServer().getServicesManager()
                                .getRegistration(net.milkbowl.vault.permission.Permission.class)))
                        .map(RegisteredServiceProvider::getProvider)
                        .map(vault -> new VaultPermissionAdapter(this, vault)))
                .orElseGet(() -> new SpigotPermissionAdapter(this));

        this.cmdr = new Command.Manager() {{
            this.<Command.ContextProvider>addChild($ -> Stream.of(BanMod$Spigot.this, permAdapter));
        }};
        this.adapter = cmdr.new Adapter$Spigot(this);
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();
    }

    @Override
    public void onEnable() {
        this.entityService = new HibernateEntityService(this);
        defaultCategory = entityService.defaultCategory();

        Bukkit.getPluginManager().registerEvents(eventDispatch, this);
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
}
