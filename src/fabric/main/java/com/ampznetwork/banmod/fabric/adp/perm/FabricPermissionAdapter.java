package com.ampznetwork.banmod.fabric.adp.perm;

import com.ampznetwork.banmod.core.cmd.PermissionAdapter;
import com.ampznetwork.banmod.fabric.BanMod$Fabric;
import lombok.Value;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.util.TriState;
import net.minecraft.server.command.CommandOutput;
import org.comroid.api.func.util.Command;

import java.util.UUID;

@Value
public class FabricPermissionAdapter implements PermissionAdapter<CommandOutput> {
    BanMod$Fabric mod;

    @Override
    public TriState getPermissionState(Command.Usage usage, CommandOutput player, UUID playerId, String node) {
        return switch (Permissions.getPermissionValue(playerId, node).join()) {
            case FALSE -> TriState.FALSE;
            case DEFAULT -> TriState.NOT_SET;
            case TRUE -> TriState.TRUE;
        };
    }
}
