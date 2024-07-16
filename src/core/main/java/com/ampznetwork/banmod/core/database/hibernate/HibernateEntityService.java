package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.DbObject;
import com.ampznetwork.banmod.api.entity.EntityType;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.database.PollingMessagingService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.GetOrCreate;
import org.comroid.api.func.util.Streams;
import org.comroid.api.map.Cache;
import org.comroid.api.tree.Container;
import org.comroid.api.tree.UncheckedCloseable;
import org.hibernate.Session;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.time.Instant.*;
import static org.comroid.api.Polyfill.*;
import static org.comroid.api.func.util.Debug.*;

@Value
@Log4j2
@EqualsAndHashCode(of = "manager")
public class HibernateEntityService extends Container.Base implements EntityService {
    private static final PersistenceProvider SPI = new HibernatePersistenceProvider();

    public static Unit buildPersistenceUnit(
            DatabaseInfo info, Function<HikariDataSource, PersistenceUnitInfo> unitProvider,
            @MagicConstant(stringValues = { "update", "validate" }) String hbm2ddl
    ) {
        var config = Map.of(
                "hibernate.connection.driver_class", info.type()
                        .getDriverClass()
                        .getCanonicalName(),
                "hibernate.connection.url", info.url(),
                "hibernate.connection.username", info.user(),
                "hibernate.connection.password", info.pass(),
                "hibernate.dialect", info.type()
                        .getDialectClass()
                        .getCanonicalName(),
                "hibernate.show_sql", String.valueOf("true".equals(System.getenv("TRACE"))),
                "hibernate.hbm2ddl.auto", hbm2ddl);
        var dataSource = new HikariDataSource() {{
            setDriverClassName(info.type()
                    .getDriverClass()
                    .getCanonicalName());
            setJdbcUrl(info.url());
            setUsername(info.user());
            setPassword(info.pass());
        }};
        var unit    = unitProvider.apply(dataSource);
        var factory = SPI.createContainerEntityManagerFactory(unit, config);
        var manager = factory.createEntityManager();
        return new Unit(dataSource, manager);
    }

    Cache<UUID, PlayerData>                players;
    Cache<UUID, Infraction>                infractions;
    Cache<UUID, PunishmentCategory>        categories;
    Map<EntityType, Cache<UUID, DbObject>> caches;
    BanMod                                 banMod;
    EntityManager                          manager;
    ScheduledExecutorService               scheduler;
    PollingMessagingService                messagingService;

    public HibernateEntityService(BanMod mod) {
        // boot up hibernate
        this.banMod = mod;
        var unit = buildPersistenceUnit(mod.getDatabaseInfo(), BanModPersistenceUnit::new, "update");
        this.manager = unit.manager;

        // boot up messaging service
        this.scheduler        = Executors.newScheduledThreadPool(2);
        this.messagingService = new PollingMessagingService(this, manager, Duration.ofSeconds(2));
        addChildren(unit, scheduler, messagingService);

        // caches & cleanup task
        var caches = new ConcurrentHashMap<EntityType, Cache<UUID, ? extends DbObject>>();
        caches.put(EntityType.PlayerData, this.players = new Cache<>(PlayerData::getId, this::uncache, WeakReference::new));
        caches.put(EntityType.Infraction, this.infractions = new Cache<>(Infraction::getId, this::uncache, WeakReference::new));
        caches.put(EntityType.PunishmentCategory, this.categories = new Cache<>(PunishmentCategory::getId, this::uncache, SoftReference::new));
        this.caches = uncheckedCast(Collections.unmodifiableMap(caches));

        // cleanup task
        scheduler.scheduleAtFixedRate(() -> Stream.of(players, infractions, categories)
                .forEach(Cache::clear), 10, 10, TimeUnit.MINUTES);

        PlayerData.CACHE_NAME = (uuid, name) -> getOrCreatePlayerData(uuid)
                .setUpdateOriginal(merge -> merge.pushKnownName(name))
                .complete(build -> build.id(uuid).knownName(name, now()));
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return manager.createQuery("select pc from PunishmentCategory pc", PunishmentCategory.class)
                .getResultStream()
                .peek(categories::push);
    }

    @Override
    public Stream<Infraction> getInfractions() {
        return manager.createQuery("select i from Infraction i", Infraction.class)
                .getResultStream()
                .peek(infractions::push);
    }

    @Override
    public Optional<PunishmentCategory> getCategory(String name) {
        return manager.createQuery("select pc from PunishmentCategory  pc where pc.name = :name", PunishmentCategory.class)
                .setParameter("name", name)
                .getResultStream()
                .peek(categories::push)
                .findAny();
    }

    @Override
    public Optional<PunishmentCategory> getCategory(UUID id) {
        return getFromCacheOrSupply(EntityType.PunishmentCategory, id,
                manager.createQuery("select pc from PunishmentCategory pc where pc.id = :id", PunishmentCategory.class)
                        .setParameter("id", id)
                        .getResultStream())
                .findAny();
    }

