package com.ampznetwork.banmod.core.event;

import com.ampznetwork.banmod.api.BanMod;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.java.Log;

@Log
@Value
@NonFinal
public class EventDispatchBase {
    BanMod banMod;
}
