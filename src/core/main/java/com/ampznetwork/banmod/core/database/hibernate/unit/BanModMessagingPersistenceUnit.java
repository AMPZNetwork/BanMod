package com.ampznetwork.banmod.core.database.hibernate.unit;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.PersistenceUnitBase;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class BanModMessagingPersistenceUnit extends PersistenceUnitBase {
    public BanModMessagingPersistenceUnit(HikariDataSource dataSource) {
        super("BanMod-Messaging",
                dataSource,
                BanMod.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of(NotifyEvent.class)
                        .map(Class::getCanonicalName)
                        .toList());
    }
}
