package com.ampznetwork.banmod.api;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;

public interface BanMod {
    PlayerAdapter getPlayerAdapter();
    EntityService getEntityService();
}
