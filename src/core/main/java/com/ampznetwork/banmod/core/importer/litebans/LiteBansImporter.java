package com.ampznetwork.banmod.core.importer.litebans;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.banmod.core.importer.ImportResult;
import com.ampznetwork.banmod.core.importer.litebans.entity.Ban;
import com.ampznetwork.banmod.core.importer.litebans.entity.History;
import com.ampznetwork.banmod.core.importer.litebans.entity.Mute;
import com.ampznetwork.libmod.api.model.info.DatabaseInfo;
import lombok.Value;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.time.Instant.now;

@Value
public class LiteBansImporter implements com.ampznetwork.banmod.core.importer.Importer {
    BanMod mod;
    HibernateEntityService.Unit unit;

    public LiteBansImporter(BanMod mod, DatabaseInfo info) {
        this.mod = mod;
        this.unit = HibernateEntityService.buildPersistenceUnit(info, LiteBansPersistenceUnit::new, "validate");
    }

    @Override
    public ImportResult run() {
        int[] count = new int[]{0, 0, 0};
        var service = mod.getEntityService();
        Stream.concat(
                        unit.manager().createQuery("select m from Mute m", Mute.class)
                                .getResultStream(),
                        unit.manager().createQuery("select b from Ban b", Ban.class)
                                .getResultStream())
                .map(it -> {
                    // todo: handle ip bans
                    // for ip bans, we need to assign player entries based on known ips
                    Punishment punishment;
                    if (it instanceof Mute) {
                        punishment = Punishment.Mute;
                        count[0] += 1;
                    } else if (it instanceof Ban) {
                        punishment = Punishment.Ban;
                        count[1] += 1;
                    } else throw new AssertionError("invalid entity type");
                    return Infraction.builder()
                            .player(service.getOrCreatePlayerData(it.getUuid()).requireNonNull())
                            .category(mod.getDefaultCategory())
                            .punishment(punishment)
                            .issuer(it.getBannedByUuid())
                            .revoker(it.getRemovedByUuid())
                            .timestamp(Instant.ofEpochMilli(it.getTime()))
                            .expires(Instant.ofEpochMilli(it.getUntil()))
                            .reason(it.getReason())
                            .build();
                }).forEach(service::save);

        unit.manager()
                .createQuery("select h from History h", History.class)
                .getResultStream()
                .map(hist -> {
                    var now = now();
                    var data = service.getPlayerData(hist.getUuid())
                            .orElseGet(() -> new PlayerData(hist.getUuid(), now(),
                                    new ConcurrentHashMap<>(), new ConcurrentHashMap<>()));
                    data.getKnownNames().put(hist.getName(), now);
                    data.getKnownIPs().put(hist.getIp(), now);
                    count[2] += 1;
                    return data;
                }).forEach(service::save);

        return new ImportResult(count[0], count[1], count[2]);
    }

    @Override
    public void close() {
        unit.close();
    }
}
