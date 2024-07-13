package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.api.model.mc.Player;
import lombok.experimental.UtilityClass;
import org.comroid.annotations.Instance;
import org.comroid.api.func.util.Command;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Stream.*;
import static org.comroid.api.func.util.Streams.atLeastOneOrElseFlatten;
import static org.comroid.api.func.util.Streams.cast;

@UtilityClass
public class AutoFillProvider {
    enum PageNumber implements Command.AutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .findAny().orElseThrow();
            var punishment = switch (usage.getFullCommand()[0]) {
                case "mutelist" -> Punishment.Mute;
                case "banlist" -> Punishment.Ban;
                default -> throw new IllegalStateException("Unexpected value: " + usage.getFullCommand()[0]);
            };
            var infractions = mod.getEntityService().getInfractions()
                    .filter(Infraction.IS_IN_EFFECT)
                    .filter(i -> i.getCategory().getPunishment() == punishment)
                    .toList();
            var pageCount = (int) Math.ceil(1d * infractions.size() / BanModCommands.ENTRIES_PER_PAGE);
            return IntStream.range(1, pageCount + 1)
                    .mapToObj(String::valueOf);
        }
    }

    enum Players implements Command.AutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .findAny().orElseThrow();
            return concat(
                    mod.getPlayerAdapter().getCurrentPlayers()
                            .map(Player::getName),
                    mod.getEntityService().getPlayerData()
                            .map(PlayerData::getLastKnownName)
            ).filter(Objects::nonNull).distinct();
        }
    }

    enum PlayersByInfractionPunishment implements Command.AutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .findAny().orElseThrow();
            var playerId = usage.getContext().stream()
                    .flatMap(cast(UUID.class))
                    .findAny().orElseThrow();
            return Arrays.stream(Punishment.values())
                    .filter(not(Punishment::isInherentlyTemporary))
                    // check if keyword is in full command
                    .filter(key -> Arrays.stream(usage.getFullCommand())
                            .map(String::toLowerCase)
                            .anyMatch(key.getName()::equals))
                    .collect(atLeastOneOrElseFlatten(() -> of(Punishment.Mute, Punishment.Ban)))
                    // by active infractions and their type; list all currently punished players
                    .flatMap(key -> mod.getEntityService()
                            .getInfractions(playerId)
                            .filter(Infraction.IS_IN_EFFECT)
                            .filter(inf -> inf.getCategory().getPunishment() == key)
                            .map($ -> mod.getPlayerAdapter().getName(playerId)));
        }
    }

    enum Categories implements Command.AutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            if (Arrays.asList(usage.getFullCommand()).contains("create"))
                return empty();
            return usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .flatMap(mod -> mod.getEntityService().getCategories())
                    .map(PunishmentCategory::getName);
        }
    }
}
