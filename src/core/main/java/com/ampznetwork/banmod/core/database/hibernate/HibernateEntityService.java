package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.zaxxer.hikari.HikariDataSource;
import org.comroid.api.func.util.GetOrCreate;
import org.comroid.api.tree.Container;
import org.comroid.api.tree.UncheckedCloseable;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.intellij.lang.annotations.MagicConstant;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import java.sql.Timestamp;
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
        PlayerData.CACHE_NAME = this::pushPlayerName;
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
    public GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId) {
        return new GetOrCreate<>(
                () -> getPlayerData(playerId).orElse(null),
                () -> PlayerData.builder().id(playerId),
                builder -> push(builder.build()));
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return manager.createQuery("select pc from PunishmentCategory pc", PunishmentCategory.class)
                .getResultStream();
    }

    @Override
    public GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name) {
        return new GetOrCreate<>(
                () -> getCategories()
                        .filter(cat -> name.equals(cat.getName()))
                        .findAny().orElse(null),
                () -> PunishmentCategory.builder().name(name),
                builder -> push(builder.build()));
    }

    @Override
    public PunishmentCategory push(PunishmentCategory category) {
        wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                        update banmod_categories cat
                        set cat.description = :description,
                            cat.defaultReason = :defaultReason,
                            cat.baseDuration = :baseDuration,
                            cat.repetitionExpBase = :repetitionExpBase
                        where cat.name = :name
                        """)
                .setParameter("name", category.getName())
                .setParameter("description", category.getDescription())
                .setParameter("defaultReason", category.getDefaultReason())
                .setParameter("baseDuration", category.getBaseDuration())
                .setParameter("repetitionExpBase", category.getRepetitionExpBase()));
        return category;
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
    public GetOrCreate<Infraction, Infraction.Builder> createInfraction() {
        return new GetOrCreate<>(null, Infraction::builder, builder -> push(builder.build()));
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
    public Infraction push(Infraction infraction) {
        wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                        update banmod_infractions i
                        set i.expires = :expires,
                            i.issuer = :issuer,
                            i.category_name = :categoryName,
                            i.punishment = :punishment,
                            i.reason = :reason,
                            i.revoker = :revoker
                        where i.id = :id
                        """)
                .setParameter("id", infraction.getId())
                .setParameter("expires", infraction.getExpires())
                .setParameter("issuer", infraction.getIssuer())
                .setParameter("categoryName", infraction.getCategory().getName())
                .setParameter("punishment", infraction.getPunishment())
                .setParameter("reason", infraction.getReason())
                .setParameter("revoker", infraction.getRevoker()));
        return infraction;
    }

    @Override
    public void pushPlayerId(UUID id) {
        if (id == null) return;
        wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                if (
                    select count(pd.id)
                    from banmod_playerdata pd
                    where pd.id = :id
                ) then
                    update banmod_playerdata pd
                    set pd.lastSeen = NOW()
                    where pd.id = :id;
                else
                    insert into banmod_playerdata (id) values (:id);
                end if;
                """).setParameter("id", id));
    }

    @Override
    public void pushPlayerName(UUID uuid, String name) {
        pushPlayerId(uuid);
        wrapQuery(Query::executeUpdate,
                manager.createNativeQuery("""
                                update banmod_playerdata_names names
                                set names.knownNames_KEY = :name,
                                    names.knownNames = :seen
                                where names.PlayerData_id = :id
                                """)
                        .setParameter("id", uuid)
                        .setParameter("name", name)
                        .setParameter("seen", Timestamp.from(now()))
        );
    }

    @Override
    public void pushPlayerIp(UUID uuid, String ip) {
        pushPlayerId(uuid);
        wrapQuery(Query::executeUpdate,
                manager.createNativeQuery("""
                                update banmod_playerdata_ips ip
                                set ip.knownIPs_KEY = :ip,
                                    ip.knownIPs = :seen
                                where ip.PlayerData_id = :id
                                """)
                        .setParameter("id", uuid)
                        .setParameter("ip", ip.substring(1))
                        .setParameter("seen", Timestamp.from(now())));
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

    private <R> R wrapQuery(Function<Query, R> executor, Query query) {
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
