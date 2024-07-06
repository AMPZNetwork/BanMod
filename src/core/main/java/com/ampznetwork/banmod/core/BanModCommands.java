package com.ampznetwork.banmod.core;

import com.ampznetwork.banmod.api.BanMod;
import lombok.experimental.UtilityClass;
import org.comroid.api.func.util.Command;

import java.util.UUID;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.*;

@UtilityClass
public class BanModCommands {
    @Command public String reload(BanMod banMod, UUID playerId) {throw new Command.Error("unimplemented");}
    @Command public String punish(BanMod banMod, UUID playerId) {throw new Command.Error("unimplemented");}
    @Command public String mute(BanMod banMod, UUID playerId) {throw new Command.Error("unimplemented");}
    @Command public String kick(BanMod banMod, UUID playerId) {throw new Command.Error("unimplemented");}
    @Command public String ban(BanMod banMod, UUID playerId) {throw new Command.Error("unimplemented");}
}
