package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.info.Constraint;
import org.comroid.api.tree.Container;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
public class HibernateEntityService extends Container.Base implements EntityService {
    private final BanMod banMod;
    private final EntityManager manager;

    public HibernateEntityService(BanMod banMod, DatabaseType type, String url, String user, String pass) {
        this.banMod = banMod;

        var config = Map.of(
                "hibernate.connection.driver_class", type.getDriverClass().getCanonicalName(),
                "hibernate.connection.url", url,
                "hibernate.connection.username", user,
                "hibernate.connection.password", pass,
                "hibernate.dialect", type.getDialectClass().getCanonicalName(),
                //"hibernate.show_sql", String.valueOf(isDebug()),
                "hibernate.hbm2ddl.auto", "update"
        );
        var provider = new HibernatePersistenceProvider();
        var dataSource = new HikariDataSource() {{
            setDriverClassName(type.getDriverClass().getCanonicalName());
            setJdbcUrl(url);
            setUsername(user);
            setPassword(pass);
        }};
        var unit = new BanModPersistenceUnit(dataSource);
        var factory = provider.createContainerEntityManagerFactory(unit, config);
        this.manager = factory.createEntityManager();

        addChildren(dataSource, factory, manager);
    }

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
        return manager.createQuery("select i from Infraction i where i.playerId == :playerId", Infraction.class)
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
}
