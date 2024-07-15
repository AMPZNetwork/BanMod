package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.core.cmd.PermissionAdapter;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.banmod.fabric.adp.internal.FabricPlayerAdapter;
import com.ampznetwork.banmod.fabric.adp.perm.FabricLuckPermsPermissionAdapter;
import com.ampznetwork.banmod.fabric.adp.perm.FabricPermissionAdapter;
import com.ampznetwork.banmod.fabric.cfg.Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Command$Manager$Adapter$Fabric;
import org.comroid.api.java.SoftDepend;
import org.comroid.api.java.StackTraceUtils;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.stream.Stream;

import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.*;

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

        var permAdapter = SoftDepend.type("net.luckperms.api.LuckPerms").wrap()
                .map($ -> LuckPermsProvider.get())
                .map(lp -> {
                    var permissionAdapter = new FabricLuckPermsPermissionAdapter(this, lp);
                    lp.getContextManager().registerCalculator(permissionAdapter);
                    return (PermissionAdapter) permissionAdapter;
                })
                .orElseGet(() -> new FabricPermissionAdapter(this));

        this.cmdr = new Command.Manager() {{
            this.<Command.ContextProvider>addChild($ -> Stream.of(BanMod$Fabric.this, permAdapter));
        }};
        this.adapter = new Command$Manager$Adapter$Fabric(cmdr);
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();

        initialize();
    }

    @Override
    public void initialize() {
        config.load();

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
}
