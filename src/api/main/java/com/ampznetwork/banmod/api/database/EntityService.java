package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.DbObject;
import com.ampznetwork.banmod.api.entity.EntityType;
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
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static java.time.Instant.now;

@SuppressWarnings("UnusedReturnValue")
public interface EntityService extends LifeCycle {
    static String ip2string(InetAddress ip) {
        return ip.toString().substring(1);
    }

    BanMod getBanMod();

    ScheduledExecutorService getScheduler();

    Stream<PunishmentCategory> getCategories();

    Stream<Infraction> getInfractions();

    @Deprecated(forRemoval = true)
    Optional<PunishmentCategory> getCategory(String name);

    Optional<PunishmentCategory> getCategory(UUID id);

    GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name);

    Stream<PlayerData> getPlayerData();

    Optional<PlayerData> getPlayerData(UUID playerId);

    GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId);

    default PlayerResult queuePlayer(UUID playerId) {
        return getInfractions(playerId)
                .filter(Infraction.IS_IN_EFFECT)
                .sorted(Infraction.BY_SEVERITY)
                .map(i -> new PlayerResult(playerId,
                        i.getPunishment() == Punishment.Mute,
                        i.getPunishment() == Punishment.Ban,
                        i.getReason(), i.getTimestamp(), i.getExpires()))
                .findFirst()
                .orElseGet(() -> {
                    var now    = now();
                    // no result means no ban or mute or anything, we SHOULD be returning false here
                    return new PlayerResult(playerId, false, false, null, now, now);
                });
    }

    default PunishmentCategory defaultCategory() {
        return getOrCreateCategory("default")
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

    Stream<Infraction> getInfractions(UUID playerId);

    Optional<Infraction> getInfraction(UUID id);

    GetOrCreate<Infraction, Infraction.Builder> createInfraction();

    default int findRepetition(UUID playerId, PunishmentCategory category) {
        return (int) getInfractions(playerId)
                .filter(i -> i.getCategory().equals(category) && !i.getPunishment().isInherentlyTemporary())
                .count();
    }

    void revokeInfraction(UUID id, UUID revoker);

    <T extends DbObject> T save(T object);

    void refresh(EntityType type, UUID... ids);

    void uncache(Object id, @Nullable DbObject obj);

    int delete(Object... objects);

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
