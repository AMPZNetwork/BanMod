package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.importer.litebans.LiteBansImporter;
import com.ampznetwork.banmod.core.importer.vanilla.VanillaBansImporter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import org.comroid.annotations.Alias;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Streams;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
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

@Slf4j
@UtilityClass
public class BanModCommands {
    public static int ENTRIES_PER_PAGE = 8;

    @Command
    public Component reload(BanMod banMod) {
        banMod.reload();
        return text("Configuration reloaded!")
                .color(GREEN);
    }

    @Command
    public Component cleanup(BanMod banMod, @NotNull @Arg(value = "method", autoFill = {"infractions", "playerdata", "*"}) String method) {
        final var service = banMod.getEntityService();
        var text = text();
        int c;
        switch (method) {
            case "*":
            case "infractions":
                c = service.delete(service.getInfractions()
                        .filter(Infraction.IS_IN_EFFECT.negate())
                        .toArray());
                text.append(text("Removed ")
                        .append(text(c).color(GREEN))
                        .append(text(" expired infractions")));
                if (!"*".equals(method))
                    break;
                else text.append(text("\n"));
            case "playerdata":
                var affected = service.getPlayerData()
                        .filter(data -> data.getKnownNames().size() > 1 || data.getKnownIPs().size() > 1)
                        .peek(data -> {
                            var knownName = data.getLastKnownName();
                            var knownIp = data.getLastKnownIp();
                            var now = now();
                            data.setKnownNames(new HashMap<>() {{
                                put(knownName, now);
                            }});
                            data.setKnownIPs(new HashMap<>() {{
                                put(knownIp, now);
                            }});
                        })
                        .toArray();
                if (!service.save(affected))
                    throw couldNotSaveError();
                text.append(text("Cleaned up ")
                        .append(text(affected.length).color(GREEN))
                        .append(text(" users")));
                break;
            default:
                throw new Command.Error("Unexpected value: " + method);
        }
        return text.build();
    }

    @Command
    public Component lookup(BanMod banMod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name) {
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
        for (var knownName : data.getKnownNames().entrySet())
            text = text.append(text("\n- "))
                    .append(text(knownName.getKey())
                            .hoverEvent(showText(text("Last seen: " + knownName.getValue())))
                            .color(YELLOW))
                    .append(text("\n"));
        text = text.append(text("Known IPs:"));
        for (var knownIp : data.getKnownIPs().entrySet())
            text = text.append(text("\n- "))
                    .append(text(knownIp.toString())
                            .hoverEvent(showText(text("Last seen: " + knownIp.getValue())))
                            .color(YELLOW))
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
    public Component punish(BanMod banMod,
                            UUID issuer,
                            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                            @NotNull @Arg(value = "category", autoFillProvider = AutoFillProvider.Categories.class) String category,
                            @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        var cat = banMod.getEntityService().findCategory(category)
                .orElseThrow(() -> new Command.Error("Unknown category: " + category));
        var infraction = standardInfraction(banMod, cat, tgt, issuer, reason)
                .build();

        // save infraction
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();

        // apply infraction
        var punishment = infraction.getCategory().getPunishment();
        if (punishment != Punishment.Mute)
            banMod.getPlayerAdapter().kick(tgt, infraction.getReason());

        return textPunishmentFull(name, punishment, reason);
    }

    @Command
    public Component mutelist(BanMod banMod, @Nullable @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page) {
        return infractionList(banMod, page == null ? 1 : page, Punishment.Mute);
    }

    @Command
    public Component tempmute(BanMod banMod,
                              UUID issuer,
                              @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                              @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
                              @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        return textPunishmentFull(name, Punishment.Mute, infraction.getReason());
    }

    @Command
    public Component mute(BanMod banMod,
                          UUID issuer,
                          @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                          @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var infraction = standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason).expires(null).build();
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        return textPunishmentFull(name, Punishment.Mute, infraction.getReason());
    }

