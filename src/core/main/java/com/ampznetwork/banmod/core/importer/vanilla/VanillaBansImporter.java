package com.ampznetwork.banmod.core.importer.vanilla;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.Infraction;
import com.ampznetwork.banmod.core.importer.ImportResult;
import com.ampznetwork.banmod.core.importer.Importer;
import com.ampznetwork.banmod.core.importer.vanilla.entry.Ban;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.comroid.api.func.util.Command;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;

@Value
public class VanillaBansImporter implements Importer {
    BanMod mod;

    @Override
    public ImportResult run() {
        var list = new ArrayList<Infraction>();
        var mapper = new ObjectMapper();
        try (
                var banFile = new FileInputStream("banned-players.json");
                //var ipBanFile = new FileInputStream("banned-ips.json")
        ) {
            var service = mod.getEntityService();
            mapper.readValues(mapper.createParser(banFile), Ban.class)
                    .forEachRemaining(ban -> list.add(Infraction.builder()
                            .player(service.getOrCreatePlayerData(ban.getUuid()).get())
                            .category(mod.getDefaultCategory())
                            .timestamp(ban.getCreated().toInstant(ZoneOffset.UTC))
                            .reason(ban.getReason())
                            .build()));

            // todo: ip bans

            service.save(list.toArray());
            return new ImportResult(0, list.size(), 0);
        } catch (FileNotFoundException e) {
            return ImportResult.ZERO;
        } catch (IOException e) {
            throw new Command.Error("Could not import vanilla bans: " + e);
        }
    }

    @Override
    public void close() {
    }
}
