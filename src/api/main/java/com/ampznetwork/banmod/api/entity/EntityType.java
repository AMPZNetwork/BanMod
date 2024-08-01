package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.banmod.api.database.EntityService;

import java.util.Optional;
import java.util.UUID;

@Deprecated(forRemoval = true)
public enum EntityType {
    PlayerData {
        @Override
        public Optional<? extends DbObject> fetch(EntityService service, UUID id) {
            return service.getPlayerData(id);
        }
    }, Infraction {
        @Override
        public Optional<? extends DbObject> fetch(EntityService service, UUID id) {
            return service.getInfraction(id);
        }
    }, PunishmentCategory {
        @Override
        public Optional<? extends DbObject> fetch(EntityService service, UUID id) {
            return service.getCategory(id);
        }
    };

    public abstract Optional<? extends DbObject> fetch(EntityService service, UUID id);
}
