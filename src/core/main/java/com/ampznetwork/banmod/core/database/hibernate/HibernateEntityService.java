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
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return manager.createQuery("select pd from PlayerData  pd where pd.id = :playerId", PlayerData.class)
                .setParameter("playerId", playerId)
                .getResultStream()
                .findAny();
    }

    @Override
    public boolean deleteCategory(String name) {
        return findCategory(name)
                .filter(it -> {
                    try {
                        manager.remove(it);
                        return true;
                    } catch (Throwable t) {
                        log.warn("Could not delete category {}", name, t);
                        return false;
                    }
                })
                .isPresent();
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return manager.createQuery("select pc from PunishmentCategory pc", PunishmentCategory.class)
                .getResultStream();
    }

    @Override
    public Stream<Infraction> getInfractions(UUID playerId) {
        return manager.createQuery("select i from Infraction i where i.playerId = :playerId", Infraction.class)
                .setParameter("playerId", playerId)
                .getResultStream();
    }

    @Override
    public boolean save(Object... entities) {
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
