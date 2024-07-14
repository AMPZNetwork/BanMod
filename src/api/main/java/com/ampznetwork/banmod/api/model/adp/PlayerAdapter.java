package com.ampznetwork.banmod.api.model.adp;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.comroid.api.func.util.Command;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.stream.Stream;

public interface PlayerAdapter extends Command.PermissionChecker.Adapter {
    BanMod getBanMod();

    default UUID getId(String name) {
        return PlayerData.fetchId(name).join();
    }

    default String getName(UUID playerId) {
        return getBanMod().getEntityService()
                .getOrCreatePlayerData(playerId).get()
                .getOrFetchUsername().join();
    }

    boolean isOnline(UUID playerId);

    void kick(UUID playerId, TextComponent reason);
    void send(UUID playerId, TextComponent component);
    void broadcast(@Nullable String recieverPermission, Component component);

    void openBook(UUID playerId, BookAdapter book);

    Stream<PlayerData> getCurrentPlayers();
}
