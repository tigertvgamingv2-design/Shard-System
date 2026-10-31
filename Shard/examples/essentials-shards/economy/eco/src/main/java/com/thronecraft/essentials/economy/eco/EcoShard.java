package com.thronecraft.essentials.economy.eco;

import com.thronecraft.net.managers.EconomyTransactionLogger;
import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class EcoShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("eco") != null) plugin.getCommand("eco").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission."); return true; }
        if (args.length != 3) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /eco give|take|set <player> <amount>"); return true; }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /eco give|take|set <player> <amount>"); return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { com.thronecraft.net.shards.ShardMessages.send(sender, "That player must be online."); return true; }
        double amount;
        try { amount = Double.parseDouble(args[2]); } catch (NumberFormatException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Enter a valid amount."); return true; }
        if (!Double.isFinite(amount) || amount <= 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be greater than zero."); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = "balances." + target.getUniqueId();
        double old = data.getDouble(path, 0);
        double next = action.equals("give") ? old + amount : action.equals("take") ? old - amount : amount;
        if (!Double.isFinite(next) || next < 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "That would make the balance negative or invalid."); return true; }
        data.set(path, next);
        try { data.save(file); } catch (IOException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Balance could not be saved."); plugin.getLogger().severe("Could not save economy.yml: " + ex.getMessage()); return true; }
        EconomyTransactionLogger.recordOrReport(plugin, sender, sender.getName(),
                "eco." + action, target.getName(), amount, old, next);
        com.thronecraft.net.shards.ShardMessages.send(sender, "Set " + target.getName() + "'s balance to " + String.format(java.util.Locale.US, "%.2f", next) + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("eco") != null) plugin.getCommand("eco").setExecutor(null);
        plugin = null; file = null;
    }
}