    @Command
    public Component unmute(BanMod banMod,
                            UUID issuer,
                            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                            @Nullable String[] args) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Mute)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not muted"));
        infraction.setRevoker(issuer);
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        return text("User " + name + " was unmuted").color(GREEN);
    }

    @Command
    public Component kick(BanMod banMod,
                          UUID issuer,
                          @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                          @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = standardInfraction(banMod, banMod.getKickCategory(), tgt, issuer, reason)
                .expires(null)
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Kick, infraction.getReason());
    }

    @Command
    public Component banlist(BanMod banMod, @Nullable @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page) {
        return infractionList(banMod, page == null ? 1 : page, Punishment.Ban);
    }

    @Command
    public Component tempban(BanMod banMod,
                             UUID issuer,
                             @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                             @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
                             @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Ban, infraction.getReason());
    }

    @Command
    public Component ban(BanMod banMod,
                         UUID issuer,
                         @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                         @Nullable String[] args) {
        var reason = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var infraction = standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason).expires(null).build();
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        banMod.getPlayerAdapter().kick(tgt, infraction.getReason());
        return textPunishmentFull(name, Punishment.Ban, infraction.getReason());
    }

    @Command
    public Component unban(BanMod banMod,
                           UUID issuer,
                           @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                           @Nullable String[] args) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Ban)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not banned"));
        infraction.setRevoker(issuer);
        if (!banMod.getEntityService().save(infraction))
            throw couldNotSaveError();
        return text("User " + name + " was unbanned").color(GREEN);
    }

    private Component infractionList(BanMod banMod, int page, Punishment punishment) {
        final var infractions = banMod.getEntityService().getInfractions()
                .filter(Infraction.IS_IN_EFFECT)
                .filter(i -> i.getCategory().getPunishment() == punishment)
                .toList();
        final var pageCount = Math.ceil(1d * infractions.size() / ENTRIES_PER_PAGE);
        // todo: use book adapter here
        return infractions.stream()
                .skip((page - 1L) * ENTRIES_PER_PAGE)
                .limit(ENTRIES_PER_PAGE)
                .map(i -> text("\n- ")
                        .append(textPunishmentFull(banMod.getPlayerAdapter().getName(i.getPlayerId()),
                                i.getCategory().getPunishment(),
                                i.getReason())))
                .collect(Streams.atLeastOneOrElseGet(() -> text("\n- ")
                        .append(text("(none)").color(GRAY))))
                .collect(Collector.of(() -> text()
                                .append(text(punishment.name() + "list (Page %d of %d)".formatted(page, (int) pageCount))),
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

    private static Command.@NotNull Error couldNotSaveError() {
        return new Command.Error("Could not save changes");
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
        public Component create(BanMod banMod,
                                @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name,
                                @NotNull @Arg(value = "baseDuration", autoFillProvider = Command.AutoFillProvider.Duration.class) String baseDuration,
                                @Nullable @Arg(value = "repetitionBase", required = false) Double repetitionBase) {
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
            if (!banMod.getEntityService().save(category))
                throw couldNotSaveError();
            return text("Category ")
                    .append(text(name).color(AQUA))
                    .append(text(" was "))
                    .append(text(update[0] ? "updated" : "created")
                            .color(update[0] ? GREEN : DARK_GREEN));
        }

        @Command
        public Component delete(BanMod banMod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayerNames.class) String name) {
            var service = banMod.getEntityService();
            var cat = service.findCategory(name);
            return service.delete(cat) > 0
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
            } catch (SchemaManagementException smex) {
                var str = "LiteBans Databases were in an incorrect format.";
                log.warn("{} Please report this at " + BanMod.IssuesUrl, str, smex);
                return text(str).color(YELLOW);
            } catch (Throwable t) {
                log.error("Could not import from LiteBans. Please report this at " + BanMod.IssuesUrl, t);
                throw new Command.Error("Could not import from LiteBans. Please check console for further information.");
            }
        }
    }
}
