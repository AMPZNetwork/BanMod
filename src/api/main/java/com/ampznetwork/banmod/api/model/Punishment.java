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
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Punishment implements Named, DefaultReason {
    Kick(true, false, "kicked", NamedTextColor.YELLOW),
    Mute(false, true, "muted", NamedTextColor.RED),
    Debuff(false, false, "debuffed", NamedTextColor.LIGHT_PURPLE),
    Ban(false, false, "banned", NamedTextColor.DARK_RED);

    public static final Comparator<Map.Entry<@NotNull Integer, Punishment>> BY_SEVERITY = Comparator.comparingInt(Map.Entry::getKey);
    boolean inherentlyTemporary;
    boolean passive;
    String  adverb;
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

    public TextComponent toComponent(boolean useAdverb) {
        return Component.text(useAdverb ? adverb : name())
                .color(color);
    }
}
