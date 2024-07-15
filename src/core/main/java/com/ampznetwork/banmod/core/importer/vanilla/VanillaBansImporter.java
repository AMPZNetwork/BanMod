package com.ampznetwork.banmod.core.importer.vanilla;

import com.ampznetwork.banmod.api.BanMod;
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

@Value
public class VanillaBansImporter implements Importer {
    BanMod mod;

    @Override
    public ImportResult run() {
        var c = new int[]{0};
        var mapper = new ObjectMapper();
        try (
                var banFile = new FileInputStream("banned-players.json");
                //var ipBanFile = new FileInputStream("banned-ips.json")
        ) {
            var service = mod.getEntityService();
            mapper.readValues(mapper.createParser(banFile), Ban.class)
                    .forEachRemaining(ban -> service.save(Infraction.builder()
                            .player(service.getOrCreatePlayerData(ban.getUuid()).requireNonNull())
                            .category(mod.getDefaultCategory())
                            .timestamp(ban.getCreated().toInstant(ZoneOffset.UTC))
                            .reason(ban.getReason())
                            .build()));

            // todo: ip bans

            return new ImportResult(0, c[0], 0);
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
