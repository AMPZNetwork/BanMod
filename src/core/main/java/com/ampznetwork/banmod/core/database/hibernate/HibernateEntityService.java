package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.zaxxer.hikari.HikariDataSource;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.info.Constraint;
import org.comroid.api.tree.Container;
import org.comroid.api.tree.UncheckedCloseable;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static org.comroid.api.func.util.Debug.isDebug;

public class HibernateEntityService extends Container.Base implements EntityService {
    private static final PersistenceProvider SPI = new HibernatePersistenceProvider();
    private final BanMod mod;
    private final EntityManager manager;

    {
        PlayerData.CACHE_NAME = this::pingUsernameCache;
    }

    public HibernateEntityService(BanMod mod) {
        this.mod = mod;
        var unit = buildPersistenceUnit(mod.getDatabaseInfo(), BanModPersistenceUnit::new, "update");
        this.manager = unit.manager;
        addChildren(unit);
    }

    public static Unit buildPersistenceUnit(
            DatabaseInfo info,
            Function<HikariDataSource, PersistenceUnitInfo> unitProvider,
            @MagicConstant(stringValues = {"update", "validate"}) String hbm2ddl) {
        var config = Map.of(
                "hibernate.connection.driver_class", info.type().getDriverClass().getCanonicalName(),
                "hibernate.connection.url", info.url(),
                "hibernate.connection.username", info.user(),
                "hibernate.connection.password", info.pass(),
                "hibernate.dialect", info.type().getDialectClass().getCanonicalName(),
                "hibernate.show_sql", String.valueOf(isDebug()),
                "hibernate.hbm2ddl.auto", hbm2ddl
        );
        var dataSource = new HikariDataSource() {{
            setDriverClassName(info.type().getDriverClass().getCanonicalName());
            setJdbcUrl(info.url());
            setUsername(info.user());
            setPassword(info.pass());
        }};
        var unit = unitProvider.apply(dataSource);
        var factory = SPI.createContainerEntityManagerFactory(unit, config);
        var manager = factory.createEntityManager();
        return new Unit(dataSource, manager);
    }

    @Override
    public Stream<PlayerData> getPlayerData() {
        return manager.createQuery("select pd from PlayerData pd", PlayerData.class).getResultStream();
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return manager.createQuery("select pd from PlayerData  pd where pd.id = :playerId", PlayerData.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .findAny();
    }

    @Override
    public AlmostComplete<PlayerData> getOrCreatePlayerData(UUID playerId) {
        return new AlmostComplete<>(
                () -> getPlayerData(playerId)
                        .orElseGet(() -> new PlayerData(playerId, new HashMap<>(), new HashMap<>())),
                this::save
        );
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return manager.createQuery("select pc from PunishmentCategory pc", PunishmentCategory.class)
                .getResultStream();
    }

    @Override
    public Stream<Infraction> getInfractions() {
        return manager.createQuery("select i from Infraction i", Infraction.class)
                .getResultStream();
    }

    @Override
    public Stream<Infraction> getInfractions(UUID playerId) {
        return manager.createQuery("select i from Infraction i where i.player.id = :playerId", Infraction.class)
                .setParameter("playerId", playerId)
                .getResultStream();
    }

    @Override
    public void pingIdCache(UUID id) {
        wrapQuery(Query::executeUpdate,
                manager.createQuery("""
                                select count(pd) > 0
                                from PlayerData pd
                                where pd.id = :id
                                """, Boolean.class)
                        .setParameter("id", id),
                manager.createNativeQuery("insert into banmod_playerdata (id) values (:id)")
                        .setParameter("id", id));
    }

    @Override
    public void pingUsernameCache(UUID uuid, String name) {
        pingIdCache(uuid);
        wrapQuery(Query::executeUpdate,
                manager.createQuery("""
                                select count(pd) > 0
                                from PlayerData pd
                                join pd.knownNames kn
                                where pd.id = :id and key(kn) = :name
                                """, Boolean.class)
                        .setParameter("id", uuid)
                        .setParameter("name", name),
                manager.createNativeQuery("""
                                insert into banmod_playerdata_names (PlayerData_id, knownNames, knownNames_KEY)
                                values (:id, :lastSeen, :name);
                                """)
                        .setParameter("id", uuid)
                        .setParameter("lastSeen", Timestamp.from(now()))
                        .setParameter("name", name)
        );
    }

    @Override
    public void pingIpCache(UUID uuid, InetAddress ip) {
        pingIdCache(uuid);
        wrapQuery(Query::executeUpdate,
                manager.createQuery("""
                                select count(pd) > 0
                                from PlayerData pd
                                join pd.knownIPs kip
                                where pd.id = :id and key(kip) = :ip
                                """, Boolean.class)
                        .setParameter("id", uuid)
                        .setParameter("ip", ip.toString().substring(1)),
                manager.createNativeQuery("""
                                insert into banmod_playerdata_ips (PlayerData_id, knownIPs, knownIPs_KEY)
                                values (:id, :lastSeen, :ip);
                                """)
                        .setParameter("id", uuid)
                        .setParameter("lastSeen", Timestamp.from(now()))
                        .setParameter("ip", ip.toString().substring(1)));
    }

    @Override
    public <T> T refresh(T it) {
        Constraint.notNull(it, "entity");
        manager.refresh(it);
        return it;
    }

    @Override
    public synchronized boolean save(Object... entities) {
        var transaction = manager.getTransaction();
        synchronized (transaction) {
            try {
                transaction.begin();
                for (Object each : entities)
                    manager.persist(each);
                manager.flush();
                transaction.commit();
            } catch (Throwable t) {
                transaction.rollback();
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, "Could not save all entities", t);
                return false;
            }
        }
        return true;
    }

    @Override
    public int delete(Object... infractions) {
        var transaction = manager.getTransaction();
        var c = 0;
        synchronized (transaction) {
            try {
                transaction.begin();
                for (Object each : infractions) {
                    manager.remove(each);
                    c += 1;
                }
                manager.flush();
                transaction.commit();
            } catch (Throwable t) {
                transaction.rollback();
                BanMod.Resources.printExceptionWithIssueReportUrl(mod, "Could not remove all entities", t);
            }
        }
        return c;
    }

    private <R> R wrapQuery(Function<Query, R> executor, @Nullable Query condition, Query query) {
        if (condition != null)
            try {
                if ((boolean) condition.getSingleResult())
                    return null;
            } catch (Throwable t) {
                mod.log().warn("Could not execute condition " + condition, t);
                throw t;
            }
        var transaction = manager.getTransaction();
        synchronized (transaction) {
            transaction.begin();
            try {
                var result = executor.apply(query);
                transaction.commit();
                return result;
            } catch (Throwable t) {
                mod.log().warn("Could not execute query " + query, t);
                if (transaction.isActive())
                    transaction.rollback();
                throw t;
            }
        }
    }

    public record Unit(HikariDataSource dataSource, EntityManager manager) implements UncheckedCloseable {
        @Override
        public void close() {
            dataSource.close();
            manager.close();
        }
    }
}
