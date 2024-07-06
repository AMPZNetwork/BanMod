package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.core.database.file.LocalEntityService;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotEventDispatch;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.func.util.Command;
import org.comroid.api.java.StackTraceUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import static org.comroid.api.func.util.Streams.append;

@Getter
public class BanMod$Spigot extends JavaPlugin implements BanMod {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    private final SpigotPlayerAdapter playerAdapter = new SpigotPlayerAdapter(this);
    private final SpigotEventDispatch eventDispatch = new SpigotEventDispatch(this);
    private FileConfiguration config;
    private EntityService entityService;
    private Command.Manager cmdr;
    private Command.Manager.Adapter$Spigot adapter;

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
                if (!(sender instanceof Player player))
                    throw new Command.Error("Cannot be used from console");
                return super.collectExtraArgs(sender)
                        .collect(append(BanMod$Spigot.this));
            }
        };
        cmdr.register(BanMod$Spigot.class);
        cmdr.register(this);
        cmdr.initialize();
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        var dbImpl = config.getString("worldmod.entity-service", "database");
        var dbType = EntityService.DatabaseType.valueOf(config.getString("banmod.database.type", "h2"));
        var dbUrl = config.getString("worldmod.database.url", "jdbc:h2:file:./banmod.h2");
        var dbUser = config.getString("worldmod.database.username", "sa");
        var dbPass = config.getString("worldmod.database.password", "");
        this.entityService = switch (dbImpl.toLowerCase()) {
            case "file", "local" -> new LocalEntityService(this);
            case "hibernate", "database" -> new HibernateEntityService(this, dbType, dbUrl, dbUser, dbPass);
            default -> throw new IllegalStateException("Unexpected value: " + dbImpl.toLowerCase());
        };

        //getPluginManager().registerEvents(eventDispatch, this);
    }

    @Override
    @SneakyThrows
    public void onDisable() {
        this.entityService.terminate();
    }
}
