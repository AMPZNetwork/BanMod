package com.ampznetwork.banmod.fabric.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
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

import java.net.InetAddress;
import java.util.UUID;
import java.util.regex.Pattern;

@Value
@Slf4j
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
        // thank you fabric devs for this very useful and reasonable method
        var info = handler.getConnectionInfo();
        var matcher = ConInfoPattern.matcher(info);

        if (!matcher.matches()) {
            log.warn(("Could not parse connection info string. Please report this at %s" +
                    "\n\tString: %s").formatted(BanMod.IssuesUrl, info));
            return;
        }

        var id = UUID.fromString(matcher.group("id"));
        var ip = InetAddress.getByName(matcher.group("ip"));
        var result = playerLogin(id, ip);

        if (result.isBanned())
            handler.disconnect(Text.of(result.reason()));
    }

    @Override
    public boolean allowChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        var player = player(sender.getUuid());
        var maySend = !player.isMuted();
        if (!maySend) {
            var reason = player.reason();
            sender.sendMessage(Text.of("You are muted! " + (reason == null ? "" : "Reason: " + reason)));
        }
        return maySend;
    }
}
