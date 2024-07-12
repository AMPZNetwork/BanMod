package com.ampznetwork.banmod.fabric.adp.internal;

import com.ampznetwork.banmod.api.model.adp.BookAdapter;
import com.ampznetwork.banmod.api.model.adp.PlayerAdapter;
import com.ampznetwork.banmod.fabric.BanMod$Fabric;
import io.netty.buffer.Unpooled;
import lombok.Value;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.comroid.api.func.util.Command;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
    public void kick(UUID playerId, String reason) {
        Optional.ofNullable(banMod.getServer().getPlayerManager()
                        .getPlayer(playerId))
                .orElseThrow(() -> new Command.Error("Player not found"))
                .networkHandler
                .disconnect(Text.of(reason));
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
}
