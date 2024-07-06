package com.ampznetwork.banmod.core.database.file;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.model.mini.RegionCompositeKey;
import com.ampznetwork.banmod.api.model.region.Group;
import com.ampznetwork.banmod.api.model.region.Region;
import lombok.Value;
import org.comroid.api.data.Vector;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Value
public class LocalEntityService implements EntityService {
    BanMod banMod;

    // todo

    @Override
    public Optional<Region> findRegion(RegionCompositeKey key) {
        return Optional.empty();
    }

    @Override
    public Stream<Region> findRegions(Vector.N3 location, String worldName) {
        return Stream.empty();
    }

    @Override
    public Stream<Region> findClaims(UUID claimOwnerId) {
        return null;
    }

    @Override
    public Optional<Group> findGroup(String name) {
        return Optional.empty();
    }

    @Override
    public boolean save(Object... entities) {
        return false;
    }

    @Override
    public <T> T refresh(T it) {
        return null;
    }
}
