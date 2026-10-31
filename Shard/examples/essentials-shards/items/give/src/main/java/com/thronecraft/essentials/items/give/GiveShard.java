package com.thronecraft.essentials.items.give;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

public final class GiveShard implements ShardModule, CommandExecutor {
    private JavaPlugin plugin;
    @Override public void onEnable(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("give") != null) plugin.getCommand("give").setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { com.thronecraft.net.shards.ShardMessages.send(sender, "You do not have permission."); return true; }
        if (args.length < 2 || args.length > 3) { com.thronecraft.net.shards.ShardMessages.send(sender, "Usage: /give <player> <material> [amount]"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { com.thronecraft.net.shards.ShardMessages.send(sender, "That player must be online."); return true; }
        Material material;
        try { material = Material.valueOf(args[1].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Unknown material."); return true; }
        if (!material.isItem() || material.isAir()) { com.thronecraft.net.shards.ShardMessages.send(sender, "That material cannot be given as an item."); return true; }
        int amount = 1;
        if (args.length == 3) {
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be a whole number."); return true; }
        }
        if (amount < 1 || amount > material.getMaxStackSize() * 64) { com.thronecraft.net.shards.ShardMessages.send(sender, "Amount must be between 1 and " + material.getMaxStackSize() * 64 + "."); return true; }
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(new ItemStack(material, amount));
        leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        com.thronecraft.net.shards.ShardMessages.send(sender, "Gave " + amount + " " + material.name() + " to " + target.getName() + ".");
        return true;
    }
    @Override public void onDisable() {
        if (plugin != null && plugin.getCommand("give") != null) plugin.getCommand("give").setExecutor(null);
        plugin = null;
    }
}
