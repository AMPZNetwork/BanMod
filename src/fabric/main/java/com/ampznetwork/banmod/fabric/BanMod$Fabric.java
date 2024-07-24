package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.banmod.fabric.adp.internal.FabricPlayerAdapter;
import com.ampznetwork.banmod.fabric.cfg.Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Command$Manager$Adapter$Fabric;
import org.comroid.api.java.StackTraceUtils;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.stream.Stream;

import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;
import static org.comroid.api.Polyfill.parseDuration;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Fabric implements BanMod, ModInitializer, LifeCycle {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    public static Text component2text(Component component) {
        return Text.Serializer.fromJson(gson().serialize(component));
    }
    private final FabricPlayerAdapter            playerAdapter = new FabricPlayerAdapter(this);
    private final FabricEventDispatch            eventDispatch = new FabricEventDispatch(this);
    private       Config                         config        = Config.createAndLoad();
    private       MinecraftServer                server;
    private       Command.Manager                cmdr;
    private       Command$Manager$Adapter$Fabric adapter;
    private       EntityService                  entityService;
    private       PunishmentCategory             defaultCategory;

    @Override
    public DatabaseInfo getDatabaseInfo() {
        return new DatabaseInfo(
                config.database.type(),
                config.database.url(),
                config.database.username(),
                config.database.password());
    }

    @Override
    public String getMessagingServiceTypeName() {
        return config.messagingService.type();
    }

    @Override
    public MessagingService.Config getMessagingServiceConfig() {
        switch (getMessagingServiceTypeName()) {
            case "polling-db":
                var interval = parseDuration(config.messagingService.interval());
                var dbInfo = new DatabaseInfo(EntityService.DatabaseType.MySQL,
                        config.messagingService.url(),
                        config.messagingService.username(),
                        config.messagingService.password());
                return new MessagingService.PollingDatabase.Config(dbInfo, interval);
            case "rabbit-mq":
                return new MessagingService.RabbitMQ.Config(config.messagingService.uri());
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
        terminate();
        initialize();
    }

    @Override
    public boolean allowUnsafeConnections() {
        return config.allowUnsafeConnections();
    }

    @Override
    public void executeSync(Runnable task) {
        task.run(); // todo: is this safe on fabric?
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;

            if (!server.isOnlineMode())
                log.warn("Offline mode is not fully supported! Players can rejoin even after being banned.");
        });

        this.cmdr = new Command.Manager() {{
            this.<Command.ContextProvider>addChild($ -> Stream.of(BanMod$Fabric.this));
            addChildren(Command.PermissionChecker.minecraft(BanMod$Fabric.this));
        }};
        this.adapter = new Command$Manager$Adapter$Fabric(cmdr);
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();

        initialize();
    }

    @Override
    public void initialize() {
        //config.load();

        this.entityService = new HibernateEntityService(this);
        defaultCategory = entityService.defaultCategory();
    }

    @Override
    public void terminate() {
        try {
            if (entityService != null)
                entityService.terminate();
        } catch (Throwable t) {
            log().error("Error while disabling", t);
        }
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        return config.banAppealUrl();
    }

    @Override
    public boolean checkOpLevel(UUID playerId, int minimum) {
        var player = server.getPlayerManager().getPlayer(playerId);
        if (player == null)
            throw new Command.Error("Player not found: " + playerId);
        return player.hasPermissionLevel(minimum);
    }

    @Override
    public TriState checkPermission(UUID playerId, String key, boolean explicit) {
        return switch (Permissions.getPermissionValue(playerId, key).join()) {
            case FALSE -> TriState.FALSE;
            case DEFAULT -> TriState.NOT_SET;
            case TRUE -> TriState.TRUE;
        };
    }
}
