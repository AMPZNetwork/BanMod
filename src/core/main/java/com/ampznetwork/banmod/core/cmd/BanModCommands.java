package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.importer.litebans.LiteBansImporter;
import com.ampznetwork.banmod.core.importer.vanilla.VanillaBansImporter;
import com.ampznetwork.banmod.generated.PluginYml.Permission.banmod;
import com.ampznetwork.libmod.api.entity.DbObject;
import com.ampznetwork.libmod.api.entity.Player;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.comroid.annotations.Alias;
import org.comroid.annotations.Default;
import org.comroid.api.attr.Named;
import org.comroid.api.func.util.Bitmask;
import org.comroid.api.func.util.Command;
import org.comroid.api.text.StringMode;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ampznetwork.banmod.api.model.StandardInfractionFactory.*;
import static java.time.Instant.*;
import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.event.ClickEvent.*;
import static net.kyori.adventure.text.event.HoverEvent.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static org.comroid.api.Polyfill.*;
import static org.comroid.api.func.util.Command.*;

@UtilityClass
public class BanModCommands {
    @Command(permission = banmod.CLEANUP)
    public static Component cleanup(BanMod mod, UUID playerId, @NotNull @Arg(value = "method") CleanupMethod method) {
        mod.getLib().getPlayerAdapter().send(playerId, text("Starting cleanup process..."));
        final var service = mod.getEntityService();
        var       text    = text();
        var       c       = 0;
        DbObject[] buffer;
        switch (method) {
            case everything:
            case infractions:
                var infractions = service.getAccessor(Infraction.TYPE).all().toList();

                // remove expired infractions
                buffer = infractions.stream()
                        .filter(Infraction.IS_IN_EFFECT.negate())
                        .toArray(DbObject[]::new);
                c = service.delete(buffer);
                text.append(text("\nRemoved ")
                        .append(text(c).color(GREEN))
                        .append(text(" expired infractions")));
                if (c < buffer.length)
                    text.append(text("\nWarning: Not all expired elements could be deleted").color(YELLOW));

                // remove duplicate infractions
                var playerIds = new HashSet<UUID>();

                buffer = infractions.stream()
                        .sorted(Infraction.BY_NEWEST)
                        .filter(infr -> !playerIds.add(infr.getPlayer().getId()))
                        .toArray(DbObject[]::new);
                c = service.delete(buffer);
                text.append(text("\nRemoved ")
                        .append(text(c).color(GREEN))
                        .append(text(" duplicate infractions")));
                if (c < buffer.length)
                    text.append(text("\nWarning: Not all duplicate elements could be deleted").color(YELLOW));

                if (method != CleanupMethod.everything)
                    break;
            case players:
                var c0 = new int[]{ 0, 0 };
                buffer = service.getAccessor(Player.TYPE).all()
                        .filter(data -> {
                            var name = data.getKnownNames().size() > 1;
                            var ip = data.getKnownIPs().size() > 1;
                            if (name) c0[0] += 1;
                            if (ip) c0[1] += 1;
                            return name || ip;
                        })
                        .peek(data -> {
                            var name = data.getKnownNames().entrySet().stream()
                                    .min(Player.MOST_RECENTLY_SEEN)
                                    .map(Map.Entry::getKey)
                                    .orElseThrow();
                            var ip = data.getKnownIPs().entrySet().stream()
                                    .min(Player.MOST_RECENTLY_SEEN)
                                    .map(Map.Entry::getKey)
                                    .orElseThrow();
                            data.setKnownNames(new HashMap<>() {{
                                put(name, now());
                            }});
                            data.setKnownIPs(new HashMap<>() {{
                                put(ip, now());
                            }});
                        })
                        .peek(service::save)
                        .toArray(DbObject[]::new);

                text.append(text("\nCleaned up ")
                        .append(text(buffer.length).color(GREEN))
                        .append(text(" player data entries ("))
                        .append(text(c0[0]).color(AQUA))
                        .append(text(" Names; "))
                        .append(text(c0[1]).color(AQUA))
                        .append(text(" IPs)")));
                break;
            default:
                throw new Command.Error("Unexpected value: " + method);
        }
        return text.build();
    }

