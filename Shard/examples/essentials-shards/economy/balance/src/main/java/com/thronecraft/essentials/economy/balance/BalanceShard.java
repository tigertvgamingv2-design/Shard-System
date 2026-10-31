package com.thronecraft.essentials.economy.balance;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class BalanceShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File file;

    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("balance") != null) plugin.getCommand("balance").setExecutor(this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /balance [player]"); return true; }
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /balance <online player>"); return true;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { com.thronecraft.net.shards.ShardMessages.send(sender, "That player must be online."); return true; }
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        com.thronecraft.net.shards.ShardMessages.send(sender, target.getName() + " has " + format(data.getDouble("balances." + target.getUniqueId(), 0)) + " coins.");
        return true;
    }

    private String format(double amount) { return String.format(java.util.Locale.US, "%.2f", amount); }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("balance") != null) plugin.getCommand("balance").setExecutor(null);
        plugin = null; file = null;
    }
}
