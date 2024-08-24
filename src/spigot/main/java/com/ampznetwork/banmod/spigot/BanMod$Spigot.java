package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.core.cmd.BanModCommands;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotEventDispatch;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import com.ampznetwork.libmod.api.entity.Player;
import com.ampznetwork.libmod.spigot.SubMod$Spigot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.comroid.api.java.StackTraceUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

@Getter
@Slf4j(topic = BanMod.Strings.AddonName)
public class BanMod$Spigot extends SubMod$Spigot implements BanMod {
    static {
        StackTraceUtils.EXTRA_FILTER_NAMES.add("com.ampznetwork");
    }

    private final SpigotPlayerAdapter playerAdapter = new SpigotPlayerAdapter(this);
    private final SpigotEventDispatch eventDispatch = new SpigotEventDispatch(this);
    private       FileConfiguration   config;

    public BanMod$Spigot() {
        super(
                Set.of(Capability.Database),
                Set.of(Infraction.class, PlayerData.class, PunishmentCategory.class)
        );
        Player.CACHE_NAME = (uuid, name) -> getEntityService().getAccessor(PlayerData.TYPE)
                .getOrCreate(uuid)
                .setUpdateOriginal(merge -> merge.pushKnownName(name))
                .complete(build -> build.id(uuid), player -> player.pushKnownName(name));
    }

    @Override
    public @Nullable String getBanAppealUrl() {
        var url = getConfig().get("ban-appeal-url", null);
        var txt = url == null ? null : url.toString();
        if (txt != null && txt.isBlank()) txt = null;
        return txt;
    }

    @Override
    public Logger log() {
        return log;
    }

    @Override
    public boolean allowUnsafeConnections() {
        return config.getBoolean("allow-unsafe-connections", false);
    }

    @Override
    public void executeSync(Runnable task) {
        Bukkit.getScheduler().runTask(this, task);
    }

    @Override
    public void onLoad() {
        cmdr.register(BanModCommands.class);
        cmdr.register(this);

        super.onLoad();

        if (!getServer().getOnlineMode())
            log.warn(BanMod.Strings.OfflineModeInfo);

        saveDefaultConfig();
        this.config = super.getConfig();
    }

    @Override
    public void onEnable() {
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
}
