package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.PlayerResult;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import org.comroid.api.func.util.DelegateStream;
import org.comroid.api.java.StackTraceUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.ampznetwork.banmod.api.database.EntityService.*;
import static java.time.Instant.*;
import static org.comroid.api.java.StackTraceUtils.*;

@Log
@Value
@NonFinal
public abstract class EventDispatchBase {
    protected BanMod mod;

    protected PlayerResult playerLogin(UUID playerId, InetAddress address) {
        var service = mod.getEntityService();
        var name = mod.getPlayerAdapter().getName(playerId);
        var data = service.getOrCreatePlayerData(playerId)
                .setUpdateOriginal(original -> original
                        .setLastSeen(now())
                        .pushKnownName(name)
                        .pushKnownIp(address))
                .complete(builder -> builder.lastSeen(now())
                        .id(playerId)
                        .knownName(name, now())
                        .knownIP(ip2string(address), now()));
        service.save(data);

        // queue player
        return player(playerId);
    }

    protected PlayerResult player(UUID playerId) {
        return mod.getEntityService().queuePlayer(playerId);
    }

    protected <C> void handleThrowable(
            UUID playerId,
            Throwable t,
            Function<Component, C> componentSerializer,
            Consumer<C> forwardAndDisconnect
    ) {
        try (
                var writer = new StringWriter();
                var out = new DelegateStream.Output(writer);
                var printer = new PrintStream(out);
        ) {
            mod.log().warn("An internal error occurred", t);
            StackTraceUtils.writeFilteredStacktrace(t, printer);
            BanMod.Resources.notify(mod, playerId, null,
                    new PlayerResult(playerId, false, false,
                            "%s: %s".formatted(lessSimpleDetailedName(t.getClass()), t.getMessage()),
                            null, null),
                    (uuid, component) -> {
                        var serialize = componentSerializer.apply(component);
                        if (!mod.allowUnsafeConnections())
                            forwardAndDisconnect.accept(serialize);
                    });
        } catch (IOException e) {
            mod.log().error("Have you tried turning your machine off and back on again?", t);
        }
    }
}
