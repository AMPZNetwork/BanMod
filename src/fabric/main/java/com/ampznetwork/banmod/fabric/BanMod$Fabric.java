package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.banmod.fabric.adp.internal.FabricPlayerAdapter;
import com.ampznetwork.banmod.fabric.cfg.Config;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Command$Manager$Adapter$Fabric;
import org.comroid.api.java.StackTraceUtils;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.stream.Stream;

import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Fabric implements BanMod, ModInitializer, LifeCycle {
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
    private PunishmentCategory defaultCategory;

    public static Text component2text(Component component) {
        return Text.Serializer.fromJson(gson().serialize(component));
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public DatabaseInfo getDatabaseInfo() {
        return new DatabaseInfo(
                config.database.type(),
                config.database.url(),
                config.database.username(),
                config.database.password());
    }

    @Override
    public void reload() {
        config = Config.createAndLoad();
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
            this.addChild(Command.PermissionChecker.minecraft(playerAdapter));
        }};
        this.adapter = new Command$Manager$Adapter$Fabric(cmdr);
        cmdr.register(BanModCommands.class);
        cmdr.register(this);
        cmdr.initialize();
    }

    @Override
    public void initialize() {
        config.load();

        this.entityService = new HibernateEntityService(this);
        defaultCategory = entityService.defaultCategory();
    }

    @Override
    @SneakyThrows
    public void terminate() {
        entityService.terminate();
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        return config.banAppealUrl();
    }

    @Override
    public boolean allowUnsafeConnections() {
        return config.allowUnsafeConnections();
    }
}
