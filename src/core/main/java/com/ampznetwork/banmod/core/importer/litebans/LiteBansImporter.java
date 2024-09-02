package com.ampznetwork.banmod.core.importer.litebans;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.importer.ImportResult;
import com.ampznetwork.banmod.core.importer.litebans.entity.Ban;
import com.ampznetwork.banmod.core.importer.litebans.entity.History;
import com.ampznetwork.banmod.core.importer.litebans.entity.Mute;
import com.ampznetwork.libmod.api.model.info.DatabaseInfo;
import com.ampznetwork.libmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.libmod.core.database.hibernate.PersistenceUnitBase;
import lombok.Value;

import java.time.Instant;
import java.util.stream.Stream;

import static java.time.Instant.*;

@Value
public class LiteBansImporter implements com.ampznetwork.banmod.core.importer.Importer {
    BanMod mod;
    HibernateEntityService.Unit unit;

    public LiteBansImporter(BanMod mod, DatabaseInfo info) {
        this.mod  = mod;
        this.unit = HibernateEntityService.buildPersistenceUnit(info,
                dataSource -> new PersistenceUnitBase("LiteBans", LiteBansImporter.class, dataSource, Mute.class, Ban.class, History.class),
                "validate");
    }

    @Override
    public ImportResult run() {
        int[] count   = new int[]{ 0, 0, 0 };
        var service = unit.manager();
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
                    var playerAdapter = mod.getLib().getPlayerAdapter();
                    return Infraction.builder()
                            .player(mod.getLib().getEntityService()
                                    .getAccessor(PlayerData.TYPE)
                                    .get(it.getUuid())
                                    .orElseThrow())
                            .category(mod.getDefaultCategory())
                            .punishment(punishment)
                            .issuer(playerAdapter.getPlayer(it.getBannedByUuid()).orElseThrow())
                            .revoker(playerAdapter.getPlayer(it.getRemovedByUuid()).orElseThrow())
                            .revokedAt(it.getRemovedByDate())
                            .timestamp(Instant.ofEpochMilli(it.getTime()))
                            .expires(Instant.ofEpochMilli(it.getUntil()))
                            .reason(it.getReason())
                            .build();
                }).forEach(service::persist);

        unit.manager()
                .createQuery("select h from History h", History.class)
                .getResultStream()
                .map(hist -> {
                    var now = now();
                    var data = mod.getLib().getEntityService()
                            .getAccessor(PlayerData.TYPE)
                            .getOrCreate(hist.getUuid())
                            .complete(build -> build
                                    .knownName(hist.getName(), now())
                                    .knownIP(hist.getIp(), now())
                                    .name(hist.getName()));
                    data.getKnownNames().put(hist.getName(), now);
                    data.getKnownIPs().put(hist.getIp(), now);
                    count[2] += 1;
                    return data;
                }).forEach(service::persist);

        return new ImportResult(count[0], count[1], count[2]);
    }

    @Override
    public void close() {
        unit.close();
    }
}
