package com.ampznetwork.banmod.api.adapter;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.libmod.api.adapter.IBookAdapter;
import com.ampznetwork.libmod.api.adapter.IPlayerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.stream.Stream;

public interface PlayerAdapter extends IPlayerAdapter {
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

    void openBook(UUID playerId, IBookAdapter book);

    Stream<PlayerData> getCurrentPlayers();
}
