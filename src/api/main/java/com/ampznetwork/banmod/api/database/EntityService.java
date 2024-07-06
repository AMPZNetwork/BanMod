package com.ampznetwork.banmod.api.database;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;
import org.comroid.api.data.Vector;
import org.comroid.api.tree.LifeCycle;
import org.jetbrains.annotations.Contract;

import java.util.UUID;

public interface EntityService extends LifeCycle {
    boolean save(Object... it);

    @Contract("!null -> param1")
    <T> T refresh(T it);

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum DatabaseType implements Named {
        h2(org.h2.Driver.class, "org.hibernate.dialect.H2Dialect"),
        MySQL(com.mysql.jdbc.Driver.class, "org.hibernate.dialect.MySQL57Dialect");

        Class<?> driverClass;
        String dialect;
    }

    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Type implements Named {
        Database,
        File
    }
}
