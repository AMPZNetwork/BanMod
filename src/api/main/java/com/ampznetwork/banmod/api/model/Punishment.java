package com.ampznetwork.banmod.api.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.Named;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Punishment implements Named {
    Mute(false, "muted"),
    Kick(true, "kicked"),
    Ban(false, "banned");

    boolean inherentlyTemporary;
    String alternateName;
}
