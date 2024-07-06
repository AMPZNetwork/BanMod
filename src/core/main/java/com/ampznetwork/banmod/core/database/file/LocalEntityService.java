package com.ampznetwork.banmod.core.database.file;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
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
    public boolean save(Object... entities) {
        return false;
    }

    @Override
    public <T> T refresh(T it) {
        return null;
    }
}
