package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.PlayerResult;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;
import org.comroid.api.func.util.DelegateStream;
import org.comroid.api.java.StackTraceUtils;

import java.io.PrintStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.UUID;
import java.util.logging.Level;

@Log
@Value
@NonFinal
public abstract class EventDispatchBase {
    protected BanMod mod;

    protected PlayerResult player(UUID playerId) {
        return mod.getEntityService().queuePlayer(playerId);
    }

    protected PlayerResult playerLogin(UUID playerId, InetAddress address) {
        try {
            final var service = mod.getEntityService();
            final var name = mod.getPlayerAdapter().getName(playerId);

            service.pushPlayerName(playerId, name);
            service.pushPlayerIp(playerId, address);

            // queue player
            return player(playerId);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Could not check player status on join", t);
            var writer = new StringWriter();
            var printer = new PrintStream(new DelegateStream.IO.Output(writer));
            StackTraceUtils.writeFilteredStacktrace(t, printer);
            return new PlayerResult(playerId, false, false, true,
                    writer.getBuffer().toString(), null, null);
        }
    }
}
