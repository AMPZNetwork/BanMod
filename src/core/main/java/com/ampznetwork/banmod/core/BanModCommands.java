package com.ampznetwork.banmod.core;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.model.Punishment;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.comroid.api.func.util.Command;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static java.time.Instant.now;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static org.comroid.api.func.util.Command.Arg;

@UtilityClass
public class BanModCommands {
    @Command
    public Component reload(BanMod banMod) {
        banMod.reload();
        return text("Configuration reloaded!")
                .color(GREEN);
    }

    @Command
    public Component lookup(BanMod banMod, @Arg String name) {
        // todo: use book adapter here?
        var target = banMod.getPlayerAdapter().getId(name);
        var data = banMod.getEntityService().getPlayerData(target)
                .orElseThrow(() -> new Command.Error("Player not found"));
        var text = text("Player ")
                .append(text(name).color(AQUA))
                .append(text("\n"))
                .append(text("ID: "))
                .append(text(target.toString()))
                .append(text("\n"))
                .append(text("Known Names:"));
        for (var knownName : data.getKnownNames())
            text = text.append(text("- "))
                    .append(text(knownName))
                    .append(text("\n"));
        text = text.append(text("Known IPs:"));
        for (var knownIp : data.getKnownIPs())
            text = text.append(text("- "))
                    .append(text(knownIp.toString()))
                    .append(text("\n"));
        text = text.append(text("Active Infractions:"));
        for (var infraction : banMod.getEntityService().getInfractions(target)
                .filter(i -> i.getExpires() == null || i.getExpires().isAfter(now()))
                .toList())
            text = text.append(textPunishment(infraction.getCategory().getPunishment()))
                    .append(text(" by "))
                    .append(text(infraction.getIssuer() == null
                            ? "Console"
                            : banMod.getPlayerAdapter().getName(infraction.getIssuer())))
                    .append(text("\n"));
        return text;
    }

    private Component textPunishment(Punishment punishment) {
        return text(switch (punishment) {
            case Mute -> "Muted";
            case Ban -> "Banned";
            default -> throw new IllegalStateException("Unexpected value: " + punishment);
        }).color(switch (punishment) {
            case Mute -> RED;
            case Ban -> DARK_RED;
            default -> throw new IllegalStateException("Unexpected value: " + punishment);
        })
    }

    @Command
    public Component punish(BanMod banMod, UUID issuer, @Arg String name, @Arg String category, @Nullable @Arg String reason) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var cat = banMod.getEntityService().findCategory(category)
                .orElseThrow(() -> new Command.Error("Unknown category: " + category));
        var rep = banMod.getEntityService().findRepetition(tgt, cat);

        // create infraction
        var infraction = Infraction.builder()
                .playerId(tgt)
                .punishment(cat.getPunishment())
                .issuer(issuer)
                .reason(reason)
                .expires(now().plus(cat.calculateDuration(rep)))
                .build();
        banMod.getEntityService().save(infraction);

        // apply infraction
        var punishment = cat.getPunishment();
        if (punishment != Punishment.Mute)
            banMod.getPlayerAdapter().kick(tgt, infraction.getReason());

        return text("User ")
                .append(text(name).color(AQUA))
                .append(text(" was "))
                .append(textPunishment(punishment));
    }

    @Command
    public Component mute(BanMod banMod, UUID issuer, @Arg String name, @Nullable @Arg String reason) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var cat = banMod.getMuteCategory();
        var rep = banMod.getEntityService().findRepetition(tgt, cat);

        // create infraction
        var infraction = Infraction.builder()
                .playerId(tgt)
                .punishment(cat.getPunishment())
                .issuer(issuer)
                .reason(reason)
                .expires(now().plus(cat.calculateDuration(rep)))
                .build();
        banMod.getEntityService().save(infraction);
        throw new Command.Error("unimplemented");
    }

    @Command
    public Component kick(BanMod banMod, UUID issuer, @Arg String name, @Nullable @Arg String reason) {
        throw new Command.Error("unimplemented");
    }

    @Command
    public Component ban(BanMod banMod, UUID issuer, @Arg String name, @Nullable @Arg String reason) {
        throw new Command.Error("unimplemented");
    }

    @Command
    public class category {
        @Command
        public Component list(BanMod banMod, UUID issuer) {
            throw new Command.Error("unimplemented");
        }

        @Command
        public Component create(BanMod banMod, UUID issuer, @Arg String name, @Arg String baseDuration, @Nullable @Arg Double repetitionFactor) {
            if (repetitionFactor != null)
                repetitionFactor = Math.max(2, repetitionFactor);
            else repetitionFactor = 2d;
            throw new Command.Error("unimplemented");
        }

        @Command
        public Component delete(BanMod banMod, UUID issuer, @Arg String name) {
            throw new Command.Error("unimplemented");
        }

        @Command("generate-defaults")
        public Component generateDefaults(BanMod banMod, UUID issuer) {
            throw new Command.Error("unimplemented");
        }
    }
}