    @Command(permission = banmod.LOOKUP)
    public static Component lookup(BanMod mod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name) {
        // todo: use book adapter here
        var target = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        var data = mod.getEntityService().getAccessor(Player.TYPE).get(target)
                .orElseThrow(() -> new Command.Error("Player not found"));
        var text = text("")
                .append(text("Player ").decorate(BOLD))
                .append(text(name).color(AQUA).decorate(BOLD))
                .append(text("\n"))
                .append(text("ID: "))
                .append(text(target.toString())
                        .clickEvent(openUrl("https://namemc.com/profile/" + target))
                        .hoverEvent(showText(text("Open on NameMC.com")))
                        .color(YELLOW))
                .append(text("\n"))
                .append(text("Known Names:"));
        for (var knownName : data.getKnownNames().entrySet())
            text = text.append(text("\n- "))
                    .append(text(knownName.getKey())
                            .hoverEvent(showText(text("Last seen: " + BanMod.Displays.formatTimestamp(knownName.getValue()))))
                            .color(YELLOW));
        text = text.append(text("\nKnown IPs:"));
        var knownIPs = data.getKnownIPs();
        if (knownIPs.isEmpty())
            text = text.append(text("\n- ")
                            .append(text("(none)").color(GRAY)))
                    .append(text("\n"));
        else for (var knownIp : knownIPs.entrySet())
            text = text.append(text("\n- "))
                    .append(text(knownIp.getKey())
                            .hoverEvent(showText(text("Last seen: " + BanMod.Displays.formatTimestamp(knownIp.getValue()))))
                            .color(YELLOW));
        text = text.append(text("\nActive Infractions:"));
        var infractions = mod.getEntityService().getAccessor(Infraction.TYPE).all()
                .filter(Infraction.IS_IN_EFFECT)
                .toList();
        if (infractions.isEmpty())
            text = text.append(text("\n- (none)").color(GRAY));
        else for (var infraction : infractions) {
            text = text.append(text("\n- "))
                    .append(infraction.getPunishment().toComponent(true))
                    .append(text(" by "))
                    .append(text(Optional.ofNullable(infraction.getIssuer())
                            .map(Player::getName)
                            .orElse("Server"))
                            .color(AQUA));
            var expires = infraction.getExpires();
            if (expires != null)
                text = text.append(text(" until "))
                        .append(text(expires.toString()).color(YELLOW));
        }
        return text;
    }

    @Command(permission = banmod.PUNISH)
    public static Component punish(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @NotNull @Arg(value = "category", autoFillProvider = AutoFillProvider.Categories.class) String category,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        var cat = mod.getEntityService().getAccessor(PunishmentCategory.TYPE).by(PunishmentCategory::getName).get(category)
                .orElseThrow(() -> new Command.Error("Unknown category: " + category));
        final @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE)
                .create()
                .complete(e -> base(mod, tgt, cat, issuer)
                        .reason(finalReason)
                        .build()
                        .accept(e));

