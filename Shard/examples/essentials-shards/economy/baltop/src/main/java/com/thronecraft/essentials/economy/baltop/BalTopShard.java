package com.thronecraft.essentials.economy.baltop;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BalTopShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("baltop") != null) plugin.getCommand("baltop").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /baltop"); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        List<java.util.Map.Entry<String, Double>> entries = new ArrayList<>();
        var balances = data.getConfigurationSection("balances");
        if (balances != null) {
            for (String id : balances.getKeys(false)) {
                double balance = data.getDouble("balances." + id, 0);
                if (Double.isFinite(balance) && balance >= 0) entries.add(java.util.Map.entry(id, balance));
            }
        }
        entries.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
        com.thronecraft.net.shards.ShardMessages.send(sender, "Top balances:");
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            var entry = entries.get(i);
            String name;
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                name = player.getName() == null ? entry.getKey() : player.getName();
            } catch (IllegalArgumentException ex) { name = entry.getKey(); }
            com.thronecraft.net.shards.ShardMessages.send(sender, (i + 1) + ". " + name + " - " + String.format(java.util.Locale.US, "%.2f", entry.getValue()));
        }
        if (entries.isEmpty()) com.thronecraft.net.shards.ShardMessages.send(sender, "No balances recorded yet.");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("baltop") != null) plugin.getCommand("baltop").setExecutor(null);
        plugin = null; file = null;
    }
}
