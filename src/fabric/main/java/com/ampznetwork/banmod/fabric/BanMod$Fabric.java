package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.fabric.adp.internal.FabricEventDispatch;
import com.ampznetwork.libmod.fabric.LibMod$Fabric;
import com.ampznetwork.libmod.fabric.SubMod$Fabric;
import com.ampznetwork.libmod.fabric.config.Config;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.util.TriState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import org.comroid.api.func.util.Command;
import org.comroid.api.java.StackTraceUtils;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Fabric extends SubMod$Fabric implements BanMod, ModInitializer, LifeCycle {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    public static Text component2text(net.kyori.adventure.text.Component component) {
        var json = GsonComponentSerializer.gson().serializeToTree(component);
        return TextCodecs.STRINGIFIED_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
    }

    private final FabricEventDispatch eventDispatch = new FabricEventDispatch(this);
    private BanModConfig config = Config.createAndLoad(BanModConfig.class);
    private       MinecraftServer     server;

    public BanMod$Fabric() {
        super(Set.of(Capability.Database), Set.of(Infraction.class, Player.class, PunishmentCategory.class));
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        return config.getBanAppealUrl();
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public boolean allowUnsafeConnections() {
        return config.isAllowUnsafeConnections();
    }

    @Override
    public void onInitialize() {
        (super.lib = LibMod$Fabric.INSTANCE).register(this);

        super.onInitialize();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;

            if (!server.isOnlineMode())
                log.warn(BanMod.Strings.OfflineModeInfo);
        });
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
