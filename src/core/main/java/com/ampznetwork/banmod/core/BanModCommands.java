package com.ampznetwork.banmod.core;

import com.ampznetwork.banmod.api.BanMod;
import lombok.experimental.UtilityClass;
import org.comroid.api.func.util.Command;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.*;

@UtilityClass
public class BanModCommands {
    @Command public String reload(BanMod banMod, UUID issuer) {throw new Command.Error("unimplemented");}
    @Command public class category {
        @Command public String list(BanMod banMod, UUID issuer) {throw new Command.Error("unimplemented");}
        @Command public String create(BanMod banMod, UUID issuer, @Command.Arg String name, @Command.Arg String baseDuration, @Command.Arg @Nullable Double repetitionFactor) {throw new Command.Error("unimplemented");}
        @Command public String delete(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
        @Command("generate-defaults") public String generateDefaults(BanMod banMod, UUID issuer) {throw new Command.Error("unimplemented");}
    }
    @Command public String lookup(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
    @Command public String punish(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
    @Command public String mute(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
    @Command public String kick(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
    @Command public String ban(BanMod banMod, UUID issuer, @Command.Arg String name) {throw new Command.Error("unimplemented");}
}
