package com.ampznetwork.banmod.fabric.adp.perm;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.cmd.LuckPermsPermissionAdapterBase;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextConsumer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

public class FabricLuckPermsPermissionAdapter extends LuckPermsPermissionAdapterBase<CommandOutput> {
    public FabricLuckPermsPermissionAdapter(@NotNull BanMod mod, @NotNull LuckPerms luckPerms) {
        super(mod, luckPerms);
    }

    @Override
    public void calculate(@NotNull CommandOutput sender, @NotNull ContextConsumer consumer) {
        if (!(sender instanceof ServerPlayerEntity target))
            return;
        var result = mod.getEntityService().queuePlayer(target.getUuid());
        populateContext(consumer, result,
                target.getWorld().getDimensionKey().getValue().toString(),
                target.interactionManager.getGameMode().getName().toLowerCase());
    }
}
