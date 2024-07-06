package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.banmod.api.model.Punishment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;
import org.comroid.api.tree.LifeCycle;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQL57Dialect;
import org.jetbrains.annotations.Contract;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface EntityService extends LifeCycle {
    Stream<PunishmentCategory> getCategories();
    default Optional<PunishmentCategory> findCategory(String name) {
        return getCategories()
                .filter(cat -> cat.getName().equals(name))
                .findAny();
    }
    Stream<Infraction> getInfractions(UUID playerId);
    default int findRepetition(UUID playerId, PunishmentCategory category) {
        return (int) getInfractions(playerId)
                .filter(i -> i.getCategory().equals(category))
                .count();
    }
    default PlayerResult queuePlayer(UUID playerId) {
        return getInfractions(playerId)
                .filter(i -> i.getState().getValue() > 0)
                .max(Comparator.comparingInt(i -> i.getCategory().getPunishment().ordinal()))
                .map(infraction -> new PlayerResult(playerId, infraction.getCategory().getPunishment() == Punishment.Mute, infraction.getCategory().getPunishment() == Punishment.Ban))
                .orElseGet(() -> new PlayerResult(playerId, false, false));
    }

    boolean save(Object... it);

    @Contract("!null -> param1")
    <T> T refresh(T it);

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum DatabaseType implements Named {
        h2(org.h2.Driver.class, H2Dialect.class),
        MySQL(com.mysql.jdbc.Driver.class, MySQL57Dialect.class),
        MariaDB(org.mariadb.jdbc.Driver.class, MariaDBDialect.class);

        Class<?> driverClass;
        Class<?> dialectClass;
    }

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Type implements Named {
        Database,
        File
    }
}