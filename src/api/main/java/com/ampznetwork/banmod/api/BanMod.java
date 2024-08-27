package com.ampznetwork.banmod.api;

import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.libmod.api.LibMod;
import com.ampznetwork.libmod.api.SubMod;
import com.ampznetwork.libmod.api.entity.DbObject;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collector;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.event.ClickEvent.*;
import static net.kyori.adventure.text.event.HoverEvent.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public interface BanMod extends SubMod, Command.PermissionChecker.Adapter {
    LibMod getLib();

    @Override
    default Class<?> getModuleType() {
        return BanMod.class;
    }

    @Override
    default Set<Class<? extends DbObject>> getEntityTypes() {
        return Set.of(Infraction.class, PlayerData.class, PunishmentCategory.class);
    }

    default @NotNull PunishmentCategory getDefaultCategory() {
        return getEntityService().getAccessor(PunishmentCategory.TYPE)
                .by(PunishmentCategory::getName)
                .getOrCreate("default")
                .setUpdateOriginal(original -> {
                    original.getPunishmentThresholds().putAll(Map.of(
                            0, Punishment.Kick,
                            2, Punishment.Mute,
                            5, Punishment.Ban
                    ));
                    return original;
                })
                .complete(cat -> cat.punishmentThreshold(0, Punishment.Kick)
                        .punishmentThreshold(2, Punishment.Mute)
                        .punishmentThreshold(5, Punishment.Ban));
    }

    @Nullable
    String getBanAppealUrl();

    Logger log();

    boolean allowUnsafeConnections();

    default void realize(Infraction infraction) {
        var punish = infraction.getPunishment();
        if (punish.isPassive() || (!punish.isInherentlyTemporary() && !Infraction.IS_IN_EFFECT.test(infraction)))
            return;
        Resources.notify(this, infraction.getPlayer().getId(), punish, infraction.toResult(), switch (punish) {
            case Kick, Ban -> (BiConsumer<UUID, Component>) getLib().getPlayerAdapter()::kick;
            case Debuff -> (BiConsumer<UUID, Component>) getLib().getPlayerAdapter()::send; // todo
            default -> (BiConsumer<UUID, Component>) getLib().getPlayerAdapter()::send;
        });
    }

    default PlayerResult queuePlayer(UUID playerId) {
        return getEntityService().getAccessor(Infraction.TYPE)
                .querySelect("select i.* from banmod_infractions i where i.player_id = :playerId",
                        Map.of("playerId", playerId))
                .filter(Infraction.IS_IN_EFFECT)
                .sorted(Infraction.BY_SEVERITY)
                .map(i -> new PlayerResult(playerId,
                        i.getPunishment() == Punishment.Mute,
                        i.getPunishment() == Punishment.Ban,
                        i.getReason(),
                        i.getTimestamp(),
                        i.getExpires(),
                        i.getIssuer()))
                .findFirst()
                .orElseGet(() -> {
                    var now = Instant.now();
                    // no result means no ban or mute or anything, we SHOULD be returning false here
                    return new PlayerResult(playerId, false, false, null, now, now, null);
                });
    }

    default int findRepetition(UUID playerId, PunishmentCategory category) {
        return (int) getEntityService().getAccessor(Infraction.TYPE)
                .querySelect("select i.* from banmod_infractions i where i.player_id = :playerId",
                        Map.of("playerId", playerId))
                .filter(i -> i.getCategory().equals(category) && !i.getPunishment().isInherentlyTemporary())
                .count();
    }

    default void revokeInfraction(UUID id, UUID revoker) {
        var acs = getEntityService().getAccessor(Infraction.TYPE);
        acs.queryUpdate(acs.getManager()
                .createQuery("update Infraction i set i.revoker = :revoker, i.revokedAt = :now where i.id = :id")
                .setParameter("revoker", revoker)
                .setParameter("now", Instant.now())
                .setParameter("id", id));
    }

    @UtilityClass
    final class Strings {
        public static final String AddonName       = "BanMod";
        public static final String AddonId         = "banmod";
        public static final String IssuesUrl       = "https://github.com/AMPZNetwork/BanMod/issues";
        public static final String PleaseCheckConsole = "Please check console for further information";
        public static final String OfflineModeInfo = "Offline mode is not fully supported! Players may be able to rejoin even after being banned.";
    }

    @UtilityClass
    final class Resources {
        public static final int ENTRIES_PER_PAGE = 8;

        public static void notify(
                BanMod mod,
                UUID playerId,
                @Nullable Punishment punishment,
                PlayerResult result,
                BiConsumer<UUID, Component> forwarder
        ) {
            var playerAdapter = mod.getLib().getPlayerAdapter();
            if (!playerAdapter.isOnline(playerId))
                return;
            var    name       = playerAdapter.getName(playerId);
            TextComponent msgUser, msgNotify;
            String permission = Permission.PluginErrorNotification;
            if (punishment == null) {
                msgUser   = text("""
                        An internal server error occurred.
                        Please contact your server administrator and try again later.

                        %s""".formatted(result.reason())).color(RED);
                msgNotify = text("An internal error is causing issues for players and they cannot join.").color(RED)
                        .append(text("To allow connecting anyway, please enable "))
                        .append(text("allow-unsafe-connections").color(AQUA))
                        .append(text(" in the plugin configuration."));
                mod.log().error("""
                        An internal error occured and is keeping players from joining the server!
                        \tIf you want to still allow players to join, please enable 'allow-unsafe-connections' in the config.
                        \tError Message: %s""".formatted(result.reason()));
            } else switch (punishment) {
                case Mute:
                    msgUser = Displays.mutedTextUser(result);
                    msgNotify = Displays.mutedTextNotify(name);
                    permission = Permission.PlayerChatDeniedNotification;
                    break;
                case Kick:
                    msgUser = Displays.kickedTextUser(result);
                    msgNotify = Displays.kickedTextNotify(name);
                    permission = Permission.PlayerKickedNotification;
                    break;
                case Ban:
                    msgUser = Displays.bannedTextUser(mod, result);
                    msgNotify = Displays.bannedTextNotify(name);
                    permission = Permission.PlayerJoinDeniedNotification;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + punishment);
            }

            forwarder.accept(playerId, msgUser);
            playerAdapter.broadcast(permission, msgNotify);
            if (punishment != null)
                mod.log()
                        .info("User %s is %#s (%s)".formatted(name, punishment,
                                Displays.formatTimestamp(result.expires())));
        }

        public static void printExceptionWithIssueReportUrl(BanMod mod, String message, Throwable t) {
            printExceptionWithIssueReportUrl(mod.log(), message, t);
        }

        public static void printExceptionWithIssueReportUrl(Logger log, String message, Throwable t) {
            log.error(message, new RuntimeException("An unexpected internal error occurred. Please open a bugreport at " + Strings.IssuesUrl, t));
        }

        public static Command.@NotNull Error couldNotSaveError() {
            return new Command.Error("Could not save changes");
        }
    }

    @UtilityClass
    final class Permission {
        public static final String PlayerBypassMute = "banmod.bypass.mute";
        public static final String PlayerBypassKick = "banmod.bypass.kick";
        public static final String PlayerBypassBan = "banmod.bypass.ban";

        public static final String PlayerJoinDeniedNotification = "banmod.notify.join";
        public static final String PlayerChatDeniedNotification = "banmod.notify.chat";
        public static final String PlayerKickedNotification = "banmod.notify.kick";
        public static final String PluginErrorNotification  = "banmod.notify.error";
    }

    @UtilityClass
    final class Displays {
        @NotNull
        public static String formatDuration(Duration duration) {
            if (duration == null)
                return "permanent";
            return Polyfill.durationString(duration);
        }

        @NotNull
        public Component infractionList(BanMod mod, int page, Punishment punishment) {
            final var infractions = mod.getLib().getEntityService()
                    .getAccessor(Infraction.TYPE)
                    .all()
                    .filter(Infraction.IS_IN_EFFECT)
                    .filter(i -> i.getPunishment() == punishment)
                    .sorted(Infraction.BY_NEWEST)
                    .distinct().toList();
            final var pageCount = Math.ceil(1d * infractions.size() / Resources.ENTRIES_PER_PAGE);
            return infractions.stream()
                    .sorted(Infraction.BY_SHORTEST.thenComparing(i -> i.getPlayer()
                            .getLastKnownName()
                            .orElse("")))
                    .skip(Math.max(0, (page - 1L) * Resources.ENTRIES_PER_PAGE))
                    .limit(Resources.ENTRIES_PER_PAGE)
                    .map(infraction -> text("\n- ")
                            .append(textPunishmentFull(mod, infraction)))
                    .collect(Streams.atLeastOneOrElseGet(() -> text("\n- ")
                            .append(text("(none)").color(GRAY))))
                    .collect(Collector.of(() -> text()
                                    .append(text(punishment.name() + "list (Page %d of %d)"
                                            .formatted((Integer) (pageCount == 0 ? 0 : Math.max(1, page)), (Integer) (int) pageCount))),
                            ComponentBuilder::append,
                            (l, r) -> {
                                l.append(r);
                                return l;
                            },
                            ComponentBuilder::build));
        }

        @NotNull
        public Component textPunishmentFull(BanMod mod, Infraction infraction) {
            var username = mod.getLib().getPlayerAdapter().getName(infraction.getPlayer().getId());
            var text = text("User ")
                    .append(text(username).color(AQUA))
                    .append(text(" has been "))
                    .append(infraction.getPunishment()
                            .toComponent(true));

            var reason = infraction.getReason();
            if (reason != null)
                text = text.append(text(": "))
                        .append(text(reason).color(LIGHT_PURPLE));

            return text.append(textExpiry(infraction.getExpires()));
        }

        @NotNull
        public static TextComponent textExpiry(Instant expiry) {
            var text = text();
            if (expiry != null && !expiry.isBefore(Infraction.TOO_EARLY)) {
                text.append(text(" (until "))
                        .append(text(formatTimestamp(expiry)).color(YELLOW))
                        .append(text(")"));
            } else text.append(text(" ("))
                    .append(text("permanently").color(RED))
                    .append(text(")"));
            return text.build();
        }

        @NotNull
        public static TextComponent bannedTextUser(BanMod mod, PlayerResult result) {
            var text = text()
                    .append(text("You are banned from this Server")
                            .color(RED)
                            .decorate(BOLD, UNDERLINED))
                    .append(text("\n\n"));
            if (result.reason() != null)
                text.append(text("Reason:\n\n")
                                .color(AQUA)
                                .decorate(UNDERLINED))
                        .append(text(result.reason()).color(YELLOW))
                        .append(text("\n\n\n"));

            text.append(text("This punishment ").color(RED));
            if (result.expires() == null || result.expires()
                    .isBefore(Infraction.TOO_EARLY))
                text.append(text("is ").color(RED))
                        .append(text("permanent")
                                .color(DARK_RED)
                                .decorate(BOLD))
                        .append(text(".").color(RED));
            else text.append(text("ends at ").color(RED))
                    .append(text(formatTimestamp(result.expires()))
                            .color(YELLOW))
                    .append(text(".").color(RED));

            var appealUrl = mod.getBanAppealUrl();
            if (appealUrl != null)
                text.append(text("\nYou may appeal to get unbanned at\n").color(GRAY))
                        .append(text(appealUrl).color(AQUA)
                                .hoverEvent(showText(text("Open Link")))
                                .clickEvent(clickEvent(ClickEvent.Action.OPEN_URL, appealUrl)));
            return text.build();
        }

        @NotNull
        public static String formatTimestamp(Instant expiry) {
            if (expiry == null)
                return "permanent";
            var dateTime  = LocalDateTime.ofInstant(expiry, ZoneId.systemDefault());
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return dateTime.format(formatter);
        }

        @NotNull
        public static TextComponent mutedTextUser(PlayerResult result) {
            var text = text().append(text("You are muted!").color(RED));
            if (result.reason() != null)
                text.append(text(" Reason: "))
                        .append(text(result.reason()).color(YELLOW));
            text.append(textExpiry(result.expires()));
            return text.build();
        }

        @NotNull
        public static TextComponent mutedTextNotify(String name) {
            return text("")
                    .append(text("Player "))
                    .append(text(name).color(RED))
                    .append(text(" tried to write in chat, but they are "))
                    .append(text("muted").color(YELLOW))
                    .append(text("."));
        }

        @NotNull
        public static TextComponent kickedTextUser(PlayerResult result) {
            var text = text().append(text("You were kicked from the server").color(RED));
            if (result.reason() != null)
                text.append(text("\nReason: "))
                        .append(text(result.reason()).color(YELLOW));
            return text.build();
        }

        @NotNull
        public static TextComponent kickedTextNotify(String name) {
            return text("")
                    .append(text("Player "))
                    .append(text(name).color(RED))
                    .append(text(" was kicked from the server."));
        }

        @NotNull
        public static TextComponent bannedTextNotify(String name) {
            return text("")
                    .append(text("Player "))
                    .append(text(name).color(RED))
                    .append(text(" tried to join the game, but they are "))
                    .append(text("banned").color(DARK_RED))
                    .append(text("."));
        }
    }
}
