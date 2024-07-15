package com.ampznetwork.banmod.spigot.adp.perm;

import com.ampznetwork.banmod.core.cmd.PermissionAdapter;
import com.ampznetwork.banmod.spigot.BanMod$Spigot;
import lombok.Value;
import net.kyori.adventure.util.TriState;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.command.CommandSender;
import org.comroid.api.func.util.Command;

import java.util.UUID;

@Value
public class VaultPermissionAdapter implements PermissionAdapter<CommandSender> {
    BanMod$Spigot mod;
    Permission vault;

    @Override
    public TriState getPermissionState(Command.Usage usage, CommandSender player, UUID playerId, String key) {
        return TriState.byBoolean(vault.has(player, key));
    }
}
