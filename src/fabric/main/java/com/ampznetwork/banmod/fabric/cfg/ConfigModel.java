package com.ampznetwork.banmod.fabric.cfg;

import com.ampznetwork.banmod.api.BanMod;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;

@Modmenu(modId = BanMod.Strings.AddonId)
@SuppressWarnings({ "FieldMayBeFinal", "unused" })
@Config(name = "banMod-config", wrapperName = "Config")
public class ConfigModel {
    public String  banAppealUrl           = "";
    public boolean allowUnsafeConnections = false;
}