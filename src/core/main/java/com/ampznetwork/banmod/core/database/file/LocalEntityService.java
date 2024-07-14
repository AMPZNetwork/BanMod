package com.ampznetwork.banmod.core.database.file;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import lombok.Value;
import org.comroid.api.func.util.GetOrCreate;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Value
public class LocalEntityService implements EntityService {
    BanMod banMod;

    @Override
    public Stream<PlayerData> getPlayerData() {
        return Stream.empty();
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public GetOrCreate<PlayerData, PlayerData.Builder> getOrCreatePlayerData(UUID playerId) {
        return null;
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return Stream.empty();
    }

    @Override
    public GetOrCreate<PunishmentCategory, PunishmentCategory.Builder> getOrCreateCategory(String name) {
        return null;
    }

    @Override
    public Stream<Infraction> getInfractions() {
        return Stream.empty();
    }

    @Override
    public Stream<Infraction> getInfractions(UUID playerId) {
        return Stream.empty();
    }

    @Override
    public GetOrCreate<Infraction, Infraction.Builder> createInfraction() {
        return null;
    }

    @Override
    public void revokeInfraction(UUID id, UUID revoker) {

    }

    @Override
    public <T> T save(T object) {
        return null;
    }

    @Override
    public int delete(Object... objects) {
        return 0;
    }
}
