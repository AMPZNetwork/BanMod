package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import lombok.experimental.UtilityClass;
import org.comroid.annotations.Instance;
import org.comroid.api.func.util.Command;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Stream.empty;
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
                    .filter(i -> i.getPunishment() == punishment)
                    .toList();
            var pageCount = (int) Math.ceil(1d * infractions.size() / BanMod.Resources.ENTRIES_PER_PAGE);
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
            return mod.getPlayerAdapter().getCurrentPlayers()
                    .flatMap(data -> data.getLastKnownName().stream())
                    .distinct();
        }
    }

    enum PlayersByInfractionPunishment implements Command.AutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .findAny().orElseThrow();
            var punishments = Arrays.stream(Punishment.values())
                    .filter(not(Punishment::isInherentlyTemporary))
                    .toList();
            return punishments.stream()
                    // check if keyword is in full command
                    .filter(key -> Arrays.stream(usage.getFullCommand())
                            .map(String::toLowerCase)
                            .anyMatch(str -> str.contains(key.getName().toLowerCase())))
                    // otherwise fall back to listing all non temporary ones
                    .collect(atLeastOneOrElseFlatten(punishments::stream))
                    // by active infractions and their type; list all currently punished players
                    .flatMap(key -> mod.getEntityService()
                            .getInfractions()
                            .filter(Infraction.IS_IN_EFFECT)
                            .filter(infr -> infr.getPunishment() == key)
                            .flatMap(infr -> infr.getPlayer().getLastKnownName().stream()));
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
