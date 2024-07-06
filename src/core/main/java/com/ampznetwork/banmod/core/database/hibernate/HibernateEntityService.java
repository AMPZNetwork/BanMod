package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.info.Constraint;
import org.comroid.api.tree.Container;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.persistence.EntityManager;
import java.util.*;

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
                log.warn("Could not save all entities\n\tEntities: "+ Arrays.toString(entities), t);
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
