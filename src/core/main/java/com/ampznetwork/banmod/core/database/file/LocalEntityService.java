package com.ampznetwork.banmod.core.database.file;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import lombok.Value;
import org.comroid.api.func.util.AlmostComplete;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Value
public class LocalEntityService implements EntityService {
    BanMod banMod;

    {
        PlayerData.CACHE_NAME = this::pingUsernameCache;
    }

    public LocalEntityService(BanMod banMod) {
        throw new UnsupportedOperationException("unimplemented");
    }
// todo

    @Override
    public Stream<PlayerData> getPlayerData() {
        return Stream.empty();
    }

    @Override
    public Optional<PlayerData> getPlayerData(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public AlmostComplete<PlayerData> getOrCreatePlayerData(UUID playerId) {
        return null;
    }

    @Override
    public Stream<PunishmentCategory> getCategories() {
        return Stream.empty();
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
    public void pingIdCache(UUID id) {

    }

    @Override
    public void pingUsernameCache(UUID id, String name) {

    }

    @Override
    public void pingIpCache(UUID uuid, InetAddress ip) {

    }

    @Override
    public boolean save(Object... entities) {
        return false;
    }

    @Override
    public <T> T refresh(T it) {
        return null;
    }

    @Override
    public int delete(Object... infractions) {
        return 0;
    }
}
