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
import org.comroid.api.func.util.GetOrCreate;
import org.comroid.api.tree.LifeCycle;
import org.h2.Driver;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQL57Dialect;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static java.time.Instant.now;

@SuppressWarnings("UnusedReturnValue")
public interface EntityService extends LifeCycle {
    static String ip2string(InetAddress ip) {
        return ip.toString().substring(1);
    }

    Stream<PlayerData> getPlayerData();

    Optional<PlayerData> getPlayerData(UUID playerId);

    GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId);

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
                    var now = now();
                    return new PlayerResult(playerId, false, false, null, now, now);
                });
    }

    Stream<PunishmentCategory> getCategories();

    default Optional<PunishmentCategory> findCategory(String name) {
        return getCategories()
                .filter(cat -> cat.getName().equals(name))
                .findAny();
    }

    GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name);

    default PunishmentCategory defaultCategory() {
        return push(findCategory("default")
                .orElseGet(() -> PunishmentCategory.standard("default").build()));
    }

    default int findRepetition(UUID playerId, PunishmentCategory category) {
        return (int) getInfractions(playerId)
                .filter(i -> i.getCategory().equals(category) && i.getPunishment() != Punishment.Kick)
                .count();
    }

    Stream<Infraction> getInfractions();

    Stream<Infraction> getInfractions(UUID playerId);

    GetOrCreate<Infraction, Infraction.Builder> createInfraction();

    void revokeInfraction(UUID id, UUID revoker);

    <T> T push(T object);

    int delete(Object... objects);

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
        h2(Driver.class, H2Dialect.class),
        MySQL(com.mysql.jdbc.Driver.class, MySQL57Dialect.class),
        MariaDB(org.mariadb.jdbc.Driver.class, MariaDBDialect.class);

        Class<?> driverClass;
        Class<?> dialectClass;
    }

}
