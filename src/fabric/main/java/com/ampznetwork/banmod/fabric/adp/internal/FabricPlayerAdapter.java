package com.ampznetwork.banmod.fabric.adp.internal;

import com.ampznetwork.banmod.fabric.BanMod$Fabric;
import com.ampznetwork.libmod.api.model.adp.BookAdapter;
import com.ampznetwork.libmod.api.model.adp.PlayerAdapter;
import io.netty.buffer.Unpooled;
import lombok.Value;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.util.TriState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.comroid.api.func.util.Command;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;

@Value
public class FabricPlayerAdapter implements PlayerAdapter {
    BanMod$Fabric banMod;

    @Override
    public boolean isOnline(UUID playerId) {
        return banMod.getServer().getPlayerManager()
                .getPlayer(playerId) != null;
    }

    @Override
    public boolean checkOpLevel(UUID playerId, @MagicConstant(intValues = {0, 1, 2, 3, 4}) int minimum) {
        return Optional.of(banMod.getServer())
                .map(MinecraftServer::getPlayerManager)
                .map(pm -> pm.getPlayer(playerId))
                .filter(spe -> spe.hasPermissionLevel(minimum))
                .isPresent();
    }

    @Override
    public TriState checkPermission(UUID playerId, String key, boolean explicit) {
        return switch (Permissions.getPermissionValue(playerId, key).join()) {
            case FALSE -> TriState.FALSE;
            case DEFAULT -> TriState.NOT_SET;
            case TRUE -> TriState.TRUE;
        };
    }

    @Override
    public void kick(UUID playerId, TextComponent reason) {
        var serialize = BanMod$Fabric.component2text(reason);
        Optional.ofNullable(banMod.getServer().getPlayerManager()
                        .getPlayer(playerId))
                .orElseThrow(() -> new Command.Error("Player not found"))
                .networkHandler
                .disconnect(serialize);
    }

    @Override
    public void send(UUID playerId, TextComponent component) {
        var serialize = BanMod$Fabric.component2text(component);
        var player = banMod.getServer().getPlayerManager().getPlayer(playerId);
        if (player == null) return;
        player.sendMessage(serialize);
    }

    @Override
    public void broadcast(@Nullable String recieverPermission, Component component) {
        var serialize = BanMod$Fabric.component2text(component);
        banMod.getServer().getPlayerManager()
                .getPlayerList().stream()
                .filter(player -> recieverPermission == null || Permissions.check(player, recieverPermission))
                .forEach(player -> player.sendMessage(serialize));
    }

    @Override
    public void openBook(UUID playerId, BookAdapter book) {
        var plr = banMod.getServer().getPlayerManager()
                .getPlayer(playerId);
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        // Set the stack's title, author, and pages
        var tag = new NbtCompound();
        tag.putString("title", BookAdapter.TITLE);
        tag.putString("author", BookAdapter.AUTHOR);

        var pages = book.getPages().stream()
                .map(page -> {
                    var text = text();
                    for (var comp : page)
                        text.append(comp);
                    return text.build();
                })
                .map(page -> NbtString.of(gson().serialize(page)))
                .collect(NbtList::new, Collection::add, Collection::addAll);

        tag.put("pages", pages);
        stack.setNbt(tag);

        // Create a PacketByteBuf and write the stack item stack to it
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeItemStack(stack);

        ServerPlayNetworking.send(plr, new Identifier("minecraft", "book_open"), buf);
    }

    @Override
    public Stream<PlayerData> getCurrentPlayers() {
        var service = banMod.getEntityService();
        return banMod.getServer().getPlayerManager()
                .getPlayerList().stream()
                .map(player -> {
                    var name = player.getName().getString();
                    return service.getOrCreatePlayerData(player.getUuid())
                            .setUpdateOriginal(original -> original.pushKnownName(name))
                            .complete(builder -> builder.knownName(name, now()));
                });
    }
}
