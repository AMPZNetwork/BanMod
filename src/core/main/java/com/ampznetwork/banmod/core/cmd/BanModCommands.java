package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.importer.litebans.LiteBansImporter;
import com.ampznetwork.banmod.core.importer.vanilla.VanillaBansImporter;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.comroid.annotations.Alias;
import org.comroid.annotations.Default;
import org.comroid.api.func.util.Command;
import org.comroid.api.text.StringMode;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;

import static java.time.Instant.now;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.openUrl;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.UNDERLINED;
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
                    throw BanMod.Resources.couldNotSaveError();
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
    public Component lookup(BanMod banMod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name) {
        // todo: use book adapter here
        var target = banMod.getPlayerAdapter().getId(name);
        var data = banMod.getEntityService().getPlayerData(target)
                .orElseThrow(() -> new Command.Error("Player not found"));
        var text = text("")
                .append(text("Player ").decorate(UNDERLINED))
                .append(text(name).color(AQUA).decorate(UNDERLINED))
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
        var knownIPs = data.getKnownIPs();
        if (knownIPs.isEmpty())
            text = text.append(text("\n- ")
                            .append(text("(none)").color(GRAY)))
                    .append(text("\n"));
        else for (var knownIp : knownIPs.entrySet())
            text = text.append(text("\n- "))
                    .append(text(knownIp.getKey())
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
                    .append(BanMod.Displays.textPunishment(infraction.getCategory().getPunishment()))
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
                            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                            @NotNull @Arg(value = "category", autoFillProvider = AutoFillProvider.Categories.class) String category,
                            @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        var cat = banMod.getEntityService().findCategory(category)
                .orElseThrow(() -> new Command.Error("Unknown category: " + category));
        var infraction = BanMod.Resources.standardInfraction(banMod, cat, tgt, issuer, reason)
                .build();

        // save infraction
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();

        // apply infraction
        var result = infraction.toResult();
        var punishment = infraction.getCategory().getPunishment();
        if (punishment != Punishment.Mute)
            banMod.getPlayerAdapter().kick(tgt, switch (punishment) {
                //case Mute -> ;
                case Kick -> BanMod.Displays.kickedTextUser(result);
                case Ban -> BanMod.Displays.bannedTextUser(banMod, result);
                default -> throw new IllegalStateException("Unexpected value: " + punishment);
            });
        else banMod.getPlayerAdapter().send(tgt, BanMod.Displays.mutedTextUser(result));

        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component mutelist(BanMod banMod, @Nullable @Default("1") @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page) {
        return BanMod.Displays.infractionList(banMod, page == null ? 1 : page, Punishment.Mute);
    }

    @Command
    public Component tempmute(BanMod banMod,
                              UUID issuer,
                              @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                              @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
                              @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = BanMod.Resources.standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component mute(BanMod banMod,
                          UUID issuer,
                          @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                          @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isMuted())
            return text("User " + name + " is already muted").color(YELLOW);
        var infraction = BanMod.Resources.standardInfraction(banMod, banMod.getMuteCategory(), tgt, issuer, reason).expires(null).build();
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component unmute(BanMod banMod,
                            UUID issuer,
                            @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayersByInfractionPunishment.class) String name) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Mute)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not muted"));
        infraction.setRevoker(issuer);
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        return text("User " + name + " was unmuted").color(GREEN);
    }

    @Command
    public Component kick(BanMod banMod,
                          UUID issuer,
                          @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                          @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = BanMod.Resources.standardInfraction(banMod, banMod.getKickCategory(), tgt, issuer, reason)
                .expires(null)
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        var text = BanMod.Displays.kickedTextUser(infraction.toResult());
        banMod.getPlayerAdapter().kick(tgt, text);
        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component banlist(BanMod banMod, @Nullable @Default("1") @Arg(value = "page", required = false, autoFillProvider = AutoFillProvider.PageNumber.class) Integer page) {
        return BanMod.Displays.infractionList(banMod, page == null ? 1 : page, Punishment.Ban);
    }

    @Command
    public Component tempban(BanMod banMod,
                             UUID issuer,
                             @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                             @NotNull @Arg(value = "duration", autoFillProvider = Command.AutoFillProvider.Duration.class) String durationText,
                             @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var now = now();
        var duration = parseDuration(durationText);
        var infraction = BanMod.Resources.standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason)
                .timestamp(now)
                .expires(now.plus(duration))
                .build();
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        banMod.getPlayerAdapter().kick(tgt, BanMod.Displays.bannedTextUser(banMod, infraction.toResult()));
        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component ban(BanMod banMod,
                         UUID issuer,
                         @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Players.class) String name,
                         @Nullable @Default("") @Arg(value = "reason", required = false, stringMode = StringMode.GREEDY) String reason) {
        if (reason == null || reason.isBlank())
            reason = null;
        var tgt = banMod.getPlayerAdapter().getId(name);
        if (banMod.getEntityService().queuePlayer(tgt).isBanned())
            return text("User " + name + " is already banned").color(YELLOW);
        var infraction = BanMod.Resources.standardInfraction(banMod, banMod.getBanCategory(), tgt, issuer, reason).expires(null).build();
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        banMod.getPlayerAdapter().kick(tgt, BanMod.Displays.bannedTextUser(banMod, infraction.toResult()));
        return BanMod.Displays.textPunishmentFull(banMod, infraction);
    }

    @Command
    public Component unban(BanMod banMod,
                           UUID issuer,
                           @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.PlayersByInfractionPunishment.class) String name) {
        var tgt = banMod.getPlayerAdapter().getId(name);
        var infraction = banMod.getEntityService().getInfractions(tgt)
                .filter(i -> i.getRevoker() == null && (i.getExpires() == null || i.getExpires().isAfter(now())))
                .filter(i -> i.getCategory().getPunishment() == Punishment.Ban)
                .findAny()
                .orElseThrow(() -> new Command.Error("User is not banned"));
        infraction.setRevoker(issuer);
        if (!banMod.getEntityService().save(infraction))
            throw BanMod.Resources.couldNotSaveError();
        return text("User " + name + " was unbanned").color(GREEN);
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
                                @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Categories.class) String name,
                                @NotNull @Arg(value = "baseDuration", autoFillProvider = Command.AutoFillProvider.Duration.class) String baseDuration,
                                @Nullable @Default("2") @Arg(value = "repetitionBase", required = false) Double repetitionBase) {
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
                throw BanMod.Resources.couldNotSaveError();
            return text("Category ")
                    .append(text(name).color(AQUA))
                    .append(text(" was "))
                    .append(text(update[0] ? "updated" : "created")
                            .color(update[0] ? GREEN : DARK_GREEN));
        }

        @Command
        public Component delete(BanMod banMod, @NotNull @Arg(value = "name", autoFillProvider = AutoFillProvider.Categories.class) String name) {
            var service = banMod.getEntityService();
            var cat = service.findCategory(name);
            return service.delete(cat) > 0
                    ? text("Deleted category " + name).color(RED)
                    : text("Could not delete category " + name).color(DARK_RED);
        }
    }

    @UtilityClass
    @Command(value = "import", permission = "4")
    public class Import {
        @Command
        public Component vanilla(BanMod banMod) {
            try (var importer = new VanillaBansImporter(banMod)) {
                var result = importer.run();
                return text("Imported ")
                        .append(text(result.banCount() + " Bans").color(RED))
                        .append(text(" from Vanilla Minecraft"));
            } catch (Throwable t) {
                throw new Command.Error("Could not import from Vanilla Minecraft. " + BanMod.Strings.PleaseCheckConsole);
            }
        }

        @Command
        public Component litebans(BanMod mod) {
            try (var importer = new LiteBansImporter(mod, mod.getDatabaseInfo())) {
                var result = importer.run();
                return text("Imported ")
                        .append(text(result.muteCount() + " Mutes").color(YELLOW))
                        .append(text(" and "))
                        .append(text(result.banCount() + " Bans").color(RED))
                        .append(text(" from LiteBans"));
            } catch (SchemaManagementException smex) {
                var str = "LiteBans Databases have an unexpected format";
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, str, smex);
                return text(str).color(YELLOW);
            } catch (Throwable t) {
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, "Could not import from LiteBans", t);
                throw new Command.Error("Could not import from LiteBans." + BanMod.Strings.PleaseCheckConsole);
            }
        }
    }
}
