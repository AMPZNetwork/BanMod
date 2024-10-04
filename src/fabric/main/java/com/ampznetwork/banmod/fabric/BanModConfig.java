package com.ampznetwork.banmod.fabric;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.libmod.fabric.config.Config;
import jdk.jfr.Name;
import lombok.Data;

@Data
@Name(BanMod.Strings.AddonId)
public class BanModConfig extends Config {
    private String  banAppealUrl;
    private boolean allowUnsafeConnections;
}
