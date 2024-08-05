package com.ampznetwork.banmod.core.importer.litebans;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.libmod.core.database.hibernate.PersistenceUnitBase;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class LiteBansPersistenceUnit extends PersistenceUnitBase {
    public LiteBansPersistenceUnit(HikariDataSource dataSource) {
        super("LiteBans",
                dataSource,
                BanMod.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of()
                        .map(Class::getCanonicalName)
                        .toList(),
                BanMod.class.getClassLoader());
    }
}
