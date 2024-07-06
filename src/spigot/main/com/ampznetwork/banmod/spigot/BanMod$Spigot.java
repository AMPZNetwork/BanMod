package com.ampznetwork.banmod.spigot;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.spigot.adp.internal.SpigotPlayerAdapter;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.func.util.Command;

@Getter
public class BanMod$Spigot extends JavaPlugin implements BanMod {
    private final SpigotPlayerAdapter playerAdapter = new SpigotPlayerAdapter(this);
    private FileConfiguration config;
    private EntityService entityService;
    private Command.Manager cmdr;
    private Command.Manager.Adapter$Spigot adapter;
}
