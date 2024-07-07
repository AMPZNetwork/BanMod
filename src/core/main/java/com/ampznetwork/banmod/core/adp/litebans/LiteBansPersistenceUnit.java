package com.ampznetwork.banmod.core.adp.litebans;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.adp.litebans.entity.Ban;
import com.ampznetwork.banmod.core.adp.litebans.entity.Mute;
import com.ampznetwork.banmod.core.database.hibernate.PersistenceUnitBase;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class LiteBansPersistenceUnit extends PersistenceUnitBase {
    public LiteBansPersistenceUnit(HikariDataSource dataSource) {
        super("LiteBans",
                dataSource,
                BanMod.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of(Mute.class, Ban.class)
                        .map(Class::getCanonicalName)
                        .toList());
    }
}
