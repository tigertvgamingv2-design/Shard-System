package com.thronecraft.essentials.items.kits;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KitsShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File kitsFile;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (plugin.getCommand("kits") != null) plugin.getCommand("kits").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /kits"); return true; }
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(kitsFile);
        List<String> names = new ArrayList<>();
        names.add("starter");
        if (kits.getConfigurationSection("kits") != null) names.addAll(kits.getConfigurationSection("kits").getKeys(false));
        Collections.sort(names.subList(1, names.size()), String.CASE_INSENSITIVE_ORDER);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Available kits: " + String.join(", ", names));
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("kits") != null) plugin.getCommand("kits").setExecutor(null);
        plugin = null; kitsFile = null;
    }
}
