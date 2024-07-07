package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.BanModCommands;
import com.ampznetwork.banmod.core.database.file.LocalEntityService;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.banmod.fabric.adp.internal.FabricPlayerAdapter;
import com.ampznetwork.banmod.fabric.cfg.Config;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Command$Manager$Adapter$Fabric;
import org.comroid.api.java.StackTraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import static java.time.Duration.*;

@Getter
public class BanMod$Fabric implements ModInitializer, BanMod {
    public static final Logger LOGGER = LoggerFactory.getLogger(BanMod.AddonName);

    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    private final FabricPlayerAdapter playerAdapter = new FabricPlayerAdapter(this);
    private final FabricEventDispatch eventDispatch = new FabricEventDispatch(this);
    private Config config = Config.createAndLoad();
    private MinecraftServer server;
    private Command.Manager cmdr;
    private Command$Manager$Adapter$Fabric adapter;
    private EntityService entityService;
    private PunishmentCategory muteCategory;
    private PunishmentCategory kickCategory;
    private PunishmentCategory banCategory;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);

        this.cmdr = new Command.Manager();
        this.adapter = new Command$Manager$Adapter$Fabric(cmdr) {
            @Override
            protected Stream<Object> streamExtraArgs() {
                return Stream.of(BanMod$Fabric.this);
            }

            @Override
            public String handleThrowable(Throwable throwable) {
                return throwable instanceof InvocationTargetException ITEx
                        ? handleThrowable(ITEx.getCause())
                        : super.handleThrowable(new Command.Error(throwable));
            }
        };
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();

        var impl = config.entityService();
        this.entityService = switch (impl) {
            case FILE -> new LocalEntityService(this);
            case DATABASE -> new HibernateEntityService(this);
        };

        // default categories
        muteCategory = entityService.findCategory("mute")
                .orElseGet(() -> new PunishmentCategory("mute", Punishment.Mute, ofHours(1), 3));
        kickCategory = entityService.findCategory("kick")
                .orElseGet(() -> new PunishmentCategory("kick", Punishment.Kick, ofSeconds(0), 1));
        banCategory = entityService.findCategory("ban")
                .orElseGet(() -> new PunishmentCategory("ban", Punishment.Ban, ofDays(1), 2));
        entityService.save(muteCategory, kickCategory, banCategory);
    }

    @Override
    public DatabaseInfo getDatabaseInfo() {
        return new DatabaseInfo(
                config.entityService(),
                config.database.type(),
                config.database.url(),
                config.database.username(),
                config.database.password());
    }

    @Override
    public void reload() {
        config = Config.createAndLoad();
    }
}
