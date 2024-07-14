package com.ampznetwork.banmod.fabric.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.model.Punishment;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
import lombok.SneakyThrows;
import lombok.Value;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.comroid.api.func.util.DelegateStream;
import org.comroid.api.java.StackTraceUtils;

import java.io.PrintStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.ampznetwork.banmod.fabric.BanMod$Fabric.component2text;

@Value
public class FabricEventDispatch extends EventDispatchBase implements ServerLoginConnectionEvents.QueryStart, ServerMessageEvents.AllowChatMessage {
    /**
     * half arsed regex to work around the shit connection api
     * todo: support ipv6
     */
    private static final Pattern ConInfoPattern = Pattern.compile(".*id=(?<id>[\\da-f-]+).*?(?<ip>\\d+\\.\\d+\\.\\d+\\.\\d+).*");

    public FabricEventDispatch(BanMod banMod) {
        super(banMod);

        ServerLoginConnectionEvents.QUERY_START.register(this);

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this);
    }

    @Override
    @SneakyThrows
    public void onLoginStart(ServerLoginNetworkHandler handler,
                             MinecraftServer server,
                             PacketSender sender,
                             ServerLoginNetworking.LoginSynchronizer synchronizer) {
        try {
        // thank you fabric devs for this very useful and reasonable method
        var info = handler.getConnectionInfo();
        var matcher = ConInfoPattern.matcher(info);

        if (!matcher.matches()) {
            mod.log().warn(("Could not parse connection info string. Please report this at %s" +
                    "\n\tString: %s").formatted(BanMod.Strings.IssuesUrl, info));
            return;
        }

        var playerId = UUID.fromString(matcher.group("id"));
        var ip = InetAddress.getByName(matcher.group("ip"));
        var result = playerLogin(playerId, ip);

            if (result.forceDeny())
                BanMod.Resources.notify(mod, playerId, null, result, (id, msg) -> {
                    var serialize = component2text(msg);
                    handler.disconnect(serialize);
                });
            else if (result.isBanned())
            BanMod.Resources.notify(mod, playerId, Punishment.Ban, result, (id, msg) -> {
                var serialize = component2text(msg);
                handler.disconnect(serialize);
            });
        } catch (Throwable t) {
            var writer = new StringWriter();
            var printer = new PrintStream(new DelegateStream.IO.Output(writer));
            StackTraceUtils.writeFilteredStacktrace(t, printer);
            handler.disconnect(Text.of("Internal error: " + writer.getBuffer()));
        }
    }

    @Override
    public boolean allowChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        var playerId = sender.getUuid();
        var result = player(playerId);
        var maySend = !result.isMuted();
        if (!maySend)
            BanMod.Resources.notify(mod, playerId, Punishment.Mute, result, (id, msg) -> {
                var serialize = component2text(msg);
                sender.sendMessage(serialize);
            });
        return maySend;
    }
}
