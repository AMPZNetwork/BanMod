package com.ampznetwork.banmod.core.database.hibernate;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Value;

import java.util.stream.Stream;

@Value
public class BanModPersistenceUnit extends PersistenceUnitBase {
    public BanModPersistenceUnit(HikariDataSource dataSource) {
        super("BanMod",
                dataSource,
                BanMod.class.getProtectionDomain().getCodeSource().getLocation(),
                Stream.of(PlayerData.class, PunishmentCategory.class, Infraction.class)
                        .map(Class::getCanonicalName)
                        .toList());
    }
}
