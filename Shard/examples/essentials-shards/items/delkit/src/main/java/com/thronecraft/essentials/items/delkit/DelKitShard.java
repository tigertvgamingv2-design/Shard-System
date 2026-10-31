package com.thronecraft.essentials.items.delkit;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class DelKitShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File kitsFile;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (plugin.getCommand("delkit") != null) plugin.getCommand("delkit").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission."); return true; }
        if (args.length != 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /delkit <name>"); return true; }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}") || name.equals("starter")) { com.thronecraft.net.shards.ShardMessages.send(sender, "That kit cannot be deleted."); return true; }
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(kitsFile);
        if (!kits.contains("kits." + name)) { com.thronecraft.net.shards.ShardMessages.send(sender, "That kit does not exist."); return true; }
        kits.set("kits." + name, null);
        try { kits.save(kitsFile); } catch (IOException ex) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Kit could not be deleted."); plugin.getLogger().severe("Could not save kits.yml: " + ex.getMessage()); return true;
        }
        com.thronecraft.net.shards.ShardMessages.send(sender, "Deleted kit " + name + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("delkit") != null) plugin.getCommand("delkit").setExecutor(null);
        plugin = null; kitsFile = null;
    }
}
