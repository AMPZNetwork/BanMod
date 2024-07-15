package com.ampznetwork.banmod.spigot.adp.perm;

import com.ampznetwork.banmod.core.cmd.PermissionAdapter;
import com.ampznetwork.banmod.spigot.BanMod$Spigot;
import lombok.Value;
import net.kyori.adventure.util.TriState;
import org.bukkit.command.CommandSender;
import org.comroid.api.func.util.Command;

import java.util.UUID;

@Value
public class SpigotPermissionAdapter implements PermissionAdapter<CommandSender> {
    BanMod$Spigot mod;

    @Override
    public TriState getPermissionState(Command.Usage usage, CommandSender sender, UUID playerId, String key) {
        return sender.hasPermission(key) ? TriState.TRUE : sender.isPermissionSet(key) ? TriState.FALSE : TriState.NOT_SET;
    }
}
