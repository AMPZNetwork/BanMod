package com.ampznetwork.banmod.core.adp.litebans;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PunishmentCategory;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import com.ampznetwork.banmod.core.adp.litebans.entity.Ban;
import com.ampznetwork.banmod.core.adp.litebans.entity.Mute;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import lombok.Value;
import org.comroid.api.tree.UncheckedCloseable;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Value
public class LiteBansImporter implements UncheckedCloseable {
    BanMod banMod;
    HibernateEntityService.Unit unit;

    public LiteBansImporter(BanMod banMod, DatabaseInfo info) {
        this.banMod = banMod;
        this.unit = HibernateEntityService.buildPersistenceUnit(info, "validate");
    }

    public ImportResult run() {
        int[] count = new int[]{0, 0};
        var convert = Stream.concat(
                        unit.manager().createQuery("select m from Mute m", Mute.class)
                                .getResultStream(),
                        unit.manager().createQuery("select b from Ban b", Ban.class)
                                .getResultStream())
                .map(it -> {
                    // todo: handle ip bans
                    // for ip bans, we need to assign player entries based on known ips
                    PunishmentCategory category;
                    if (it instanceof Mute) {
                        category = banMod.getMuteCategory();
                        count[0] += 1;
                    } else if (it instanceof Ban) {
                        category = banMod.getBanCategory();
                        count[1] += 1;
                    } else throw new AssertionError("invalid entity type");
                    return new Infraction(UUID.randomUUID(),
                            it.getUuid(),
                            category,
                            Instant.ofEpochMilli(it.getTime()),
                            Instant.ofEpochMilli(it.getUntil()),
                            it.getReason(),
                            it.getBannedByUuid(),
                            it.getRemovedByUuid());
                })
                .toArray();
        banMod.getEntityService().save(convert);
        return new ImportResult(count[0], count[1]);
    }

    @Override
    public void close() {
        unit.close();
    }

    public record ImportResult(int muteCount, int banCount) {
    }
}
