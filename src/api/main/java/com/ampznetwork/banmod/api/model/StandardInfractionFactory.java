package com.ampznetwork.banmod.api.model;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

import static java.time.Instant.*;

@Value
@Builder
@RequiredArgsConstructor
public class StandardInfractionFactory implements Consumer<Infraction.Builder> {
    public static Builder base(BanMod mod, UUID playerId, PunishmentCategory category, @Nullable UUID issuer) {
        return base(mod, playerId, category, null, issuer);
    }

    public static Builder base(BanMod mod, UUID playerId, @Nullable Punishment punishment, @Nullable UUID issuer) {
        return base(mod, playerId, null, punishment, issuer);
    }

    public static Builder base(
            BanMod mod,
            UUID playerId,
            PunishmentCategory category,
            @Nullable Punishment punishment,
            @Nullable UUID issuer
    ) {
        if (category == null) category = mod.getDefaultCategory();
        return builder().mod(mod).playerId(playerId).category(category).punishment(punishment).issuer(issuer);
    }

    BanMod mod;
    UUID   playerId;
    PunishmentCategory category;
    @lombok.Builder.Default
    @Nullable
    Punishment punishment = null;
    @lombok.Builder.Default
    @Nullable
    UUID     issuer    = null;
    @lombok.Builder.Default
    @Nullable
    String   reason    = null;
    @lombok.Builder.Default
    @Nullable
    Duration duration  = null;
    @lombok.Builder.Default
    boolean  permanent = false;

    @Override
    @SuppressWarnings("ConstantValue")
    public void accept(Infraction.Builder builder) {
        var service = mod.getEntityService();
        var rep    = mod.findRepetition(playerId, category);
        var target = service.getAccessor(PlayerData.TYPE).getOrCreate(playerId).requireNonNull();
        var punish = punishment != null ? punishment : category.calculatePunishment(rep).orElse(Punishment.Kick);
        var expire = duration != null ? duration : category.calculateDuration(rep);
        var now    = now();

        builder.player(target)
                .category(category)
                .punishment(punish)
                .issuer(issuer)
                .reason(reason)
                .timestamp(now)
                .expires(permanent ? null : now.plus(expire));
    }
}
