package com.ampznetwork.banmod.api.model;

import com.ampznetwork.banmod.api.model.info.DefaultReason;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Punishment implements Named, DefaultReason {
    Kick(true, "kicked"),
    Mute(false, "muted"),
    Debuff(false, "debuffed"),
    Ban(false, "banned");

    boolean inherentlyTemporary;
    String adverb;

    @Override
    public String getAlternateName() {
        return adverb;
    }

    public String getDefaultReason() {
        return "You were " + adverb;
    }
}
