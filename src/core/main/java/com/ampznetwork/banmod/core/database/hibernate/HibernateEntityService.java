package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.DbObject;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;
import org.comroid.api.func.util.GetOrCreate;
import org.comroid.api.map.Cache;
import org.comroid.api.tree.Container;
import org.comroid.api.tree.UncheckedCloseable;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.intellij.lang.annotations.MagicConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.comroid.api.func.util.Debug.*;

@Value
public class HibernateEntityService extends Container.Base implements EntityService {
    private static final PersistenceProvider SPI = new HibernatePersistenceProvider();
    private static final Logger log = LoggerFactory.getLogger(HibernateEntityService.class);
    public Cache<UUID, PlayerData> Players;
    public Cache<UUID, Infraction> Infractions;
    public Cache<String, PunishmentCategory> Categories;
    BanMod banMod;
    EntityManager manager;
    MessagingService messagingService;

    public HibernateEntityService(BanMod mod) {
        this.banMod = mod;
        var unit = buildPersistenceUnit(mod.getDatabaseInfo(), BanModPersistenceUnit::new, "update");
        this.manager = unit.manager;
        this.messagingService = new MessagingService(mod);
        addChildren(unit, messagingService);
        this.Players = new Cache<>(PlayerData::getId, this::uncache, WeakReference::new, this::getPlayerData);
        this.Infractions = new Cache<>(Infraction::getId, this::uncache, WeakReference::new, this::getInfraction);
        this.Categories = new Cache<>(PunishmentCategory::getName, this::uncache, SoftReference::new, this::getCategory);
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
        return manager.createQuery("select pd from PlayerData pd", PlayerData.class)
                .getResultStream()
                .peek(data -> Players.replace(data.getId(), data));
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return manager.createQuery("select pd from PlayerData  pd where pd.id = :playerId", PlayerData.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .findAny();
    }

    @Override
    public GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId) {
        return new GetOrCreate<>(
                () -> getPlayerData(playerId).orElse(null),
                () -> PlayerData.builder().id(playerId),
                PlayerData.Builder::build,
                this::save);
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return manager.createQuery("select pc from PunishmentCategory pc", PunishmentCategory.class)
                .getResultStream();
    }

    private Optional<PunishmentCategory> getCategory(String name) {
        return manager.createQuery("select pc from PunishmentCategory  pc where pc.name = :name", PunishmentCategory.class)
                .setParameter("name", name)
                .getResultStream()
                .findAny();
    }

    @Override
    public GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name) {
        return new GetOrCreate<>(
                () -> getCategories()
                        .filter(cat -> name.equals(cat.getName()))
                        .findAny().orElse(null),
                () -> PunishmentCategory.builder().name(name),
                PunishmentCategory.Builder::build,
                this::save);
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

    private Optional<Infraction> getInfraction(UUID id) {
        return manager.createQuery("select i from Infraction i where i.id = :id", Infraction.class)
                .setParameter("id", id)
                .getResultStream()
                .findAny();
    }

    @Override
    public GetOrCreate<Infraction, Infraction.Builder> createInfraction() {
        return new GetOrCreate<>(null, Infraction::builder, Infraction.Builder::build, this::save);
    }

    @Override
    public void revokeInfraction(UUID id, UUID revoker) {
        wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                        update banmod_infractions i set i.revoker = :revoker where i.id = :id
                        """)
                .setParameter("id", id)
                .setParameter("revoker", revoker));
    }

    @Override
    public <T> T save(T object) {
        wrapTransaction(() -> {
            try {
                manager.persist(object);
            } catch (Throwable t) {
                log.debug("persist() failed for " + object, t);
                manager.merge(object);
            }
            return null;
        });
        return object;
    }

    @Override
    public int delete(Object... infractions) {
        var transaction = manager.getTransaction();
        var c = 0;
        synchronized (transaction) {
            try {
                transaction.begin();
                for (Object each : infractions) {
                    each = manager.merge(each);
                    manager.remove(each);
                    c += 1;
                }
                manager.flush();
                transaction.commit();
            } catch (Throwable t) {
                transaction.rollback();
                BanMod.Resources.printExceptionWithIssueReportUrl(banMod, "Could not remove all entities", t);
            }
        }
        return c;
    }

    private void uncache(Object id, DbObject obj) {
    }

    @SuppressWarnings("UnusedReturnValue")
    private <T> T wrapQuery(Function<Query, T> executor, Query query) {
        return wrapTransaction(new Supplier<T>() {
            @Override
            public T get() {
                return executor.apply(query);
            }

            @Override
            public String toString() {
                return query.toString();
            }
        });
    }

    private <T> T wrapTransaction(Supplier<T> task) {
        var transaction = manager.getTransaction();
        synchronized (transaction) {
            transaction.begin();
            try {
                var result = task.get();
                transaction.commit();
                return result;
            } catch (Throwable t) {
                banMod.log().warn("Could not execute task " + task, t);
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
