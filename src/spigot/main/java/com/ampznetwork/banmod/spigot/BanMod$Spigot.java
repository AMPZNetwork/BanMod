package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.BanModCommands;
import com.ampznetwork.banmod.core.database.file.LocalEntityService;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotEventDispatch;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.func.util.Command;
import org.comroid.api.java.StackTraceUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import static java.time.Duration.*;
import static org.comroid.api.func.util.Streams.append;

@Getter
public class BanMod$Spigot extends JavaPlugin implements BanMod {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    private final SpigotPlayerAdapter playerAdapter = new SpigotPlayerAdapter(this);
    private final SpigotEventDispatch eventDispatch = new SpigotEventDispatch(this);
    private PunishmentCategory muteCategory;
    private PunishmentCategory kickCategory;
    private PunishmentCategory banCategory;
    private FileConfiguration config;
    private Command.Manager cmdr;
    @Delegate(types = {TabCompleter.class, CommandExecutor.class})
    private Command.Manager.Adapter$Spigot adapter;
    private EntityService entityService;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        this.config = super.getConfig();

        this.cmdr = new Command.Manager();
        this.adapter = cmdr.new Adapter$Spigot(this) {
            @Override
            public String handleThrowable(Throwable throwable) {
                return throwable instanceof InvocationTargetException ITEx
                        ? handleThrowable(ITEx.getCause())
                        : super.handleThrowable(new Command.Error(throwable));
            }

            @Override
            protected Stream<Object> collectExtraArgs(@NotNull CommandSender sender) {
                return super.collectExtraArgs(sender)
                        .collect(append(BanMod$Spigot.this, sender instanceof Player player ? player.getUniqueId() : null));
            }
        };
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        var db = getDatabaseInfo();
        this.entityService = switch (db.impl()) {
            case FILE -> new LocalEntityService(this);
            case DATABASE -> new HibernateEntityService(this);
        };

        // default categories
        muteCategory = entityService.findCategory("mute")
                .orElseGet(() -> new PunishmentCategory("mute", Punishment.Mute, ofHours(1), 3));
        kickCategory = entityService.findCategory("kick")
                .orElseGet(() -> new PunishmentCategory("kick", Punishment.Kick, ofSeconds(0), 1));
        banCategory = entityService.findCategory("ban")
                .orElseGet(() -> new PunishmentCategory("ban", Punishment.Ban, ofDays(1), 3));
        entityService.save(muteCategory, kickCategory, banCategory);

        Bukkit.getPluginManager().registerEvents(eventDispatch, this);
    }

    @Override
    @SneakyThrows
    public void onDisable() {
        this.entityService.terminate();
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
        var dbImpl = EntityService.Type.valueOf(config.getString("banmod.entity-service", "database").toUpperCase());
        var dbType = EntityService.DatabaseType.valueOf(config.getString("banmod.database.type", "h2"));
        var dbUrl = config.getString("banmod.database.url", "jdbc:h2:file:./BanMod.h2");
        var dbUser = config.getString("banmod.database.username", "sa");
        var dbPass = config.getString("banmod.database.password", "");
        return new DatabaseInfo(dbImpl, dbType, dbUrl, dbUser, dbPass);
    }
}
