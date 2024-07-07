package com.ampznetwork.banmod.core;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.importer.litebans.LiteBansImporter;
import com.ampznetwork.banmod.core.importer.vanilla.VanillaBansImporter;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import org.comroid.annotations.Alias;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Streams;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.time.Instant.now;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.openUrl;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static org.comroid.api.Polyfill.parseDuration;
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
        // todo: use book adapter here
        var target = banMod.getPlayerAdapter().getId(name);
        var data = banMod.getEntityService().getPlayerData(target)
                .orElseThrow(() -> new Command.Error("Player not found"));
        var text = text("Player ")
                .append(text(name).color(AQUA))
                .append(text("\n"))
                .append(text("ID: "))
                .append(text(target.toString())
                        .clickEvent(openUrl("https://namemc.com/profile/" + target))
                        .hoverEvent(showText(text("Open on NameMC.com")))
                        .color(YELLOW))
                .append(text("\n"))
                .append(text("Known Names:"));
        for (var knownName : data.getKnownNames())
            text = text.append(text("\n- "))
                    .append(text(knownName).color(YELLOW))
                    .append(text("\n"));
        text = text.append(text("Known IPs:"));
        for (var knownIp : data.getKnownIPs())
            text = text.append(text("\n- "))
                    .append(text(knownIp.toString()).color(YELLOW))
                    .append(text("\n"));
        text = text.append(text("Active Infractions:"));
        var infractions = banMod.getEntityService().getInfractions(target)
                .filter(Infraction.IS_IN_EFFECT)
                .toList();
        if (infractions.isEmpty())
            text = text.append(text("\n- (none)").color(GRAY));
        else for (var infraction : infractions)
            text = text.append(text("\n- "))
                    .append(textPunishment(infraction.getCategory().getPunishment()))
                    .append(text(" by "))
                    .append(text(infraction.getIssuer() == null
                            ? "Server"
                            : banMod.getPlayerAdapter().getName(infraction.getIssuer()))
                            .color(AQUA));
        return text;
    }

    @Command
    public Component punish(BanMod banMod, UUID issuer, @Arg String name, @Arg String category, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        var cat = banMod.getEntityService().findCategory(category)
                .orElseThrow(() -> new Command.Error("Unknown category: " + category));
        var infraction = standardInfraction(banMod, cat, tgt, issuer, reason)
                .build();
        banMod.getEntityService().save(infraction);

        // apply infraction
        var punishment = infraction.getCategory().getPunishment();
        if (punishment != Punishment.Mute)
            banMod.getPlayerAdapter().kick(tgt, infraction.getReason());

        return textPunishmentFull(name, punishment, reason);
    }

    @Command
    public Component mutelist(BanMod banMod, @Nullable @Arg Integer page) {
        return infractionList(banMod, page == null ? 1 : page, Punishment.Mute);
    }

    @Command
    public Component tempmute(BanMod banMod, UUID issuer, @Arg String name, @Arg String durationText, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        banMod.getEntityService().save(infraction);
        return textPunishmentFull(name, Punishment.Mute, infraction.getReason());
    }

    @Command
    public Component mute(BanMod banMod, UUID issuer, @Arg String name, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var infraction = standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason).expires(null).build();
        banMod.getEntityService().save(infraction);
        return textPunishmentFull(name, Punishment.Mute, infraction.getReason());
    }

    @Command
    public Component unmute(BanMod banMod, UUID issuer, @Arg String name, @Nullable String[] args) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Mute)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not muted"));
        infraction.setRevoker(issuer);
        banMod.getEntityService().save(infraction);
        return text("User " + name + " was unmuted").color(GREEN);
    }

    @Command
    public Component kick(BanMod banMod, UUID issuer, @Arg String name, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = standardInfraction(banMod, banMod.getKickCategory(), tgt, issuer, reason)
                .expires(null)
                .build();
        banMod.getEntityService().save(infraction);
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Kick, infraction.getReason());
    }

    @Command
    public Component banlist(BanMod banMod, @Nullable @Arg Integer page) {
        return infractionList(banMod, page == null ? 1 : page, Punishment.Ban);
    }

    @Command
    public Component tempban(BanMod banMod, UUID issuer, @Arg String name, @Arg String durationText, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        banMod.getEntityService().save(infraction);
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Ban, infraction.getReason());
    }

    @Command
    public Component ban(BanMod banMod, UUID issuer, @Arg String name, @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var infraction = standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason).expires(null).build();
        banMod.getEntityService().save(infraction);
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Ban, infraction.getReason());
    }

    @Command
    public Component unban(BanMod banMod, UUID issuer, @Arg String name, @Nullable String[] args) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Ban)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not banned"));
        infraction.setRevoker(issuer);
        banMod.getEntityService().save(infraction);
        return text("User " + name + " was unbanned").color(GREEN);
    }

    private Component infractionList(BanMod banMod, int page, Punishment punishment) {
        final var infractions = banMod.getEntityService().getInfractions().toList();
        final var perPage = 8;
        final var pageCount = infractions.size() / perPage;
        // todo: use book adapter here
        return infractions.stream()
                .filter(Infraction.IS_IN_EFFECT)
                .filter(i -> i.getCategory().getPunishment() == punishment)
                .skip((page - 1L) * perPage)
                .limit(perPage)
                .map(i -> text("\n- ")
                        .append(textPunishmentFull(banMod.getPlayerAdapter().getName(i.getPlayerId()),
                                i.getCategory().getPunishment(),
                                i.getReason())))
                .collect(Streams.atLeastOneOrElseGet(() -> text("\n- ")
                        .append(text("(none)").color(GRAY))))
                .collect(Collector.of(() -> Component.text()
                                .append(text(punishment.name() + "list (Page %d of %d)".formatted(page, pageCount))),
                        ComponentBuilder::append,
                        (l, r) -> {
                            l.append(r);
                            return l;
                        },
                        ComponentBuilder::build));
    }

    private Infraction.Builder standardInfraction(BanMod banMod,
                                                  PunishmentCategory category,
                                                  UUID target,
                                                  @Nullable UUID issuer,
                                                  @Nullable String reason) {
        var rep = banMod.getEntityService().findRepetition(target, category);
        var now = now();
        return Infraction.builder()
                .playerId(target)
                .category(category)
                .issuer(issuer)
                .reason(reason)
                .timestamp(now)
                .expires(now.plus(category.calculateDuration(rep)));
    }

    private Component textPunishmentFull(String username, Punishment punishment, @Nullable String reason) {
        var text = text("User ")
                .append(text(username).color(AQUA))
                .append(text(" has been "))
                .append(textPunishment(punishment));
        if (reason != null)
            text = text.append(text(": "))
                    .append(text(reason).color(LIGHT_PURPLE));
        return text;
    }

    private Component textPunishment(Punishment punishment) {
        return text(switch (punishment) {
            case Mute -> "muted";
            case Kick -> "kicked";
            case Ban -> "banned";
        }).color(switch (punishment) {
            case Mute -> YELLOW;
            case Kick -> RED;
            case Ban -> DARK_RED;
        });
    }

    @Command
    @UtilityClass
    public class category {
        @Command
        public Component list(BanMod banMod) {
            // todo: use book adapter
            var text = text("Available Punishment categories:");
            for (var category : banMod.getEntityService().getCategories().toList())
                text = text.append(text("\n"))
                        .append(text(category.getName()).color(AQUA))
                        .append(text(" punishes with "))
                        .append(text(category.getPunishment().getName()).color(RED))
                        .append(text("\n- Base Duration: "))
                        .append(text(category.getBaseDuration().toString()).color(YELLOW))
                        .append(text("\n- Exponent Base: "))
                        .append(text(category.getRepetitionExpBase()).color(YELLOW));
            return text;
        }

        @Command
        @Alias("update")
        public Component create(BanMod banMod, @Arg String name, @Arg String baseDuration, @Nullable @Arg Double repetitionBase) {
            var duration = parseDuration(baseDuration);
            if (repetitionBase != null)
                repetitionBase = Math.max(2, repetitionBase);
            else repetitionBase = 2d;
            var update = new boolean[]{false};
            var category = banMod.getEntityService().findCategory(name)
                    .map(it -> {
                        update[0] = true;
                        return it.toBuilder();
                    })
                    .orElseGet(PunishmentCategory::builder)
                    .name(name)
                    .baseDuration(duration)
                    .repetitionExpBase(repetitionBase)
                    .build();
            banMod.getEntityService().save(category);
            return text("Category ")
                    .append(text(name).color(AQUA))
                    .append(text(" was "))
                    .append(text(update[0] ? "updated" : "created")
                            .color(update[0] ? GREEN : DARK_GREEN));
        }

        @Command
        public Component delete(BanMod banMod, @Arg String name) {
            return banMod.getEntityService().deleteCategory(name)
                    ? text("Deleted category " + name).color(RED)
                    : text("Could not delete category " + name).color(DARK_RED);
        }
    }

    @UtilityClass
    @Command("import")
    public class Import {
        @Command
        public Component vanilla(BanMod banMod) {
            try (var importer = new VanillaBansImporter(banMod)) {
                var result = importer.run();
                return text("Imported ")
                        .append(text(result.banCount() + " Bans").color(RED))
                        .append(text(" from Vanilla Minecraft"));
            } catch (Throwable t) {
                throw new Command.Error("Could not import from Vanilla Minecraft: " + t);
            }
        }

        @Command
        public Component litebans(BanMod banMod) {
            try (var importer = new LiteBansImporter(banMod, banMod.getDatabaseInfo())) {
                var result = importer.run();
                return text("Imported ")
                        .append(text(result.muteCount() + " Mutes").color(YELLOW))
                        .append(text(" and "))
                        .append(text(result.banCount() + " Bans").color(RED))
                        .append(text(" from LiteBans"));
            } catch (Throwable t) {
                throw new Command.Error("Could not import from LiteBans: " + t);
            }
        }
    }
}