        // apply infraction
        mod.realize(infraction);

        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.MUTELIST)
    public static Component mutelist(
            BanMod mod, @Nullable @Default("1") @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page
    ) {
        return BanMod.Displays.infractionList(mod, page == null ? 1 : page, Punishment.Mute);
    }

    @Command(permission = banmod.TEMPMUTE)
    public static Component tempmute(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        var playerResult = mod.queuePlayer(tgt);
        if (playerResult.isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE)
                .create()
                .complete(e -> base(mod, tgt, Punishment.Mute, issuer)
                        .duration(parseDuration(durationText))
                        .reason(finalReason)
                        .build()
                        .accept(e));
        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.MUTE)
    public static Component mute(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        if (mod.queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        final @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).create()
                .complete(e -> base(mod, tgt, Punishment.Mute, issuer)
                        .permanent(true)
                        .reason(finalReason)
                        .build()
                        .accept(e));
        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.UNMUTE)
    public static Component unmute(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayersByInfractionPunishment.class) String name
    ) {
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).all()
                .filter(Infraction.IS_IN_EFFECT)
                .filter(i -> i.getPlayer().getId().equals(tgt))
                .filter(i -> i.getPunishment() == Punishment.Mute)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not muted"));
        if (infraction.getPlayer().getId().equals(issuer))
            throw new Command.Error("You cannot unmute yourself!");
        mod.revokeInfraction(infraction.getId(), issuer);
        return text("User " + name + " was unmuted").color(GREEN);
    }

    @Command(permission = banmod.KICK)
    public static Component kick(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        final @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).create()
                .complete(e -> base(mod, tgt, Punishment.Kick, issuer)
                        .reason(finalReason)
                        .build()
                        .accept(e));

        mod.realize(infraction);
        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.BANLIST)
    public static Component banlist(
            BanMod mod, @Nullable @Default("1") @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page
    ) {
        return BanMod.Displays.infractionList(mod, page == null ? 1 : page, Punishment.Ban);
    }

    @Command(permission = banmod.TEMPBAN)
    public static Component tempban(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        if (mod.queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        final @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).create()
                .complete(e -> base(mod, tgt, Punishment.Ban, issuer)
                        .duration(parseDuration(durationText))
                        .reason(finalReason)
                        .build()
                        .accept(e));
        mod.realize(infraction);
        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.BAN)
    public static Component ban(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason
    ) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        if (mod.queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        final @Nullable String finalReason = reason;
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).create()
                .complete(e -> base(mod, tgt, Punishment.Ban, issuer)
                        .permanent(true)
                        .reason(finalReason)
                        .build()
                        .accept(e));
        mod.realize(infraction);
        return BanMod.Displays.textPunishmentFull(mod, infraction);
    }

    @Command(permission = banmod.UNBAN)
    public static Component unban(
            BanMod mod,
            UUID issuer,
            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayersByInfractionPunishment.class) String name
    ) {
        var tgt = mod.getLib().getPlayerAdapter().getIdOrThrow(name);
        var infraction = mod.getEntityService().getAccessor(Infraction.TYPE).all()
                .filter(Infraction.IS_IN_EFFECT)
                .filter(i -> i.getPlayer().getId().equals(tgt))
                .filter(i -> i.getPunishment() == Punishment.Ban)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not banned"));
        if (infraction.getPlayer().getId() == issuer)
            throw new Command.Error("You cannot unban yourself!");
        mod.revokeInfraction(infraction.getId(), issuer);
        return text("User " + name + " was unbanned").color(GREEN);
    }

    public enum CleanupMethod implements Named, Bitmask.Attribute<CleanupMethod> {
        infractions, players, everything
    }

    @Command(permission = banmod.INFRACTION)
    @UtilityClass
    @Alias("punishment")
    public class infraction {
        @Command
        public Component list(
                BanMod mod,
                @Nullable @Arg(value = "page", autoFillProvider = AutoFillProvider.PageNumber.class, required = false) Integer page,
                @Nullable @Arg(value = "query", autoFillProvider = AutoFillProvider.InfractionQuery.class, required = false, stringMode = StringMode.GREEDY)
                String query
        ) {
            if (query != null)
                throw new Command.Error("query unimplemented");
            return BanMod.Displays.infractionList(mod, page == null ? 1 : page, Punishment.Ban);
        }

        @Command
        public Component set(
                BanMod mod,
                @NotNull @Arg(value = "query", autoFillProvider = AutoFillProvider.InfractionQuery.class) String query,
                @NotNull @Arg(value = "property", autoFillProvider = AutoFillProvider.ObjectProperties.class) String propertyName,
                @Nullable @Arg(value = "value", autoFillProvider = AutoFillProvider.ObjectPropertyValues.class) String value
        ) {
            throw new Command.Error("unimplemented");
        }
    }

    @Command(permission = banmod.CATEGORY)
    @UtilityClass
    public class category {
        @Command
        public Component duration(
                BanMod mod,
                @NotNull @Arg(value = "category", autoFillProvider = AutoFillProvider.Categories.class) String categoryName,
                @NotNull @Arg(value = "player", autoFillProvider = AutoFillProvider.Players.class) String playerName,
                @Nullable @Arg(value = "repetition", required = false) Integer repetition
        ) {
            throw new Command.Error("unimplemented");
        }

        @Command
        public Component list(BanMod mod) {
            // todo: use book adapter
            var text = text().append(text("Available Punishment categories:").decorate(BOLD));
            for (var category : mod.getEntityService().getAccessor(PunishmentCategory.TYPE).all().toList()) {
                text.append(text("\n"))
                        .append(text(category.getName())
                                .color(AQUA).decorate(UNDERLINED))
                        .append(text(" punishes with: "));
                var thresholds = category.getPunishmentThresholds()
                        .entrySet().stream()
                        .sorted(Punishment.BY_SEVERITY)
                        .toList();
                var fltpd = thresholds.stream()
                                    .filter(e -> !e.getValue().isInherentlyTemporary())
                                    .mapToInt(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(0) - 1;
                for (int i = 0; i < thresholds.size(); i++) {
                    var e          = thresholds.get(i);
                    var punishment = e.getValue();
                    int startsAtRepetition = e.getKey();
                    text.append(text("\n-> From the "))
                            .append(text(startsAtRepetition == 0 ? "first"
                                                                 : ordinal(startsAtRepetition + 1)).color(AQUA))
                            .append(text(" time: "))
                            .append(punishment.toComponent(false));
                    if (!punishment.isInherentlyTemporary()) {
                        if (i + 1 >= thresholds.size())
                            text.append(text(" (at least "))
                                    .append(text(BanMod.Displays.formatDuration(category
                                            .calculateDuration(startsAtRepetition - fltpd - 1)))
                                            .color(YELLOW))
                                    .append(text(")"));
                        else {
                            text.append(text(" ("));
                            var nextThreshold = thresholds.get(i + 1).getKey() - 1;
                            for (var n = fltpd; n < nextThreshold; n++) {
                                var repetition = n - fltpd;
                                text.append(text(BanMod.Displays.formatDuration(category
                                        .calculateDuration(repetition)))
                                        .color(YELLOW));
                                if (n >= 5 && i + 1 >= thresholds.size()) {
                                    text.append(text("..."));
                                    break;
                                } else if (n + 1 < nextThreshold)
                                    text.append(text("; "));
                            }
                            text.append(text(")"));
                        }
                    }
                }
                text.append(text("\n- Base Duration: "))
                        .append(text(BanMod.Displays.formatDuration(category.getBaseDuration())).color(YELLOW))
                        .append(text("\n- Exponent Base: "))
                        .append(text(category.getRepetitionExpBase()).color(YELLOW));
            }
            return text.build();
        }

        @Command
        @Alias("update")
        public Component create(
                BanMod mod,
                @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Categories.class) String name,
                @NotNull @Arg(value = "baseDuration", autoFillProvider = Command.AutoFillProvider.Duration.class) String baseDuration,
                @Nullable @Default("2") @Arg(value = "repetitionBase", required = false) Double repetitionBase
        ) {
            var duration = parseDuration(baseDuration);
            if (repetitionBase != null)
                repetitionBase = Math.max(2, repetitionBase);
            else repetitionBase = 2d;
            var                    update              = new boolean[]{ false };
            final @Nullable Double finalRepetitionBase = repetitionBase;
            var category = mod.getEntityService()
                    .getAccessor(PunishmentCategory.TYPE)
                    .by(PunishmentCategory::getName)
                    .getOrCreate(name)
                    .setUpdateOriginal(cat -> cat.setName(name).setBaseDuration(duration).setRepetitionExpBase(finalRepetitionBase))
                    .complete(build -> build.name(name).baseDuration(duration).repetitionExpBase(finalRepetitionBase));
            mod.getEntityService().save(category);
            return text("Category ")
                    .append(text(name).color(AQUA))
                    .append(text(" was "))
                    .append(text(update[0] ? "updated" : "created")
                            .color(update[0] ? GREEN : DARK_GREEN));
        }

        @Command
        public Component set(
                BanMod mod,
                @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Categories.class) String categoryName,
                @NotNull @Arg(value = "property", autoFillProvider = AutoFillProvider.ObjectProperties.class) String propertyName,
                @Nullable @Arg(value = "value", autoFillProvider = AutoFillProvider.ObjectPropertyValues.class) String value
        ) {
            throw new Command.Error("unimplemented");
        }

        @Command
        public Component delete(BanMod mod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Categories.class) String name) {
            if ("default".equals(name))
                throw new Command.Error("Cannot delete the default category!");
            var service = mod.getEntityService();
            var cat = service.getAccessor(PunishmentCategory.TYPE).by(PunishmentCategory::getName).get(name)
                    .orElseThrow(() -> new Command.Error("Could not find category named " + name));
            return service.delete(cat) > 0
                   ? text("Deleted category " + name).color(RED)
                   : text("Could not delete category " + name).color(DARK_RED);
        }
    }

    @UtilityClass
    @Command(value = "import", permission = "4")
    public class Import {
        @Command
        public Component vanilla(BanMod mod, UUID playerId, @Default("false") @Arg(value = "cleanup", required = false) boolean cleanup) {
            mod.getLib().getPlayerAdapter().send(playerId, text("Starting import process..."));
            try (var importer = new VanillaBansImporter(mod)) {
                var result = importer.run();
                var text = text("Imported ")
                        .append(text(result.banCount() + " Bans").color(RED))
                        .append(text(" from Vanilla Minecraft"));
                if (cleanup) text = text
                        .append(text("\n"))
                        .append(cleanup(mod, playerId, CleanupMethod.everything));
                return text;
            } catch (Throwable t) {
                var msg = "Could not import bans from Vanilla Minecraft";
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, msg, t);
                throw new Command.Error(msg + " " + BanMod.Strings.PleaseCheckConsole);
            }
        }

        @Command
        public Component litebans(BanMod mod, UUID playerId, @Default("false") @Arg(value = "cleanup", required = false) boolean cleanup) {
            mod.getLib().getPlayerAdapter().send(playerId, text("Starting import process..."));
            try (var importer = new LiteBansImporter(mod, mod.getLib().getDatabaseInfo())) {
                var result = importer.run();
                var text = text("Imported ")
                        .append(text(result.muteCount() + " Mutes").color(Punishment.Mute.getColor()))
                        .append(text(", "))
                        .append(text(result.banCount() + " Bans").color(Punishment.Ban.getColor()))
                        .append(text(" and "))
                        .append(text(result.playerDataCount() + " Player Entries ").color(AQUA))
                        .append(text(" from LiteBans"));
                if (cleanup) text = text.append(cleanup(mod, playerId, CleanupMethod.everything));
                return text;
            } catch (SchemaManagementException smex) {
                var msg = "LiteBans Databases have an unexpected format. " + BanMod.Strings.PleaseCheckConsole;
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, msg, smex);
                return text(msg).color(YELLOW);
            } catch (Throwable t) {
                var msg = "Could not import from LiteBans.";
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, msg, t);
                throw new Command.Error(msg + " " + BanMod.Strings.PleaseCheckConsole);
            }
        }
    }
}
