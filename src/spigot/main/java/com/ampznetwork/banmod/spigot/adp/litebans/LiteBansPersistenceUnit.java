package com.ampznetwork.banmod.spigot.adp.litebans;

import com.ampznetwork.banmod.core.database.hibernate.PersistenceUnitBase;
import com.ampznetwork.banmod.spigot.BanMod$Spigot;
import com.ampznetwork.banmod.spigot.adp.litebans.entity.Ban;
import com.ampznetwork.banmod.spigot.adp.litebans.entity.Mute;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class LiteBansPersistenceUnit extends PersistenceUnitBase {
    public LiteBansPersistenceUnit(HikariDataSource dataSource) {
        super("LiteBans",
                dataSource,
                BanMod$Spigot.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of(Mute.class, Ban.class)
                        .map(Class::getCanonicalName)
                        .toList());
    }
}
