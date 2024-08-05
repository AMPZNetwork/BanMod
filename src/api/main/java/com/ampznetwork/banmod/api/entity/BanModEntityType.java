package com.ampznetwork.banmod.api.entity;

import com.ampznetwork.libmod.api.entity.DbObject;
import com.ampznetwork.libmod.api.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class BanModEntityType<E extends DbObject, B> extends LibModEntityType<E, B> {
    public static final BanModEntityType<PlayerData, PlayerData.Builder>                 PLAYER_DATA
            = new BanModEntityType<>(Player.TYPE, PlayerData.class, PlayerData.Builder.class);
    public static final BanModEntityType<Infraction, Infraction.Builder>                 INFRACTION
            = new BanModEntityType<>(Infraction.class, Infraction.Builder.class);
    public static final BanModEntityType<PunishmentCategory, PunishmentCategory.Builder> PUNISHMENT_CATEGORY
            = new BanModEntityType<>(PunishmentCategory.class, PunishmentCategory.Builder.class);

    private BanModEntityType(Class<E> entityType, Class<B> builderType) {
        this(null, entityType, builderType);
    }

    private BanModEntityType(
            @Nullable LibModEntityType<?, ?> parent, Class<E> entityType, Class<B> builderType
    ) {
        super(parent, entityType, builderType);
    }
}
