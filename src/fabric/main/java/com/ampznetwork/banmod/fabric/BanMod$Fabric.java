package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.banmod.fabric.adp.internal.FabricPlayerAdapter;
import com.ampznetwork.banmod.fabric.cfg.Config;
import com.ampznetwork.libmod.api.messaging.MessagingService;
import com.ampznetwork.libmod.api.model.info.DatabaseInfo;
import com.ampznetwork.libmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.libmod.fabric.SubMod$Fabric;
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
import org.comroid.api.java.StackTraceUtils;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;
import static org.comroid.api.Polyfill.parseDuration;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Fabric extends SubMod$Fabric implements BanMod, ModInitializer, LifeCycle {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    public static Text component2text(Component component) {
        return Text.Serializer.fromJson(gson().serialize(component));
    }

    private final FabricPlayerAdapter playerAdapter = new FabricPlayerAdapter(this);
    private final FabricEventDispatch eventDispatch = new FabricEventDispatch(this);
    private       Config              config        = Config.createAndLoad();
    private       MinecraftServer     server;

    @Override
    public String getMessagingServiceTypeName() {
        return config.messagingService.type();
    }

    @Override
    public MessagingService.Config getMessagingServiceConfig() {
        switch (getMessagingServiceTypeName()) {
            case "polling-db":
                var interval = parseDuration(config.messagingService.interval());
                var dbInfo = getDatabaseInfo(config.messagingService.database);
                return new MessagingService.PollingDatabase.Config(dbInfo, interval);
            case "rabbit-mq":
                return new MessagingService.RabbitMQ.Config(config.messagingService.uri());
            default:
                throw new UnsupportedOperationException("Unknown messaging service type: " + getMessagingServiceTypeName());
        }
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        return config.banAppealUrl();
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
                log.warn(BanMod.Strings.OfflineModeInfo);
        });

        cmdr.register(this);

        initialize();
    }

    @Override
    public void initialize() {
        //config.load();

        this.entityService = new HibernateEntityService(this,
                getMessagingServiceConfig().inheritDatasource() ? BanModCombinedPersistenceUnit::new : BanModEntityPersistenceUnit::new);
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

    @Contract("null -> null; !null -> new")
    private DatabaseInfo getDatabaseInfo(Config.Database config) {
        return config == null ? null : new DatabaseInfo(config.type(), config.url(), config.username(), config.password());
    }
}
