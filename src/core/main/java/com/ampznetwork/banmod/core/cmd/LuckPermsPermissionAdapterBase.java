package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.PlayerResult;
import lombok.Value;
import lombok.experimental.NonFinal;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import org.jetbrains.annotations.NotNull;

@Value
@NonFinal
public abstract class LuckPermsPermissionAdapterBase<Player> implements PermissionAdapter<Player>, ContextCalculator<Player> {
    protected @NotNull BanMod mod;
    protected @NotNull LuckPerms luckPerms;

    protected void populateContext(ContextConsumer consumer, PlayerResult result, String worldName, String gameMode) {
        consumer.accept("muted", String.valueOf(result.isMuted()));
        consumer.accept("world", worldName);
        consumer.accept("gamemode", gameMode);
    }
}