    @Override
    public GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name) {
        return new GetOrCreate<>(() -> getCategories().filter(cat -> name.equals(cat.getName()))
                .findAny()
                .orElse(null), () -> PunishmentCategory.builder()
                .name(name), PunishmentCategory.Builder::build, this::save)
                .addCompletionCallback(categories::push);
    }

    @Override
    public Stream<PlayerData> getPlayerData() {
        return manager.createQuery("select pd from PlayerData pd", PlayerData.class)
                .getResultStream()
                .peek(players::push);
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return getFromCacheOrSupply(EntityType.PlayerData, playerId,
                manager.createQuery("select pd from PlayerData  pd where pd.id = :playerId", PlayerData.class)
                        .setParameter("playerId", playerId)
                        .getResultStream())
                .findAny();
    }

    @Override
    public GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId) {
        return new GetOrCreate<>(() -> getPlayerData(playerId).orElse(null), () -> PlayerData.builder()
                .id(playerId), PlayerData.Builder::build, this::save)
                .addCompletionCallback(players::push);
    }

    @Override
    public Stream<Infraction> getInfractions(UUID playerId) {
        return manager.createQuery("select i from Infraction i where i.player.id = :playerId", Infraction.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .peek(infractions::push);
    }

    @Override
    public Optional<Infraction> getInfraction(UUID id) {
        return getFromCacheOrSupply(EntityType.Infraction, id,
                manager.createQuery("select i from Infraction i where i.id = :id", Infraction.class)
                        .setParameter("id", id)
                        .getResultStream())
                .findAny();
    }

    @Override
    public GetOrCreate<Infraction, Infraction.Builder> createInfraction() {
        return new GetOrCreate<>(null, Infraction::builder, Infraction.Builder::build, this::save)
                .addCompletionCallback(infractions::push)
                .addCompletionCallback(infraction -> messagingService.push()
                        .complete(notif -> notif.relatedId(infraction.getId()).relatedType(EntityType.Infraction)));
    }

    @Override
    public void revokeInfraction(UUID id, UUID revoker) {
        wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                        update banmod_infractions i
                        set i.revoker = :revoker, i.revokedAt = :now
                        where i.id = :id
                        """)
                .setParameter("id", id)
                .setParameter("revoker", revoker)
                .setParameter("now", now()));
        refresh(EntityType.Infraction, id);
        getInfraction(id).ifPresent(infraction -> messagingService.push()
                .complete(bld -> bld.relatedId(infraction.getId()).relatedType(EntityType.Infraction)));
    }

    @Override
    public <T extends DbObject> T save(T object) {
        var persistent = wrapTransaction(() -> {
            if (manager.contains(object))
                try {
                    manager.persist(object);
                    // now a persistent object!
                    return object;
                } catch (Throwable t) {
                    log.log(isDebug() ? Level.ERROR : Level.DEBUG, "persist() failed for " + object, t);
                }
            // try merging as a fallback action
            return manager.merge(object);
        });
        if (!(object instanceof NotifyEvent))
            caches.get(object.getEntityType()).push(persistent);
        return persistent;
    }

    @Override
    public void uncache(Object id, DbObject obj) {

    }

    @Override
    public int delete(Object... infractions) {
        var transaction = manager.getTransaction();
        var c           = 0;
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

    @SuppressWarnings("UnusedReturnValue")
    public <T> T wrapQuery(Function<Query, T> executor, Query query) {
        return wrapTransaction(new Supplier<>() {
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

    public <T> T wrapTransaction(Supplier<T> executor) {
        return wrapTransaction(Connection.TRANSACTION_READ_COMMITTED, executor);
    }

    public <T> T wrapTransaction(@MagicConstant(valuesFromClass = Connection.class) int isolation, Supplier<T> executor) {
        var transaction = manager.getTransaction();

        synchronized (transaction) {
            transaction.begin();

            try ( // need a session
                  var session = manager.unwrap(Session.class)
                          .getSessionFactory()
                          .openSession()
            ) {
                // isolate
                session.doWork(con -> con.setTransactionIsolation(isolation));

                // execute and commit
                var result = executor.get();
                transaction.commit();

                return result;
            } catch (Throwable t) {
                banMod.log()
                        .warn("Could not execute task " + executor, t);
                if (transaction.isActive()) transaction.rollback();
                throw t;
            }
        }
    }

    public void refresh(@NotNull EntityType relatedType, @NotNull UUID... relatedIds) {
        var cache = caches.get(relatedType);
        for (var id : relatedIds) {
            var obj = cache.get(id);
            if (obj != null) {
                manager.refresh(obj);
                continue;
            }
            var value = relatedType.fetch(this, id).orElse(null);
            save(value);
        }
    }

    private <T extends DbObject> Stream<T> getFromCacheOrSupply(EntityType type, UUID id, Stream<T> fallback) {
        return caches.get(type).wrap(id).stream()
                .map(Polyfill::<T>uncheckedCast)
                .collect(Streams.or(() -> fallback.peek(caches.get(type)::push)));
    }

    public record Unit(HikariDataSource dataSource, EntityManager manager) implements UncheckedCloseable {
        @Override
        public void close() {
            dataSource.close();
            manager.close();
        }
    }
}
