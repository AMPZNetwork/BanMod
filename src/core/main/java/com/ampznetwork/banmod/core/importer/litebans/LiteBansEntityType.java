package com.ampznetwork.banmod.core.importer.litebans;

import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.core.importer.litebans.entity.Ban;
import com.ampznetwork.libmod.api.entity.DbObject;
import com.ampznetwork.libmod.api.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class LiteBansEntityType<E extends DbObject, B> extends LibModEntityType<E, B> {
    public static final LiteBansEntityType<Ban, Ban.Builder>                               BAN
            = new LiteBansEntityType<>(Player.TYPE, PlayerData.class, PlayerData.Builder.class);
    public static final LiteBansEntityType<Infraction, Infraction.Builder>                 INFRACTION
            = new LiteBansEntityType<>(Infraction.class, Infraction.Builder.class);
    public static final LiteBansEntityType<PunishmentCategory, PunishmentCategory.Builder> PUNISHMENT_CATEGORY
            = new LiteBansEntityType<>(PunishmentCategory.class, PunishmentCategory.Builder.class);

    private LiteBansEntityType(Class<E> entityType, Class<B> builderType) {
        this(null, entityType, builderType);
    }

    private LiteBansEntityType(
            @Nullable LibModEntityType<?, ?> parent, Class<E> entityType, Class<B> builderType
    ) {
        super(parent, entityType, builderType);
    }
}
