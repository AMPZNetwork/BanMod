package com.ampznetwork.banmod.fabric.adp.internal;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.core.event.EventDispatchBase;
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

@Value
public class FabricEventDispatch extends EventDispatchBase implements ServerLoginConnectionEvents.Init, ServerLoginConnectionEvents.QueryStart, ServerMessageEvents.AllowChatMessage {
    public FabricEventDispatch(BanMod banMod) {
        super(banMod);

        ServerLoginConnectionEvents.INIT.register(this);
        ServerLoginConnectionEvents.QUERY_START.register(this);

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this);
    }

    @Override
    public void onLoginInit(ServerLoginNetworkHandler handler, MinecraftServer server) {
        System.out.println("FabricEventDispatch.onLoginInit");
        System.out.println("handler = " + handler);
        System.out.println("server = " + server);
    }

    @Override
    public void onLoginStart(ServerLoginNetworkHandler handler,
                             MinecraftServer server,
                             PacketSender sender,
                             ServerLoginNetworking.LoginSynchronizer synchronizer) {
        System.out.println("FabricEventDispatch.onLoginStart");
        System.out.println("handler = " + handler);
        System.out.println("server = " + server);
        System.out.println("sender = " + sender);
        System.out.println("synchronizer = " + synchronizer);
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
