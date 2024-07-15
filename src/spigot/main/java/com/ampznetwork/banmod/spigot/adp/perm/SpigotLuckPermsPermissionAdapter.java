package com.ampznetwork.banmod.spigot.adp.perm;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.cmd.LuckPermsPermissionAdapterBase;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public class SpigotLuckPermsPermissionAdapter extends LuckPermsPermissionAdapterBase<CommandSender> {
    public SpigotLuckPermsPermissionAdapter(@NotNull BanMod mod, @NotNull LuckPerms luckPerms) {
        super(mod, luckPerms);
    }

    @Override
    public void calculate(@NonNull CommandSender sender, @NonNull ContextConsumer consumer) {
        if (!(sender instanceof Player target))
            return;
        var result = mod.getEntityService().queuePlayer(target.getUniqueId());
        populateContext(consumer, result,
                target.getWorld().getName(),
                target.getGameMode().name().toLowerCase());
    }
}
