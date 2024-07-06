package com.ampznetwork.banmod.api;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;

public interface BanMod {
    PunishmentCategory getMuteCategory();

    PunishmentCategory getKickCategory();

    PunishmentCategory getBanCategory();

    PlayerAdapter getPlayerAdapter();

    EntityService getEntityService();

    void reload();
}
