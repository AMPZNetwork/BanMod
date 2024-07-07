package com.ampznetwork.banmod.api;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;

public interface BanMod {
    DatabaseInfo getDatabaseInfo();

    PunishmentCategory getMuteCategory();

    PunishmentCategory getKickCategory();

    PunishmentCategory getBanCategory();

    PlayerAdapter getPlayerAdapter();

    EntityService getEntityService();

    void reload();
}
