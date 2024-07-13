package com.ampznetwork.banmod.spigot.adp.internal;

import com.ampznetwork.banmod.api.entity.PlayerData;
import com.ampznetwork.banmod.api.model.adp.BookAdapter;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;
import com.ampznetwork.banmod.api.model.mc.Player;
import com.ampznetwork.banmod.spigot.BanMod$Spigot;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer.get;
import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection;

@Value
public class SpigotPlayerAdapter implements PlayerAdapter {
    BanMod$Spigot banMod;

    @Override
    public UUID getId(String name) {
        final var fetch = PlayerAdapter.fetchId(banMod, name);
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> name.equals(player.getName()))
                .findAny()
                .map(OfflinePlayer::getUniqueId)
                .or(() -> banMod.getEntityService().getPlayerData()
                        .filter(pd -> pd.getKnownNames().keySet()
                                .stream().anyMatch(name::equals))
                        .map(PlayerData::getId)
                        .findAny())
                .orElseGet(fetch::join);
    }

    @Override
    public String getName(UUID playerId) {
        final var fetch = PlayerAdapter.fetchName(banMod, playerId);
        return Optional.ofNullable(banMod.getServer().getOfflinePlayer(playerId).getName())
                .or(() -> banMod.getEntityService().getPlayerData(playerId)
                        .flatMap(pd -> Optional.ofNullable(pd.getLastKnownName())))
                .orElseGet(fetch::join);
    }

    @Override
    public boolean isOnline(UUID playerId) {
        return banMod.getServer().getPlayer(playerId) != null;
    }

    @Override
    public void kick(UUID playerId, TextComponent reason) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null)
            return;
        var serialize = legacySection().serialize(reason);
        player.kickPlayer(serialize);
    }

    @Override
    public void send(UUID playerId, TextComponent component) {
        var player = banMod.getServer().getPlayer(playerId);
        if (player == null) return;
        var serialize = get().serialize(component);
        player.spigot().sendMessage(serialize);
    }

    @Override
    public void broadcast(@Nullable String receiverPermission, Component component) {
        final var serialize = get().serialize(component);
        banMod.getServer().getOnlinePlayers().stream()
                .filter(player -> receiverPermission == null || player.hasPermission(receiverPermission))
                .forEach(player -> player.spigot().sendMessage(serialize));
    }

    @Override
    public void openBook(UUID playerId, BookAdapter book) {
        if (!isOnline(playerId))
            throw new AssertionError("Target player is not online");
        var stack = new ItemStack(Material.WRITTEN_BOOK, 1);
        var meta = Objects.requireNonNull((BookMeta) stack.getItemMeta(), "item meta");
        meta.setTitle(BookAdapter.TITLE);
        meta.setAuthor(BookAdapter.AUTHOR);
        meta.spigot().setPages(book.getPages().stream()
                .map(page -> Arrays.stream(page)
                        .map(component -> get().serialize(component))
                        .flatMap(Arrays::stream)
                        .toArray(BaseComponent[]::new))
                .toList());
        stack.setItemMeta(meta);
        banMod.getServer().getPlayer(playerId).openBook(stack);
    }

    @Override
    public Stream<Player> getCurrentPlayers() {
        return banMod.getServer()
                .getOnlinePlayers().stream()
                .map(player -> new Player(player.getUniqueId(), player.getName()))
                .peek(player -> banMod.getEntityService().pingUsernameCache(player.getId(), player.getName()));
    }
}
