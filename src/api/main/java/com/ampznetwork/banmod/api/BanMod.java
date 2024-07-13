package com.ampznetwork.banmod.api;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collector;

import static java.time.Instant.now;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.clickEvent;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.UNDERLINED;

public interface BanMod {
    Logger log();

    void reload();

    DatabaseInfo getDatabaseInfo();

    PunishmentCategory getMuteCategory();

    PunishmentCategory getKickCategory();

    PunishmentCategory getBanCategory();

    PlayerAdapter getPlayerAdapter();

    EntityService getEntityService();

    @Nullable
    String getBanAppealUrl();

    @UtilityClass
    final class Strings {
        public static final String AddonName = "BanMod";
        public static final String AddonId = "banmod";
        public static final String IssuesUrl = "https://github.com/AMPZNetwork/BanMod/issues";
        public static final String PleaseCheckConsole = "Please check console for further information";
    }

    @UtilityClass
    final class Resources {
        public static final int ENTRIES_PER_PAGE = 8;

        public static Infraction.Builder standardInfraction(BanMod mod,
                                                            PunishmentCategory category,
                                                            UUID target,
                                                            @Nullable UUID issuer,
                                                            @Nullable String reason) {
            var rep = mod.getEntityService().findRepetition(target, category);
            var now = now();
            return Infraction.builder()
                    .playerId(target)
                    .category(category)
                    .issuer(issuer)
                    .reason(reason)
                    .timestamp(now)
                    .expires(now.plus(category.calculateDuration(rep)));
        }

        public static void notify(BanMod mod, UUID playerId, Punishment punishment, PlayerResult result, BiConsumer<UUID, Component> forwarder) {
            var playerAdapter = mod.getPlayerAdapter();
            var name = playerAdapter.getName(playerId);
            TextComponent msgUser, msgNotify;
            String permission;
            switch (punishment) {
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
            mod.log().info("User %s is %#s (%s)".formatted(name, punishment, Displays.textExpiryTime(result.expires())));
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
    }

    @UtilityClass
    final class Displays {
        public Component infractionList(BanMod banMod, int page, Punishment punishment) {
            final var infractions = banMod.getEntityService().getInfractions()
                    .filter(Infraction.IS_IN_EFFECT)
                    .filter(i -> i.getCategory().getPunishment() == punishment)
                    .toList();
            final var pageCount = Math.ceil(1d * infractions.size() / Resources.ENTRIES_PER_PAGE);
            // todo: use book adapter here
            return infractions.stream()
                    .skip((page - 1L) * Resources.ENTRIES_PER_PAGE)
                    .limit(Resources.ENTRIES_PER_PAGE)
                    .map(infraction -> text("\n- ")
                            .append(textPunishmentFull(banMod, infraction)))
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

        public Component textPunishment(Punishment punishment) {
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

        public Component textPunishmentFull(BanMod banMod, Infraction infraction) {
            var username = banMod.getPlayerAdapter().getName(infraction.getPlayerId());
            var text = text("User ")
                    .append(text(username).color(AQUA))
                    .append(text(" has been "))
                    .append(textPunishment(infraction.getCategory().getPunishment()));

            var reason = infraction.getReason();
            if (reason != null)
                text = text.append(text(": "))
                        .append(text(reason).color(LIGHT_PURPLE));

            return text.append(textExpiry(infraction.getExpires()));
        }

        public static @NotNull String textExpiryTime(Instant expiry) {
            var dateTime = LocalDateTime.ofInstant(expiry, ZoneId.systemDefault());
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return dateTime.format(formatter);
        }

        public static @NotNull TextComponent textExpiry(Instant expiry) {
            var text = text();
            if (expiry != null && !expiry.isBefore(Infraction.TOO_EARLY)) {
                text.append(text(" (until "))
                        .append(text(textExpiryTime(expiry)).color(YELLOW))
                        .append(text(")"));
            } else text.append(text(" ("))
                    .append(text("permanently").color(RED))
                    .append(text(")"));
            return text.build();
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
        public static TextComponent bannedTextUser(BanMod mod, PlayerResult result) {
            var text = text()
                    .append(text("You are banned from this Server")
                            .color(RED).decorate(BOLD, UNDERLINED))
                    .append(text("\n\n"));
            if (result.reason() != null)
                text.append(text("Reason:\n\n")
                                .color(AQUA).decorate(UNDERLINED))
                        .append(text(result.reason()).color(YELLOW))
                        .append(text("\n\n\n"));

            text.append(text("This punishment ").color(RED));
            if (result.expires() == null || result.expires().isBefore(Infraction.TOO_EARLY))
                text.append(text("is ").color(RED))
                        .append(text("permanent")
                                .color(DARK_RED).decorate(BOLD))
                        .append(text(".").color(RED));
            else text.append(text("ends at ").color(RED))
                    .append(text(textExpiryTime(result.expires()))
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
