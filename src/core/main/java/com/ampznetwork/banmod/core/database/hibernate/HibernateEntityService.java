package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.info.Constraint;
import org.comroid.api.tree.Container;
import org.comroid.api.tree.UncheckedCloseable;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.intellij.lang.annotations.MagicConstant;

import javax.persistence.EntityManager;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class HibernateEntityService extends Container.Base implements EntityService {
    private final BanMod banMod;
    private final EntityManager manager;

    private static final PersistenceProvider SPI = new HibernatePersistenceProvider();

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
        return manager.createQuery("select i from Infraction i where i.playerId = :playerId", Infraction.class)
                .setParameter("playerId", playerId)
                .getResultStream();
    }

    @Override
    public void pingUsernameCache(UUID uuid, String name) {
        var transaction = manager.getTransaction();

        synchronized (transaction) {
                boolean nameExists = manager.createQuery("""
                                select count(pd) > 0
                                from PlayerData pd
                                join pd.knownNames kn
                                where pd.id = :uuid and key(kn) = :name
                                """, Boolean.class)
                        .setParameter("uuid", uuid)
                        .setParameter("name", name)
                        .getSingleResult();

            try {
                transaction.begin();

                if (!nameExists) {
                    manager.createNativeQuery("insert into banmod_playerdata (id) values (:uuid)")
                            .setParameter("uuid", uuid)
                            .executeUpdate();
                    manager.createNativeQuery("""
                                    insert into banmod_playerdata_names (PlayerData_id, knownNames, knownNames_KEY)
                                    values (:uuid, :lastSeen, :name);
                                    """)
                            .setParameter("uuid", uuid)
                            .setParameter("lastSeen", Timestamp.from(Instant.now()))  // or whatever timestamp you want to use
                            .setParameter("name", name)
                            .executeUpdate();
                }

                transaction.commit();
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }

    @Override
    public void pingIpCache(UUID uuid, InetAddress ip) {
        var transaction = manager.getTransaction();

        synchronized (transaction) {
            boolean nameExists = manager.createQuery("""
                            select count(pd) > 0
                            from PlayerData pd
                            join pd.knownIPs ki
                            where pd.id = :uuid and key(ki) = :ip
                            """, Boolean.class)
                    .setParameter("uuid", uuid)
                    .setParameter("ip", ip)
                    .getSingleResult();

            try {
                transaction.begin();

                if (!nameExists) {
                    manager.createNativeQuery("insert into banmod_playerdata (id) values (:uuid)")
                            .setParameter("uuid", uuid)
                            .executeUpdate();
                    manager.createNativeQuery("""
                                    insert into banmod_playerdata_ips (PlayerData_id, knownIPs, knownIPs_KEY)
                                    values (:uuid, :lastSeen, :ip);
                                    """)
                            .setParameter("uuid", uuid)
                            .setParameter("lastSeen", Timestamp.from(Instant.now()))  // or whatever timestamp you want to use
                            .setParameter("ip", ip)
                            .executeUpdate();
                }

                transaction.commit();
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
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
                log.warn("Could not save all entities\n\tEntities: " + Arrays.toString(entities), t);
                return false;
            }
        }
        return true;
    }

    @Override
    public <T> T refresh(T it) {
        Constraint.notNull(it, "entity");
        manager.refresh(it);
        return it;
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
                log.warn("Could not remove all entities\n\tEntities: " + Arrays.toString(infractions), t);
            }
        }
        return c;
    }

    public HibernateEntityService(BanMod banMod) {
        this.banMod = banMod;
        var unit = buildPersistenceUnit(banMod.getDatabaseInfo(), BanModPersistenceUnit::new, "update");
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
                //"hibernate.show_sql", String.valueOf(isDebug()),
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

    public record Unit(HikariDataSource dataSource, EntityManager manager) implements UncheckedCloseable {
        @Override
        public void close() {
            dataSource.close();
            manager.close();
        }
    }
}
