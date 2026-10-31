package com.thronecraft.essentials.economy.pay;

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

public final class PayShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    private File file;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "economy.yml");
        if (plugin.getCommand("pay") != null) plugin.getCommand("pay").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "This command can only be used by a player."); return true; }
        if (args.length != 2) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /pay <player> <amount>"); return true; }
        Player recipient = Bukkit.getPlayerExact(args[0]);
        if (recipient == null) { com.thronecraft.net.shards.ShardMessages.send(sender, "That player must be online."); return true; }
        if (recipient.equals(player)) { com.thronecraft.net.shards.ShardMessages.send(sender, "You cannot pay yourself."); return true; }
        double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Enter a valid amount."); return true; }
        if (!Double.isFinite(amount) || amount <= 0) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be greater than zero."); return true; }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String from = "balances." + player.getUniqueId(), to = "balances." + recipient.getUniqueId();
        if (!data.getBoolean("payments." + recipient.getUniqueId(), true)) { com.thronecraft.net.shards.ShardMessages.send(sender, "That player is not accepting payments."); return true; }
        double balance = data.getDouble(from, 0);
        if (balance < amount) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have enough coins."); return true; }
        double recipientBalance = data.getDouble(to, 0);
        double updatedRecipientBalance = recipientBalance + amount;
        if (!Double.isFinite(updatedRecipientBalance)) { com.thronecraft.net.shards.ShardMessages.send(sender, "That payment would make the recipient balance invalid."); return true; }
        data.set(from, balance - amount); data.set(to, updatedRecipientBalance);
        try { data.save(file); } catch (IOException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Payment could not be saved."); plugin.getLogger().severe("Could not save economy.yml: " + ex.getMessage()); return true; }
        EconomyTransactionLogger.recordOrReport(plugin, sender, player.getName(),
                "pay", recipient.getName(), amount, balance, balance - amount);
        com.thronecraft.net.shards.ShardMessages.send(player, "You paid " + recipient.getName() + " " + String.format(java.util.Locale.US, "%.2f", amount) + " coins.");
        com.thronecraft.net.shards.ShardMessages.send(recipient, "You received " + String.format(java.util.Locale.US, "%.2f", amount) + " coins from " + player.getName() + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("pay") != null) plugin.getCommand("pay").setExecutor(null);
        plugin = null; file = null;
    }
}
