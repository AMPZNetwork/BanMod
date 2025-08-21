package com.ampznetwork.banmod.core.cmd;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.libmod.api.SubMod;
import com.ampznetwork.libmod.api.entity.Player;
import lombok.experimental.UtilityClass;
import org.comroid.annotations.Instance;
import org.comroid.commands.autofill.IAutoFillProvider;
import org.comroid.commands.impl.CommandUsage;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Predicate.*;
import static java.util.stream.Stream.*;
import static org.comroid.api.func.util.Streams.*;

@UtilityClass
public class AutoFillProvider {
    public enum PageNumber implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .findAny().orElseThrow();
            var punishment = switch (usage.getFullCommand()[0]) {
                case "mutelist" -> Punishment.Mute;
                case "banlist" -> Punishment.Ban;
                default -> null;
            };
            var infractions = mod.getEntityService()
                    .getAccessor(Infraction.TYPE).all()
                    .filter(Infraction.IS_IN_EFFECT)
                    .filter(i -> punishment == null || i.getPunishment() == punishment)
                    .toList();
            var pageCount = (int) Math.ceil(1d * infractions.size() / BanMod.Resources.ENTRIES_PER_PAGE);
            return IntStream.range(1, pageCount + 1)
                    .mapToObj(String::valueOf);
        }
    }

    public enum Players implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            var mod = usage.getContext().stream()
                    .flatMap(cast(SubMod.class))
                    .findAny().orElseThrow();
            return mod.getLib().getPlayerAdapter()
                    .getCurrentPlayers()
                    .map(Player::getName)
                    .distinct();
        }
    }

    public enum PlayersByInfractionPunishment implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
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
                            .getAccessor(Infraction.TYPE).all()
                            .filter(Infraction.IS_IN_EFFECT)
                            .filter(infr -> infr.getPunishment() == key)
                            .flatMap(infr -> infr.getPlayer().getLastKnownName().stream()));
        }
    }

    public enum Categories implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            if (Arrays.asList(usage.getFullCommand()).contains("create"))
                return empty();
            return usage.getContext().stream()
                    .flatMap(cast(BanMod.class))
                    .flatMap(mod -> mod.getEntityService().getAccessor(PunishmentCategory.TYPE).all())
                    .map(PunishmentCategory::getName);
        }
    }

    public enum InfractionQuery implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            return empty();
        }
    }

    public enum ObjectProperties implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            return empty();
        }
    }

    public enum ObjectPropertyValues implements IAutoFillProvider {
        @Instance INSTANCE;

        @Override
        public Stream<? extends CharSequence> autoFill(CommandUsage usage, String argName, String currentValue) {
            return empty();
        }
    }
}
