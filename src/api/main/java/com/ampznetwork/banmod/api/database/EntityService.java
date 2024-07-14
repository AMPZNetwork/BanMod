package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.PlayerResult;
import com.ampznetwork.banmod.api.model.Punishment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.tree.LifeCycle;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQL57Dialect;
import org.jetbrains.annotations.Contract;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface EntityService extends LifeCycle {
    Stream<PlayerData> getPlayerData();
    Optional<PlayerData> getPlayerData(UUID playerId);

    AlmostComplete<PlayerData> getOrCreatePlayerData(UUID playerId);

    Stream<PunishmentCategory> getCategories();

    default Optional<PunishmentCategory> findCategory(String name) {
        return getCategories()
                .filter(cat -> cat.getName().equals(name))
                .findAny();
    }

    Stream<Infraction> getInfractions();

    Stream<Infraction> getInfractions(UUID playerId);

    default int findRepetition(UUID playerId, PunishmentCategory category) {
        return (int) getInfractions(playerId)
                .filter(i -> i.getCategory().equals(category) && i.getPunishment() != Punishment.Kick)
                .count();
    }

    default PlayerResult queuePlayer(UUID playerId) {
        return getInfractions(playerId)
                .filter(Infraction.IS_IN_EFFECT)
                .sorted(Infraction.BY_SEVERITY)
                .map(i -> new PlayerResult(playerId,
                        i.getRevoker() == null && i.getPunishment() == Punishment.Mute,
                        i.getRevoker() == null && i.getPunishment() == Punishment.Ban,
                        i.getReason(), i.getTimestamp(), i.getExpires()))
                .findFirst()
                .orElseGet(() -> {
                    var now = Instant.now();
                    return new PlayerResult(playerId, false, false, null, now, now);
                });
    }

    void pingIdCache(UUID id);

    void pingUsernameCache(UUID id, String name);

    void pingIpCache(UUID uuid, InetAddress ip);

    boolean save(Object... it);

    @Contract("!null -> param1")
    <T> T refresh(T it);

    int delete(Object... objects);

    default PunishmentCategory defaultCategory() {
        var category = findCategory("default")
                .orElseGet(() -> PunishmentCategory.standard("default").build());
        save(category);
        return category;
    }

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Type implements Named {
        DATABASE,
        FILE
    }

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
}
