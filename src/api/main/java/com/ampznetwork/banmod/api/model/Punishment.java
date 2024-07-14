package com.ampznetwork.banmod.api.model;

import com.ampznetwork.banmod.api.model.info.DefaultReason;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.comroid.api.attr.Named;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Punishment implements Named, DefaultReason {
    Kick(true, "kicked", NamedTextColor.YELLOW),
    Mute(false, "muted", NamedTextColor.RED),
    Debuff(false, "debuffed", NamedTextColor.LIGHT_PURPLE),
    Ban(false, "banned", NamedTextColor.DARK_RED);

    boolean inherentlyTemporary;
    String adverb;
    TextColor color;

    @Override
    public String getAlternateName() {
        return adverb;
    }

    public String getDefaultReason() {
        return "You were " + adverb;
    }

    @Override
    public String toString() {
        return name();
    }

    public TextComponent toComponent() {
        return Component.text(adverb).color(color);
    }
}
