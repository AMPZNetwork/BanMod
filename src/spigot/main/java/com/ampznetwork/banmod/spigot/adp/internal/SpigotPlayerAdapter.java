package com.ampznetwork.banmod.spigot.adp.internal;

import com.ampznetwork.banmod.api.model.adp.BookAdapter;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;
import com.ampznetwork.banmod.spigot.BanMod$Spigot;
import lombok.Value;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.comroid.api.data.Vector;
import org.comroid.api.info.Constraint;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer.get;

@Value
public class SpigotPlayerAdapter implements PlayerAdapter {
    BanMod$Spigot banMod;

    @Override
    public String getName(UUID playerId) {
        return banMod.getServer().getOfflinePlayer(playerId).getName();
    }

    @Override
    public boolean isOnline(UUID playerId) {
        return banMod.getServer().getPlayer(playerId) != null;
    }

    @Override
    public Vector.N3 getPosition(UUID playerId) {
        var player = banMod.getServer().getPlayer(playerId);
        Constraint.notNull(player, "player").run();
        var location = player.getLocation();
        return new Vector.N3(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public String getWorldName(UUID playerId) {
        return banMod.getServer().getPlayer(playerId).getWorld().getName();
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
}
