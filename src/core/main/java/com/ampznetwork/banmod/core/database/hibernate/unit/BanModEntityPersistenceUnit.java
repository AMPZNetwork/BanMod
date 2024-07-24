package com.ampznetwork.banmod.core.database.hibernate.unit;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.core.database.hibernate.PersistenceUnitBase;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class BanModEntityPersistenceUnit extends PersistenceUnitBase {
    public BanModEntityPersistenceUnit(HikariDataSource dataSource) {
        super("BanMod-Entities",
                dataSource,
                BanMod.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of(PlayerData.class, PunishmentCategory.class, Infraction.class)
                        .map(Class::getCanonicalName)
                        .toList());
    }
}
